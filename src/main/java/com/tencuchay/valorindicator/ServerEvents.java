package com.tencuchay.valorindicator.server;

import com.tencuchay.valorindicator.ValorIndicator;
import com.tencuchay.valorindicator.config.ValorConfig;
import com.tencuchay.valorindicator.network.IndicatorPacket;
import com.tencuchay.valorindicator.network.ValorNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs on the logical server (dedicated server, or the integrated server in singleplayer).
 * Works out the final damage / heal amount and tells the players who should see it:
 * <ul>
 *   <li>damage a player deals: a floating number over the target, sent only to that player;</li>
 *   <li>damage a player takes (from anything) and his own healing: numbers above his health bar (HUD);</li>
 *   <li>healing of other entities: a floating number for everyone who can see them.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ValorIndicator.MOD_ID)
public final class ServerEvents {

    /**
     * valor_items writes the game time of its last critical hit into the target's persistent data
     * under this key (a long). If it equals the current game time, this hit was a crit.
     */
    private static final String VALOR_CRIT_KEY = "ValorLastCritTick";

    /**
     * valor_items shield: when the shield blocks damage it writes how much (float, summed over hits in the
     * same tick) and the game time (long) into the victim's persistent data. True damage and other
     * bypass sources skip the shield, so nothing is written for them.
     */
    private static final String SHIELD_ABSORBED_KEY = "ValorShieldAbsorbed";
    private static final String SHIELD_TICK_KEY = "ValorShieldAbsorbedTick";

    /** Lets us pull out only this hit's share when several hits land in the same tick. */
    private record ShieldSeen(long tick, float absorbed) {}

    private static final Map<Integer, ShieldSeen> SHIELD_SEEN = new HashMap<>();

    /** When a hit has both a shield part and a health part, the health part is shown this much higher. */
    private static final double OVERFLOW_LIFT = 0.45;

    // Absorption hearts before the hit, so damage soaked up by them is still shown.
    private static int hurtEntityId = -1;
    private static float absorptionBefore = 0f;

    // valor_items:magic_shield (base value) before the hit. The shield is an attribute that the hit
    // lowers, so what it blocked is the difference before / after.
    private static double magicShieldBefore = 0.0;

    private ServerEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        hurtEntityId = victim.getId();
        absorptionBefore = victim.getAbsorptionAmount();
        magicShieldBefore = magicShieldBase(victim);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;

        float health = event.getAmount(); // what went into health (plus vanilla absorption hearts below)
        float magicShield = 0f; // part of this hit that the magic shield blocked
        if (hurtEntityId == victim.getId()) {
            float absorbed = absorptionBefore - victim.getAbsorptionAmount();
            if (absorbed > 0f) health += absorbed;
            magicShield = (float) Math.max(0.0, magicShieldBefore - magicShieldBase(victim));
        }
        hurtEntityId = -1;

        // Always read it (even for hits we skip below) so the per-tick bookkeeping stays in sync.
        float shield = shieldAbsorbedByThisHit(victim);

        if (!(health > 0f) && !(shield > 0f) && !(magicShield > 0f)) return; // also filters NaN

        DamageSource source = event.getSource();
        boolean crit = wasValorCrit(victim);
        String typeId = damageTypeId(source);

        // 1) A player takes damage, from anything: numbers above his health bar.
        if (victim instanceof ServerPlayer victimPlayer) {
            emitDamage(PacketDistributor.PLAYER.with(() -> victimPlayer), victim, shield, magicShield, health, typeId, crit, true);
        }

        // 2) A player deals damage to something else: a floating number over the target, only for the attacker.
        //    (Hitting yourself is already covered by the health bar above.)
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
            emitDamage(PacketDistributor.PLAYER.with(() -> attacker), victim, shield, magicShield, health, typeId, crit, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // Only count what is actually restored (ignore overheal at full health).
        float healed = Math.min(event.getAmount(), entity.getMaxHealth() - entity.getHealth());
        if (!(healed > 0f)) return;

        if (entity instanceof ServerPlayer player) {
            // A player's own healing: above his health bar, only he sees it.
            deliver(PacketDistributor.PLAYER.with(() -> player), entity, healed, "heal", false, true, true, 0.0);
        } else {
            deliver(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), entity, healed, "heal",
                    false, true, false, 0.0);
        }
    }

    /**
     * Sends one hit as up to three numbers: the part the shield took (grey), the part the magic shield took
     * (purple) and the part that got through. The crit look goes to the part that got through, or, when the
     * shields took everything, to the last shield number.
     */
    private static void emitDamage(PacketDistributor.PacketTarget target, LivingEntity victim,
                                   float shield, float magicShield, float health,
                                   String typeId, boolean crit, boolean hud) {
        boolean hasShield = shield > 0f;
        boolean hasMagic = magicShield > 0f;
        boolean hasHealth = health > 0f;
        double lift = 0.0; // world numbers are stacked a little higher each, the HUD ones spread by themselves

        if (hasShield) {
            deliver(target, victim, shield, ValorConfig.SHIELD_TYPE, crit && !hasHealth && !hasMagic, false, hud, lift);
            if (!hud) lift += OVERFLOW_LIFT;
        }
        if (hasMagic) {
            deliver(target, victim, magicShield, ValorConfig.MAGIC_SHIELD_TYPE, crit && !hasHealth, false, hud, lift);
            if (!hud) lift += OVERFLOW_LIFT;
        }
        if (hasHealth) {
            deliver(target, victim, health, typeId, crit, false, hud, lift);
        }
    }

    /** Base value of valor_items:magic_shield, or 0 when the attribute (or the mod) is not there. */
    private static double magicShieldBase(LivingEntity entity) {
        ResourceLocation id = new ResourceLocation("valor_items", "magic_shield");
        if (!ForgeRegistries.ATTRIBUTES.containsKey(id)) return 0.0;
        AttributeInstance instance = entity.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(id));
        return instance != null ? instance.getBaseValue() : 0.0;
    }

    private static boolean wasValorCrit(LivingEntity victim) {
        CompoundTag data = victim.getPersistentData();
        return data.contains(VALOR_CRIT_KEY, Tag.TAG_LONG)
                && data.getLong(VALOR_CRIT_KEY) == victim.level().getGameTime();
    }

    /** How much of the current hit the valor_items shield took (0 when it took nothing). */
    private static float shieldAbsorbedByThisHit(LivingEntity victim) {
        CompoundTag data = victim.getPersistentData();
        long now = victim.level().getGameTime();
        if (!data.contains(SHIELD_TICK_KEY, Tag.TAG_LONG) || data.getLong(SHIELD_TICK_KEY) != now) {
            return 0f;
        }

        float sumThisTick = data.getFloat(SHIELD_ABSORBED_KEY);
        ShieldSeen seen = SHIELD_SEEN.get(victim.getId());
        float alreadyCounted = (seen != null && seen.tick() == now) ? seen.absorbed() : 0f;

        if (SHIELD_SEEN.size() > 512) SHIELD_SEEN.clear();
        SHIELD_SEEN.put(victim.getId(), new ShieldSeen(now, sumThisTick));
        return Math.max(0f, sumThisTick - alreadyCounted);
    }

    private static String damageTypeId(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
    }

    private static void deliver(PacketDistributor.PacketTarget target, LivingEntity entity, float amount,
                                String typeId, boolean crit, boolean heal, boolean hud, double yOffset) {
        IndicatorPacket packet = new IndicatorPacket(
                entity.getX(), entity.getY() + entity.getBbHeight() + yOffset, entity.getZ(),
                amount, typeId, crit, heal, hud);
        ValorNetwork.CHANNEL.send(target, packet);
    }
}
