package com.tencuchay.valorindicator.client;

import com.tencuchay.valorindicator.ValorIndicator;
import net.minecraft.resources.ResourceLocation;

/**
 * Positions of the glyphs inside textures/misc/valor_font.png (256x256).
 * Each entry is {x, y, width, height} in pixels. If you redraw the sheet and
 * the glyphs move, update the numbers here.
 */
public final class GlyphAtlas {
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(ValorIndicator.MOD_ID, "textures/misc/valor_font.png");

    public static final float TEX_SIZE = 256f;

    /** All digits share this height; it is also the reference height for sizing. */
    public static final int DIGIT_HEIGHT = 36;

    /** Extra pixels around each glyph when drawing, so the filtered edge is not cut off. */
    public static final int PAD = 1;

    /** Gap between two digits, in texture pixels. */
    public static final int SPACING = 1;

    /** Gap between the crit arrow and the first digit, in texture pixels. */
    public static final int ARROW_GAP = 4;

    private static final int[][] DIGITS = {
            {14, 14, 27, 36},   // 0
            {62, 14, 19, 36},   // 1
            {110, 14, 25, 36},  // 2
            {158, 14, 26, 36},  // 3
            {206, 14, 27, 36},  // 4
            {14, 78, 24, 36},   // 5
            {62, 78, 25, 36},   // 6
            {110, 78, 25, 36},  // 7
            {158, 78, 25, 36},  // 8
            {206, 78, 25, 36}   // 9
    };

    /** Critical hit arrow. */
    public static final int[] ARROW = {8, 142, 30, 30};

    private GlyphAtlas() {}

    public static int[] digit(int d) {
        return DIGITS[d];
    }
}
