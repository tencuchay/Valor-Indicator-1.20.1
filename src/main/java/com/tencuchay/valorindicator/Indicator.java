package com.tencuchay.valorindicator.client;

import com.tencuchay.valorindicator.config.ValorConfig;

/** One floating number in the world (client side only). */
public final class Indicator {
    public final double x, y, z;
    public final String text;     // digits only, e.g. "1350"
    public final int rgb;         // 0xRRGGBB
    public final boolean crit;
    public final ValorConfig.Gradient gradient;     // text gradient, null = single solid color (crits only)
    public final ValorConfig.Gradient iconGradient; // crit arrow gradient, null = single solid color
    public final float side;      // -1 = drifts to one side, +1 = to the other
    public final int lifetime;    // ticks

    public int age;               // ticks
    double distSq;                // filled in every frame by the renderer

    public Indicator(double x, double y, double z, String text, int rgb,
                     boolean crit, ValorConfig.Gradient gradient, ValorConfig.Gradient iconGradient,
                     float side, int lifetime) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.text = text;
        this.rgb = rgb;
        this.crit = crit;
        this.gradient = gradient;
        this.iconGradient = iconGradient;
        this.side = side;
        this.lifetime = Math.max(1, lifetime);
    }
}
