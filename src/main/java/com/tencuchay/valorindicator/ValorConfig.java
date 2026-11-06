package com.tencuchay.valorindicator.config;

import com.tencuchay.valorindicator.ValorIndicator;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Client-side config (config/valor_indicator-client.toml).
 * Only touches ForgeConfigSpec, so it is safe to load on a dedicated server too.
 */
@Mod.EventBusSubscriber(modid = ValorIndicator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ValorConfig {
    public static final ForgeConfigSpec SPEC;

    /** Pseudo damage type used for the part of a hit that the valor_items shield absorbed. */
    public static final String SHIELD_TYPE = "valor_indicator:shield";
    /** Same for the part that the magic shield absorbed. */
    public static final String MAGIC_SHIELD_TYPE = "valor_indicator:magic_shield";

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_VIEW_DISTANCE;
    public static final ForgeConfigSpec.IntValue LIFETIME_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIN_AMOUNT;
    public static final ForgeConfigSpec.IntValue MAX_INDICATORS;

    public static final ForgeConfigSpec.DoubleValue BASE_SCALE;
    public static final ForgeConfigSpec.DoubleValue CRIT_SCALE;
    public static final ForgeConfigSpec.BooleanValue DISTANCE_SCALING;
    public static final ForgeConfigSpec.DoubleValue REFERENCE_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue MIN_DISTANCE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue MAX_DISTANCE_FACTOR;

    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_DAMAGE_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> HEAL_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> SHIELD_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_SHIELD_COLOR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DAMAGE_COLORS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CRIT_DAMAGE_TYPES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CRIT_GRADIENTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CRIT_ICON_GRADIENTS;

    public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;

    /**
     * Crit gradient: rgb is used at the chosen edge (top or bottom) and blends into the normal
     * color over fraction (0..1) of the number's height.
     */
    public record Gradient(int rgb, boolean fromTop, float fraction) {}

    private static final List<String> DEFAULT_DAMAGE_COLOR_LIST = List.of(
            // fire
            "minecraft:in_fire=#FF8C1A",
            "minecraft:on_fire=#FF8C1A",
            "minecraft:lava=#FF8C1A",
            "minecraft:hot_floor=#FF8C1A",
            "minecraft:fireball=#FF8C1A",
            "minecraft:unattributed_fireball=#FF8C1A",
            // explosions (same color as fire)
            "minecraft:explosion=#FF8C1A",
            "minecraft:player_explosion=#FF8C1A",
            "minecraft:fireworks=#FF8C1A",
            "minecraft:bad_respawn_point=#FF8C1A",
            // magic
            "minecraft:magic=#E53DFD",
            "minecraft:indirect_magic=#E53DFD",
            "minecraft:dragon_breath=#E53DFD",
            "minecraft:sonic_boom=#E53DFD",
            // wither
            "minecraft:wither=#8C8CA8",
            "minecraft:wither_skull=#8C8CA8",
            // cold / lightning / water
            "minecraft:freeze=#66D9FF",
            "minecraft:lightning_bolt=#FFFF66",
            "minecraft:drown=#3366FF",
            // environment
            "minecraft:fall=#C2A27A",
            "minecraft:fly_into_wall=#C2A27A",
            "minecraft:falling_block=#C2A27A",
            "minecraft:falling_anvil=#C2A27A",
            "minecraft:falling_stalactite=#C2A27A",
            "minecraft:stalagmite=#C2A27A",
            "minecraft:in_wall=#C2A27A",
            "minecraft:cramming=#C2A27A",
            "minecraft:cactus=#8BC34A",
            "minecraft:sweet_berry_bush=#8BC34A",
            "minecraft:thorns=#8BC34A",
            "minecraft:starve=#8BC34A",
            // void / kill
            "minecraft:out_of_world=#FF3366",
            "minecraft:generic_kill=#FF3366"
    );

    private static final List<String> DEFAULT_CRIT_GRADIENT_LIST = List.of(
            // normal damage (every type without its own color entry): orange-red from the bottom up to 50%
            "default=#FB601D,bottom,50",
            // magic damage: violet from the top down to 50%
            "minecraft:magic=#7E31EA,top,50",
            "minecraft:indirect_magic=#7E31EA,top,50",
            "minecraft:dragon_breath=#7E31EA,top,50",
            "minecraft:sonic_boom=#7E31EA,top,50"
    );

    private static final List<String> DEFAULT_CRIT_ICON_GRADIENT_LIST = List.of(
            // magic crit icon: violet from the top down to 80% of the icon
            "minecraft:magic=#7E31EA,top,80",
            "minecraft:indirect_magic=#7E31EA,top,80",
            "minecraft:dragon_breath=#7E31EA,top,80",
            "minecraft:sonic_boom=#7E31EA,top,80"
    );

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("General").push("general");
        ENABLED = b.comment("Show damage / heal indicators.")
                .define("enabled", true);
        MAX_VIEW_DISTANCE = b.comment("Max distance (in blocks) at which indicators are shown.")
                .defineInRange("maxViewDistance", 96, 8, 512);
        LIFETIME_TICKS = b.comment("How long an indicator lives, in ticks (20 ticks = 1 second).")
                .defineInRange("lifetimeTicks", 30, 10, 200);
        MIN_AMOUNT = b.comment("Damage / heal smaller than this is not shown.")
                .defineInRange("minAmount", 0.0D, 0.0D, 1000.0D);
        MAX_INDICATORS = b.comment("Max number of indicators alive at once (oldest are removed first).")
                .defineInRange("maxIndicators", 150, 10, 1000);
        b.pop();

        b.comment("Size").push("scale");
        BASE_SCALE = b.comment("Overall size multiplier of the numbers.")
                .defineInRange("baseScale", 1.0D, 0.1D, 5.0D);
        CRIT_SCALE = b.comment("Size multiplier for critical hits (relative to a normal hit).")
                .defineInRange("critScale", 1.5D, 1.0D, 5.0D);
        DISTANCE_SCALING = b.comment("Make numbers bigger the farther the target is, so they stay readable.")
                .define("distanceScaling", true);
        REFERENCE_DISTANCE = b.comment("Distance (blocks) at which the number has its normal size.")
                .defineInRange("referenceDistance", 8.0D, 1.0D, 64.0D);
        MIN_DISTANCE_FACTOR = b.comment("Smallest size factor used when the target is very close.")
                .defineInRange("minDistanceFactor", 0.5D, 0.05D, 1.0D);
        MAX_DISTANCE_FACTOR = b.comment("Largest size factor used when the target is very far.")
                .defineInRange("maxDistanceFactor", 12.0D, 1.0D, 50.0D);
        b.pop();

        b.comment("Colors, written as #RRGGBB. Critical hits and the crit arrow use the same color as the number.")
                .push("colors");
        DEFAULT_DAMAGE_COLOR = b.comment("Color for damage types that are not listed in damageColors.")
                .define("defaultDamageColor", "#FF4035", ValorConfig::isValidColor);
        HEAL_COLOR = b.comment("Color for healing.")
                .define("healColor", "#55FF55", ValorConfig::isValidColor);
        SHIELD_COLOR = b.comment("Color of the part of a hit that a shield absorbed.")
                .define("shieldColor", "#C4C4C4", ValorConfig::isValidColor);
        MAGIC_SHIELD_COLOR = b.comment("Color of the part of a hit that a magic shield absorbed.")
                .define("magicShieldColor", "#B77CC2", ValorConfig::isValidColor);
        DAMAGE_COLORS = b.comment("Color per damage type, as \"damage_type_id=#RRGGBB\".",
                        "Modded damage types work too, e.g. \"somemod:my_damage=#00FFAA\".")
                .defineListAllowEmpty("damageColors", DEFAULT_DAMAGE_COLOR_LIST,
                        o -> o instanceof String s && s.indexOf('=') > 0);
        b.pop();

        b.comment("Critical hits").push("crit");
        CRIT_DAMAGE_TYPES = b.comment(
                        "Damage types that are shown as critical hits (bigger number + arrow), as damage type ids.",
                        "Example: \"minecraft:on_fire\" or \"valor_items:true_damage\".",
                        "Hits that valor_items marks as critical are always shown as crit too.")
                .defineListAllowEmpty("critDamageTypes", List.of(), o -> o instanceof String s && !s.isBlank());
        CRIT_GRADIENTS = b.comment(
                        "Gradient for critical hits, as \"damage_type_id=#RRGGBB,top|bottom,percent\".",
                        "The color is used at the chosen edge of the number (top or bottom) and blends into the",
                        "normal color over that percent of the number's height; the rest keeps the normal color.",
                        "Use \"default\" as the id for damage types that have no entry in damageColors.",
                        "Damage types without a gradient entry are drawn in a single color.")
                .defineListAllowEmpty("critGradients", DEFAULT_CRIT_GRADIENT_LIST,
                        o -> o instanceof String s && s.indexOf('=') > 0);
        CRIT_ICON_GRADIENTS = b.comment(
                        "Gradient for the crit arrow icon only, same format as critGradients.",
                        "Percent is measured on the icon itself. A damage type without an entry here",
                        "uses its critGradients entry for the icon too.")
                .defineListAllowEmpty("critIconGradients", DEFAULT_CRIT_ICON_GRADIENT_LIST,
                        o -> o instanceof String s && s.indexOf('=') > 0);
        b.pop();

        b.comment("Health bar HUD (bottom-left corner)").push("hud");
        HUD_ENABLED = b.comment("Replace vanilla health, armor, food, air and experience HUD elements with the Valor HUD.")
                .define("healthBarEnabled", true);
        HUD_SCALE = b.comment("Size of the health bar.")
                .defineInRange("healthBarScale", 1.0D, 0.5D, 4.0D);
        b.pop();

        SPEC = b.build();
    }

    // ---- cached colors (rebuilt whenever the config loads / reloads) ----
    private static volatile Map<String, Integer> damageColors = Map.of();
    private static volatile Set<String> critTypes = Set.of();
    private static volatile Map<String, Gradient> gradients = Map.of();
    private static volatile Map<String, Gradient> iconGradients = Map.of();
    private static volatile int defaultColor = 0xFFFFFF;
    private static volatile int healColor = 0x55FF55;
    private static volatile int shieldColor = 0xC4C4C4;
    private static volatile int magicShieldColor = 0xB77CC2;

    private ValorConfig() {}

    public static int colorFor(String damageTypeId, boolean heal) {
        if (heal) return healColor;
        if (SHIELD_TYPE.equals(damageTypeId)) return shieldColor;
        if (MAGIC_SHIELD_TYPE.equals(damageTypeId)) return magicShieldColor;
        return damageColors.getOrDefault(damageTypeId, defaultColor);
    }

    /** Gradient for a crit of this damage type, or null for a single solid color. */
    public static Gradient gradientFor(String damageTypeId) {
        Gradient g = gradients.get(damageTypeId);
        if (g != null) return g;
        if (!damageColors.containsKey(damageTypeId) && !SHIELD_TYPE.equals(damageTypeId) && !MAGIC_SHIELD_TYPE.equals(damageTypeId)) {
            return gradients.get("default");
        }
        return null;
    }

    /** Gradient for the crit arrow icon; falls back to the text gradient when there is no icon entry. */
    public static Gradient iconGradientFor(String damageTypeId) {
        Gradient g = iconGradients.get(damageTypeId);
        if (g != null) return g;
        if (!damageColors.containsKey(damageTypeId) && !SHIELD_TYPE.equals(damageTypeId) && !MAGIC_SHIELD_TYPE.equals(damageTypeId)) {
            g = iconGradients.get("default");
            if (g != null) return g;
        }
        return gradientFor(damageTypeId);
    }

    public static boolean isCritType(String damageTypeId) {
        return critTypes.contains(damageTypeId);
    }

    @SubscribeEvent
    static void onConfigEvent(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            rebuild();
        }
    }

    private static void rebuild() {
        Map<String, Integer> map = new HashMap<>();
        for (String entry : DAMAGE_COLORS.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            Integer color = parseColor(entry.substring(eq + 1).trim());
            if (color != null) {
                map.put(entry.substring(0, eq).trim(), color);
            }
        }
        damageColors = map;

        Set<String> crit = new HashSet<>();
        for (String id : CRIT_DAMAGE_TYPES.get()) {
            crit.add(id.trim());
        }
        critTypes = crit;

        gradients = parseGradients(CRIT_GRADIENTS.get());
        iconGradients = parseGradients(CRIT_ICON_GRADIENTS.get());

        Integer def = parseColor(DEFAULT_DAMAGE_COLOR.get());
        defaultColor = def != null ? def : 0xFFFFFF;
        Integer heal = parseColor(HEAL_COLOR.get());
        healColor = heal != null ? heal : 0x55FF55;
        Integer shield = parseColor(SHIELD_COLOR.get());
        shieldColor = shield != null ? shield : 0xC4C4C4;
        Integer magicShield = parseColor(MAGIC_SHIELD_COLOR.get());
        magicShieldColor = magicShield != null ? magicShield : 0xB77CC2;
    }

    private static Map<String, Gradient> parseGradients(List<? extends String> entries) {
        Map<String, Gradient> map = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String[] parts = entry.substring(eq + 1).split(",");
            if (parts.length != 3) continue;
            Integer color = parseColor(parts[0].trim());
            String dir = parts[1].trim().toLowerCase(Locale.ROOT);
            if (color == null || !(dir.equals("top") || dir.equals("bottom"))) continue;
            double percent;
            try {
                percent = Double.parseDouble(parts[2].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            float fraction = (float) (Math.max(0.0, Math.min(100.0, percent)) / 100.0);
            map.put(entry.substring(0, eq).trim(), new Gradient(color, dir.equals("top"), fraction));
        }
        return map;
    }

    private static boolean isValidColor(Object o) {
        return o instanceof String s && parseColor(s) != null;
    }

    private static Integer parseColor(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
