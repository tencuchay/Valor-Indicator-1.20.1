package com.tencuchay.valorindicator.client;

import com.tencuchay.valorindicator.config.ValorConfig;
import com.tencuchay.valorindicator.network.IndicatorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class IndicatorManager {
    /** Numbers floating in the world. */
    private static final List<Indicator> ACTIVE = new ArrayList<>();
    /** Numbers above the player's own health bar. */
    private static final List<Indicator> HUD_ACTIVE = new ArrayList<>();

    private static final long MAX_SHOWN_VALUE = 999_999_999L;
    private static final int MAX_HUD_INDICATORS = 30;

    private IndicatorManager() {}

    public static List<Indicator> active() {
        return ACTIVE;
    }

    public static List<Indicator> activeHud() {
        return HUD_ACTIVE;
    }

    /** Called on the client main thread when a packet arrives. */
    public static void add(IndicatorPacket p) {
        if (!ValorConfig.ENABLED.get()) return;

        float amount = p.amount();
        if (Float.isNaN(amount) || amount < ValorConfig.MIN_AMOUNT.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (!p.hud()) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double maxDist = ValorConfig.MAX_VIEW_DISTANCE.get();
            double dx = p.x() - cam.x, dy = p.y() - cam.y, dz = p.z() - cam.z;
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) return;
        }

        long value = Math.min(MAX_SHOWN_VALUE, Math.max(1L, Math.round((double) amount)));

        boolean crit = !p.heal() && (p.crit() || ValorConfig.isCritType(p.typeId()));
        ValorConfig.Gradient gradient = crit ? ValorConfig.gradientFor(p.typeId()) : null;
        ValorConfig.Gradient iconGradient = crit ? ValorConfig.iconGradientFor(p.typeId()) : null;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Indicator indicator = new Indicator(
                p.x() + rnd.nextDouble(-0.15, 0.15),
                p.y() + rnd.nextDouble(0.0, 0.2),
                p.z() + rnd.nextDouble(-0.15, 0.15),
                Long.toString(value),
                ValorConfig.colorFor(p.typeId(), p.heal()),
                crit,
                gradient,
                iconGradient,
                rnd.nextBoolean() ? 1f : -1f,
                ValorConfig.LIFETIME_TICKS.get());

        List<Indicator> list = p.hud() ? HUD_ACTIVE : ACTIVE;
        int max = p.hud() ? MAX_HUD_INDICATORS : ValorConfig.MAX_INDICATORS.get();
        while (list.size() >= max) {
            list.remove(0);
        }
        list.add(indicator);
    }

    public static void tick() {
        age(ACTIVE);
        age(HUD_ACTIVE);
    }

    private static void age(List<Indicator> list) {
        for (Iterator<Indicator> it = list.iterator(); it.hasNext(); ) {
            Indicator i = it.next();
            if (++i.age >= i.lifetime) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        HUD_ACTIVE.clear();
    }
}
