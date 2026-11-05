package com.tencuchay.valorindicator.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tencuchay.valorindicator.config.ValorConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws the numbers for the player's own heals and damage taken above the health bar, with the same
 * movement as the numbers over mobs: grow, float up and drift sideways, drop a little, shrink fast and fade.
 * Called from the health bar HUD, so everything is in the HUD's local pixels (y grows downwards).
 */
public final class HudIndicatorRenderer {

    /** Height of a digit in local HUD pixels at animation scale 1. */
    private static final float DIGIT_HEIGHT_PX = 14f;

    // Same timeline as the world numbers (t = 0..1 over the lifetime)
    private static final float POP_END = 0.15f;
    private static final float POP_START_SCALE = 0.6f;
    private static final float POP_PEAK_SCALE = 1.3f;
    private static final float SETTLE_END = 0.5f;
    private static final float SHRINK_START = 0.6f;
    private static final float END_SCALE = 0.1f;
    private static final float PEAK_TIME = 0.45f;

    // Movement in local HUD pixels
    private static final float RISE = 14f;
    private static final float FALL = 5f;
    private static final float DRIFT = 22f;

    private HudIndicatorRenderer() {}

    /** anchorX / anchorY: where the numbers start (above the middle of the health bar). */
    public static void render(GuiGraphics g, float partial, float anchorX, float anchorY) {
        List<Indicator> list = IndicatorManager.activeHud();
        if (list.isEmpty() || !ValorConfig.ENABLED.get()) return;

        g.flush();
        Matrix4f m = g.pose().last().pose();
        float critScale = ValorConfig.CRIT_SCALE.get().floatValue();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GlyphAtlas.TEXTURE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Indicator ind : list) {
            draw(buf, m, ind, partial, anchorX, anchorY, critScale);
        }
        tesselator.end();
    }

    private static void draw(BufferBuilder buf, Matrix4f m, Indicator ind, float partial,
                             float anchorX, float anchorY, float critScale) {
        float t = Mth.clamp((ind.age + partial) / ind.lifetime, 0f, 1f);

        float fade = t < SHRINK_START ? 0f : (t - SHRINK_START) / (1f - SHRINK_START);
        int a = (int) (255f * (1f - Mth.clamp(fade, 0f, 1f)));
        if (a <= 0) return;

        float anim;
        if (t < POP_END) {
            anim = Mth.lerp(easeOutCubic(t / POP_END), POP_START_SCALE, POP_PEAK_SCALE);
        } else if (t < SETTLE_END) {
            anim = Mth.lerp((t - POP_END) / (SETTLE_END - POP_END), POP_PEAK_SCALE, 1f);
        } else if (t < SHRINK_START) {
            anim = 1f;
        } else {
            anim = Mth.lerp(fade, 1f, END_SCALE);
        }

        float s = (DIGIT_HEIGHT_PX / GlyphAtlas.DIGIT_HEIGHT) * anim * (ind.crit ? critScale : 1f);

        float height;
        if (t < PEAK_TIME) {
            height = RISE * easeOutCubic(t / PEAK_TIME);
        } else {
            float k = (t - PEAK_TIME) / (1f - PEAK_TIME);
            height = RISE - FALL * k * k;
        }
        float cx = anchorX + DRIFT * easeOutCubic(t) * ind.side;
        float cy = anchorY - height; // screen y grows downwards

        String text = ind.text;
        float total = 0f;
        if (ind.crit) {
            total += GlyphAtlas.ARROW[2] + GlyphAtlas.ARROW_GAP;
        }
        for (int i = 0; i < text.length(); i++) {
            int d = text.charAt(i) - '0';
            if (d < 0 || d > 9) continue;
            total += GlyphAtlas.digit(d)[2] + GlyphAtlas.SPACING;
        }
        total -= GlyphAtlas.SPACING;

        float pen = -total / 2f;
        if (ind.crit) {
            int[] arrow = GlyphAtlas.ARROW;
            drawGlyph(buf, m, cx, cy, s, arrow, pen, ind.rgb, ind.iconGradient, a);
            pen += arrow[2] + GlyphAtlas.ARROW_GAP;
        }
        for (int i = 0; i < text.length(); i++) {
            int d = text.charAt(i) - '0';
            if (d < 0 || d > 9) continue;
            int[] glyph = GlyphAtlas.digit(d);
            drawGlyph(buf, m, cx, cy, s, glyph, pen, ind.rgb, ind.gradient, a);
            pen += glyph[2] + GlyphAtlas.SPACING;
        }
    }

    /** One glyph, cut in two strips when it has a gradient (same idea as the world renderer). */
    private static void drawGlyph(BufferBuilder buf, Matrix4f m, float cx, float cy, float s, int[] glyph,
                                  float penX, int rgb, ValorConfig.Gradient grad, int a) {
        final int pad = GlyphAtlas.PAD;
        float halfH = glyph[3] / 2f;
        float yHigh = halfH + pad;
        float yLow = -halfH - pad;

        float x0 = cx + (penX - pad) * s;
        float x1 = cx + (penX + glyph[2] + pad) * s;
        float u0 = (glyph[0] - pad) / GlyphAtlas.TEX_SIZE;
        float u1 = (glyph[0] + glyph[2] + pad) / GlyphAtlas.TEX_SIZE;
        float vTop = (glyph[1] - pad) / GlyphAtlas.TEX_SIZE;
        float vBottom = (glyph[1] + glyph[3] + pad) / GlyphAtlas.TEX_SIZE;

        if (grad != null && grad.fraction() > 0f) {
            float half = glyph[3] / 2f;
            float ySplit = grad.fromTop()
                    ? half - grad.fraction() * glyph[3]
                    : -half + grad.fraction() * glyph[3];
            if (ySplit > yLow && ySplit < yHigh) {
                strip(buf, m, cy, s, x0, x1, ySplit, yHigh, yLow, yHigh, u0, u1, vTop, vBottom, rgb, grad, a);
                strip(buf, m, cy, s, x0, x1, yLow, ySplit, yLow, yHigh, u0, u1, vTop, vBottom, rgb, grad, a);
                return;
            }
        }
        strip(buf, m, cy, s, x0, x1, yLow, yHigh, yLow, yHigh, u0, u1, vTop, vBottom, rgb, grad, a);
    }

    /** One quad covering [yLow, yHigh] (texture pixels, +y up) of a glyph whose full range is [totalLow, totalHigh]. */
    private static void strip(BufferBuilder buf, Matrix4f m, float cy, float s, float x0, float x1,
                              float yLow, float yHigh, float totalLow, float totalHigh,
                              float u0, float u1, float vTop, float vBottom,
                              int rgb, ValorConfig.Gradient grad, int a) {
        float total = totalHigh - totalLow;
        float vHigh = vTop + (totalHigh - yHigh) / total * (vBottom - vTop);
        float vLow = vTop + (totalHigh - yLow) / total * (vBottom - vTop);

        float refHeight = total - 2f * GlyphAtlas.PAD;
        int cHigh = colorAt(yHigh, rgb, grad, refHeight);
        int cLow = colorAt(yLow, rgb, grad, refHeight);
        float yTopScreen = cy - yHigh * s;     // higher up = smaller screen y
        float yBottomScreen = cy - yLow * s;

        buf.vertex(m, x0, yTopScreen, 0f).uv(u0, vHigh).color((cHigh >> 16) & 0xFF, (cHigh >> 8) & 0xFF, cHigh & 0xFF, a).endVertex();
        buf.vertex(m, x0, yBottomScreen, 0f).uv(u0, vLow).color((cLow >> 16) & 0xFF, (cLow >> 8) & 0xFF, cLow & 0xFF, a).endVertex();
        buf.vertex(m, x1, yBottomScreen, 0f).uv(u1, vLow).color((cLow >> 16) & 0xFF, (cLow >> 8) & 0xFF, cLow & 0xFF, a).endVertex();
        buf.vertex(m, x1, yTopScreen, 0f).uv(u1, vHigh).color((cHigh >> 16) & 0xFF, (cHigh >> 8) & 0xFF, cHigh & 0xFF, a).endVertex();
    }

    private static int colorAt(float ly, int base, ValorConfig.Gradient grad, float refHeight) {
        if (grad == null || grad.fraction() <= 0f) return base;

        float half = refHeight / 2f;
        float d = grad.fromTop() ? (half - ly) / refHeight : (ly + half) / refHeight;
        if (d >= grad.fraction()) return base;
        if (d < 0f) d = 0f;

        float k = d / grad.fraction();
        int gr = (grad.rgb() >> 16) & 0xFF, gg = (grad.rgb() >> 8) & 0xFF, gb = grad.rgb() & 0xFF;
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int r = Math.round(gr + (br - gr) * k);
        int g = Math.round(gg + (bg - gg) * k);
        int b = Math.round(gb + (bb - gb) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static float easeOutCubic(float x) {
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }
}
