package com.tencuchay.valorindicator.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tencuchay.valorindicator.config.ValorConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the numbers directly in camera (view) space, so they always face the player
 * regardless of what the pose stack contains at this render stage.
 */
public final class IndicatorRenderer {

    /** Height of a digit in blocks at baseScale 1, factor 1, animation scale 1. */
    private static final float DIGIT_WORLD_HEIGHT = 0.5f;

    // ---- Animation (t = 0..1 over the lifetime) ----
    // Size: pop up -> settle -> shrink fast at the end.
    private static final float POP_END = 0.15f;
    private static final float POP_START_SCALE = 0.6f;
    private static final float POP_PEAK_SCALE = 1.3f;
    private static final float SETTLE_END = 0.5f;     // scale is 1.0 here
    private static final float SHRINK_START = 0.6f;   // fast shrink + fade start here
    private static final float END_SCALE = 0.1f;

    // Height: float up until PEAK_TIME, then drop a little.
    private static final float PEAK_TIME = 0.45f;
    private static final float BASE_LIFT = 0.25f;     // blocks
    private static final float RISE = 0.9f;           // blocks, up to the peak
    private static final float FALL = 3.0f;           // blocks, dropped after the peak
    private static final float DRIFT = 0.7f;          // blocks, sideways (random side)

    private static final List<Indicator> VISIBLE = new ArrayList<>();

    private IndicatorRenderer() {}

    public static void render(Camera camera, float partial) {
        List<Indicator> all = IndicatorManager.active();
        if (all.isEmpty() || !ValorConfig.ENABLED.get()) return;

        Vec3 cam = camera.getPosition();
        double maxDist = ValorConfig.MAX_VIEW_DISTANCE.get();
        double maxSq = maxDist * maxDist;

        VISIBLE.clear();
        for (Indicator i : all) {
            double dx = i.x - cam.x, dy = i.y - cam.y, dz = i.z - cam.z;
            i.distSq = dx * dx + dy * dy + dz * dz;
            if (i.distSq <= maxSq) VISIBLE.add(i);
        }
        if (VISIBLE.isEmpty()) return;

        // Far to near, so closer numbers are painted on top of farther ones.
        VISIBLE.sort((a, b) -> Double.compare(b.distSq, a.distSq));

        float baseScale = ValorConfig.BASE_SCALE.get().floatValue();
        float critScale = ValorConfig.CRIT_SCALE.get().floatValue();
        boolean distScaling = ValorConfig.DISTANCE_SCALING.get();
        double refDist = ValorConfig.REFERENCE_DISTANCE.get();
        double minFactor = ValorConfig.MIN_DISTANCE_FACTOR.get();
        double maxFactor = Math.max(minFactor, ValorConfig.MAX_DISTANCE_FACTOR.get());

        // camera.rotation() maps camera space (x = left, y = up, z = forward) to world space,
        // so its inverse turns a world offset into camera space.
        Quaternionf inverse = camera.rotation().conjugate(new Quaternionf());
        Vector3f tmp = new Vector3f();

        // Vertices below are already in view space, so the model-view matrix must be identity.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.last().pose().identity();
        RenderSystem.applyModelViewMatrix();

        // No depth test = never hidden behind blocks or entities.
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GlyphAtlas.TEXTURE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (Indicator ind : VISIBLE) {
            drawIndicator(buf, inverse, tmp, cam, ind, partial,
                    baseScale, critScale, distScaling, refDist, minFactor, maxFactor);
        }

        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    private static void drawIndicator(BufferBuilder buf, Quaternionf inverse, Vector3f tmp, Vec3 cam,
                                      Indicator ind, float partial,
                                      float baseScale, float critScale, boolean distScaling,
                                      double refDist, double minFactor, double maxFactor) {
        float t = Mth.clamp((ind.age + partial) / ind.lifetime, 0f, 1f);

        // Fade together with the fast shrink at the end.
        float fade = t < SHRINK_START ? 0f : (t - SHRINK_START) / (1f - SHRINK_START);
        int a = (int) (255f * (1f - Mth.clamp(fade, 0f, 1f)));
        if (a <= 0) return;

        // Size: pop up, settle to 1.0, then shrink fast.
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

        // Farther target -> bigger number, so it stays readable.
        float factor = 1f;
        if (distScaling) {
            factor = (float) Mth.clamp(Math.sqrt(ind.distSq) / refDist, minFactor, maxFactor);
        }

        float pixelWorld = DIGIT_WORLD_HEIGHT * baseScale / GlyphAtlas.DIGIT_HEIGHT;
        float s = pixelWorld * factor * anim * (ind.crit ? critScale : 1f);

        // Height: float up to the peak, then fall a little.
        float height;
        if (t < PEAK_TIME) {
            height = RISE * easeOutCubic(t / PEAK_TIME);
        } else {
            float k = (t - PEAK_TIME) / (1f - PEAK_TIME);
            height = RISE - FALL * k * k;
        }
        float up = (BASE_LIFT + height) * factor;
        float right = DRIFT * easeOutCubic(t) * factor * ind.side;

        // Anchor in camera space: tmp = (left, up, forward) of the offset from the camera.
        tmp.set((float) (ind.x - cam.x), (float) (ind.y - cam.y), (float) (ind.z - cam.z)).rotate(inverse);
        float cx = -tmp.x + right;   // view space: +x = screen right
        float cy = tmp.y + up;       // +y = screen up
        float cz = -tmp.z;           // camera looks down -z
        if (cz > -0.05f) return;     // behind (or on top of) the camera

        // Total width in texture pixels, to center the whole thing.
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
            drawGlyph(buf, cx, cy, cz, s, arrow, pen, ind.rgb, ind.iconGradient, a);
            pen += arrow[2] + GlyphAtlas.ARROW_GAP;
        }
        for (int i = 0; i < text.length(); i++) {
            int d = text.charAt(i) - '0';
            if (d < 0 || d > 9) continue;
            int[] glyph = GlyphAtlas.digit(d);
            drawGlyph(buf, cx, cy, cz, s, glyph, pen, ind.rgb, ind.gradient, a);
            pen += glyph[2] + GlyphAtlas.SPACING;
        }
    }

    /**
     * Draws one glyph (left edge at penX texture pixels from the anchor), centered vertically on the anchor.
     * With a gradient the glyph is cut into two strips at the point where the gradient ends,
     * so the color can blend over part of the height and stay solid for the rest.
     */
    private static void drawGlyph(BufferBuilder buf, float cx, float cy, float cz, float s, int[] glyph,
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
                strip(buf, cy, cz, s, x0, x1, ySplit, yHigh, yLow, yHigh, u0, u1, vTop, vBottom, rgb, grad, a);
                strip(buf, cy, cz, s, x0, x1, yLow, ySplit, yLow, yHigh, u0, u1, vTop, vBottom, rgb, grad, a);
                return;
            }
        }
        strip(buf, cy, cz, s, x0, x1, yLow, yHigh, yLow, yHigh, u0, u1, vTop, vBottom, rgb, grad, a);
    }

    /** One quad covering [yLow, yHigh] (texture pixels, +y up) of a glyph whose full range is [totalLow, totalHigh]. */
    private static void strip(BufferBuilder buf, float cy, float cz, float s, float x0, float x1,
                              float yLow, float yHigh, float totalLow, float totalHigh,
                              float u0, float u1, float vTop, float vBottom,
                              int rgb, ValorConfig.Gradient grad, int a) {
        float total = totalHigh - totalLow;
        float vHigh = vTop + (totalHigh - yHigh) / total * (vBottom - vTop);
        float vLow = vTop + (totalHigh - yLow) / total * (vBottom - vTop);

        float refHeight = total - 2f * GlyphAtlas.PAD; // height of the glyph itself, without padding
        int cHigh = colorAt(yHigh, rgb, grad, refHeight);
        int cLow = colorAt(yLow, rgb, grad, refHeight);
        float yvHigh = cy + yHigh * s;
        float yvLow = cy + yLow * s;

        buf.vertex(x0, yvHigh, cz).uv(u0, vHigh).color((cHigh >> 16) & 0xFF, (cHigh >> 8) & 0xFF, cHigh & 0xFF, a).endVertex();
        buf.vertex(x0, yvLow, cz).uv(u0, vLow).color((cLow >> 16) & 0xFF, (cLow >> 8) & 0xFF, cLow & 0xFF, a).endVertex();
        buf.vertex(x1, yvLow, cz).uv(u1, vLow).color((cLow >> 16) & 0xFF, (cLow >> 8) & 0xFF, cLow & 0xFF, a).endVertex();
        buf.vertex(x1, yvHigh, cz).uv(u1, vHigh).color((cHigh >> 16) & 0xFF, (cHigh >> 8) & 0xFF, cHigh & 0xFF, a).endVertex();
    }

    /** Color at height ly (texture pixels, +y up, 0 = middle of the glyph, refHeight = glyph height). */
    private static int colorAt(float ly, int base, ValorConfig.Gradient grad, float refHeight) {
        if (grad == null || grad.fraction() <= 0f) return base;

        float lineHeight = refHeight;
        float half = lineHeight / 2f;
        // 0 at the gradient's edge, 1 at the opposite edge
        float d = grad.fromTop() ? (half - ly) / lineHeight : (ly + half) / lineHeight;
        if (d >= grad.fraction()) return base;
        if (d < 0f) d = 0f;

        float k = d / grad.fraction(); // 0 = gradient color, 1 = normal color
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
