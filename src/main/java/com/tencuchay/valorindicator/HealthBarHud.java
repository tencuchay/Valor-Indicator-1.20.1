package com.tencuchay.valorindicator.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tencuchay.valorindicator.ValorIndicator;
import com.tencuchay.valorindicator.config.ValorConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

/**
 * Player HUD built from textures/gui/health_bar.png (128x128):
 * <ul>
 *   <li>bottom-left corner: diamond badge with the level and the XP ring, health bar (with tick marks and
 *       shields), mana strip and the food bar (food + saturation, flashing preview of the food you hold);</li>
 *   <li>right of the hotbar: oxygen bubbles that turn dark one by one as the air runs out.</li>
 * </ul>
 * All coordinates below are sheet pixels. The player block sits at (0,0) of the sheet and is drawn with
 * its top-left corner as the local origin, so a sheet pixel (x, y) of the block is local (x, y).
 */
public final class HealthBarHud {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ValorIndicator.MOD_ID, "textures/gui/health_bar.png");
    private static final int TEX_SIZE = 128;

    private static final int MARGIN = 6; // distance from the screen edges (before scaling)

    // Empty frame of the player block, cut in two (badge, then frame with the bar slots)
    private static final int BLOCK_HEIGHT = 36;
    private static final int BADGE_W = 36, BADGE_H = 36;
    private static final int FRAME_X = 36, FRAME_W = 92, FRAME_H = 31;

    // Health: slot in the frame, and the green fill sprite in the sheet
    private static final int HP_X = 36, HP_Y = 12, HP_W = 90, HP_H = 8;
    private static final int HP_SPRITE_U = 36, HP_SPRITE_V = 33;
    private static final int TICK_COLOR = 0x99000000;
    private static final int TICK_HEIGHT = 5;
    // Shield (valor_items:shield), #C4C4C4 on average
    private static final int SHIELD_TOP = 0xFFD0D0D0;
    private static final int SHIELD_BOTTOM = 0xFFB8B8B8;
    // Magic shield (valor_items:magic_shield), #B77CC2 on average
    private static final int MAGIC_SHIELD_TOP = 0xFFC48ED0;
    private static final int MAGIC_SHIELD_BOTTOM = 0xFFAA6AB4;

    // Mana: slot in the frame, and the strip sprite
    private static final int MANA_X = 36, MANA_Y = 21, MANA_W = 90, MANA_H = 3;
    private static final int MANA_SPRITE_U = 36, MANA_SPRITE_V = 69;

    // Food: the slot is a parallelogram under the mana strip. The sprites are the same shape,
    // 7 px to the right and 50 / 57 rows lower in the sheet.
    private static final int FOOD_X = 29, FOOD_Y = 26, FOOD_W = 90, FOOD_H = 3;
    private static final int FOOD_SPRITE_U = 36;
    private static final int FOOD_BROWN_V = 76;   // food level
    private static final int FOOD_GOLD_V = 83;    // saturation
    private static final int MAX_FOOD = 20;

    // Badge center (the XP ring is drawn around it)
    private static final float BADGE_CX = 18f, BADGE_CY = 18f;

    // XP ring in the sheet: 32x32 at (2,39), center (18,55). On screen it is centered on the badge,
    // so a point at local y corresponds to sheet row y + 37.
    private static final float RING_SHEET_OFFSET_Y = 37f;
    private static final float RING_REACH = 16.5f;        // half size of the area that can be revealed
    private static final float RING_STEP = 0.04f;         // radians per slice of the wipe

    // Level digits in the sheet: 0-9, each 4x10, one every 8 px starting at (40,97)
    private static final int DIGIT_U = 40, DIGIT_V = 97, DIGIT_STEP = 8, DIGIT_W = 4, DIGIT_H = 10, DIGIT_GAP = 1;

    // Oxygen bubbles: {x, y, w, h} of each bright bubble in the sheet, top to bottom. The dark
    // (empty) version of the same bubble is DARK_OFFSET_X pixels to the right.
    private static final int[][] BUBBLES = {
            {7, 77, 3, 3}, {10, 78, 6, 6}, {6, 82, 4, 4}, {12, 85, 4, 4},
            {6, 87, 6, 6}, {12, 91, 3, 3}, {9, 94, 4, 4}
    };
    private static final int DARK_OFFSET_X = 15;
    private static final int CLUSTER_X = 6, CLUSTER_Y = 77, CLUSTER_H = 21; // top-left and height of the cluster
    private static final int OXYGEN_GAP = 8; // distance from the right edge of the hotbar

    private HealthBarHud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (!ValorConfig.HUD_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        // Same rule as vanilla: nothing in creative / spectator.
        if (player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;

        float maxHealth = Math.max(1f, player.getMaxHealth());
        float health = Mth.clamp(player.getHealth(), 0f, maxHealth);
        float shield = (float) Math.max(0.0, attributeValue(player, "shield"));
        float magicShield = (float) Math.max(0.0, attributeValue(player, "magic_shield"));
        // Like Liên Quân: when health + shields is more than max health, the whole bar becomes health + shields.
        // Order on the bar: health, shield, magic shield.
        float barMax = Math.max(maxHealth, health + shield + magicShield);

        double maxMana = attributeValue(player, "max_mana");
        double mana = attributeValue(player, "mana");
        float manaRatio = maxMana > 0.0 ? (float) Mth.clamp(mana / maxMana, 0.0, 1.0) : 0f;

        float scale = ValorConfig.HUD_SCALE.get().floatValue();

        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(MARGIN, screenHeight - MARGIN - BLOCK_HEIGHT * scale, 0f);
        pose.scale(scale, scale, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Empty frame: badge, and the frame with the health / mana / food slots.
        g.blit(TEXTURE, 0, 0, 0f, 0f, BADGE_W, BADGE_H, TEX_SIZE, TEX_SIZE);
        g.blit(TEXTURE, FRAME_X, 0, (float) FRAME_X, 0f, FRAME_W, FRAME_H, TEX_SIZE, TEX_SIZE);

        // Health, then the shields right after it.
        int hpFilled = Math.round(HP_W * health / barMax);
        if (hpFilled > 0) {
            g.blit(TEXTURE, HP_X, HP_Y, (float) HP_SPRITE_U, (float) HP_SPRITE_V, hpFilled, HP_H, TEX_SIZE, TEX_SIZE);
        }
        int shieldWidth = Math.min(HP_W - hpFilled, Math.round(HP_W * shield / barMax));
        if (shieldWidth > 0) {
            g.fillGradient(HP_X + hpFilled, HP_Y, HP_X + hpFilled + shieldWidth, HP_Y + HP_H,
                    SHIELD_TOP, SHIELD_BOTTOM);
        }
        int magicStart = hpFilled + shieldWidth;
        int magicWidth = Math.min(HP_W - magicStart, Math.round(HP_W * magicShield / barMax));
        if (magicWidth > 0) {
            g.fillGradient(HP_X + magicStart, HP_Y, HP_X + magicStart + magicWidth, HP_Y + HP_H,
                    MAGIC_SHIELD_TOP, MAGIC_SHIELD_BOTTOM);
        }
        drawTicks(g, barMax);

        // Mana
        int manaFilled = Math.round(MANA_W * manaRatio);
        if (manaFilled > 0) {
            g.blit(TEXTURE, MANA_X, MANA_Y, (float) MANA_SPRITE_U, (float) MANA_SPRITE_V, manaFilled, MANA_H,
                    TEX_SIZE, TEX_SIZE);
        }

        drawFood(g, player);
        g.flush();

        drawXpRing(g, player.experienceProgress);
        drawLevel(g, player.experienceLevel);

        // Own heals and damage taken float up from just above the middle of the health bar.
        HudIndicatorRenderer.render(g, partialTick, HP_X + HP_W / 2f, 4f);

        pose.popPose();

        drawOxygen(g, player, screenWidth, screenHeight, scale);
    }

    /**
     * One tick every 10 HP; every time max HP reaches 20 ticks the step grows x10
     * (200 max HP -> a tick every 100 HP, 2000 -> every 1000, ...).
     */
    private static void drawTicks(GuiGraphics g, float maxHealth) {
        float unit = 10f;
        while (maxHealth / unit >= 20f) {
            unit *= 10f;
        }
        for (int k = 1; k * unit < maxHealth - 0.001f; k++) {
            int x = HP_X + Math.round(HP_W * (k * unit / maxHealth));
            g.fill(x, HP_Y, x + 1, HP_Y + TICK_HEIGHT, TICK_COLOR);
        }
    }

    // ---------------------------------------------------------------- food

    private static void drawFood(GuiGraphics g, Player player) {
        FoodData data = player.getFoodData();
        int food = Mth.clamp(data.getFoodLevel(), 0, MAX_FOOD);
        float saturation = Mth.clamp(data.getSaturationLevel(), 0f, (float) food);

        int foodPx = Math.round(FOOD_W * food / (float) MAX_FOOD);
        int satPx = Math.round(FOOD_W * saturation / MAX_FOOD);
        foodStrip(g, FOOD_BROWN_V, 0, foodPx);
        foodStrip(g, FOOD_GOLD_V, 0, satPx);

        // Holding food: the food and saturation eating it would add flash on the bar (same rules as vanilla).
        FoodProperties held = heldFood(player);
        if (held != null) {
            int foodAfter = Math.min(MAX_FOOD, food + held.getNutrition());
            float satAfter = Math.min(saturation + held.getNutrition() * held.getSaturationModifier() * 2f,
                    (float) foodAfter);
            int foodAfterPx = Math.round(FOOD_W * foodAfter / (float) MAX_FOOD);
            int satAfterPx = Math.round(FOOD_W * satAfter / MAX_FOOD);

            // Flashes between about 20% and 90% opacity, once per second.
            float wave = 0.5f + 0.5f * Mth.sin((Util.getMillis() % 100000L) / 160f);
            int alpha = Math.round(255f * (0.2f + 0.7f * wave));
            previewStrip(g, FOOD_BROWN_V, foodPx, foodAfterPx, alpha);
            previewStrip(g, FOOD_GOLD_V, satPx, satAfterPx, alpha);
        }
    }

    /** Draws the part of a food sprite between fromPx and toPx (pixels from the left end of the bar). */
    private static void foodStrip(GuiGraphics g, int spriteV, int fromPx, int toPx) {
        if (toPx <= fromPx) return;
        g.blit(TEXTURE, FOOD_X + fromPx, FOOD_Y, (float) (FOOD_SPRITE_U + fromPx), (float) spriteV,
                toPx - fromPx, FOOD_H, TEX_SIZE, TEX_SIZE);
    }

    /**
     * Same as foodStrip but with its own opacity. The opacity is written into the vertex colors, so it does
     * not depend on any global render state (a global shader color can be reset or ignored by other code).
     */
    private static void previewStrip(GuiGraphics g, int spriteV, int fromPx, int toPx, int alpha) {
        if (toPx <= fromPx) return;

        Matrix4f m = g.pose().last().pose();
        float x0 = FOOD_X + fromPx, x1 = FOOD_X + toPx;
        float y0 = FOOD_Y, y1 = FOOD_Y + FOOD_H;
        float u0 = (FOOD_SPRITE_U + fromPx) / (float) TEX_SIZE, u1 = (FOOD_SPRITE_U + toPx) / (float) TEX_SIZE;
        float v0 = spriteV / (float) TEX_SIZE, v1 = (spriteV + FOOD_H) / (float) TEX_SIZE;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(m, x0, y0, 0f).uv(u0, v0).color(255, 255, 255, alpha).endVertex();
        buf.vertex(m, x0, y1, 0f).uv(u0, v1).color(255, 255, 255, alpha).endVertex();
        buf.vertex(m, x1, y1, 0f).uv(u1, v1).color(255, 255, 255, alpha).endVertex();
        buf.vertex(m, x1, y0, 0f).uv(u1, v0).color(255, 255, 255, alpha).endVertex();
        tesselator.end();
    }

    /** Food in the main hand or offhand that can be eaten right now, or null. */
    private static FoodProperties heldFood(Player player) {
        FoodProperties main = eatable(player, player.getMainHandItem());
        return main != null ? main : eatable(player, player.getOffhandItem());
    }

    private static FoodProperties eatable(Player player, ItemStack stack) {
        if (stack.isEmpty()) return null;
        FoodProperties props = stack.getFoodProperties(player);
        return (props != null && player.canEat(props.canAlwaysEat())) ? props : null;
    }

    // ---------------------------------------------------------------- oxygen

    /**
     * Bubbles right of the hotbar. Shown under water or while the air is not full, like vanilla.
     * Bubbles turn dark from the top one down as the air runs out.
     */
    private static void drawOxygen(GuiGraphics g, Player player, int screenWidth, int screenHeight, float scale) {
        int maxAir = Math.max(1, player.getMaxAirSupply());
        int air = Mth.clamp(player.getAirSupply(), 0, maxAir);
        if (!player.isEyeInFluid(FluidTags.WATER) && air >= maxAir) return;

        int total = BUBBLES.length;
        int present = Mth.clamp(Mth.ceil(air * (float) total / maxAir), 0, total);

        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        // Vertically centered on the hotbar (22 px tall, at the bottom of the screen).
        pose.translate(screenWidth / 2f + 91f + OXYGEN_GAP, screenHeight - 11f - CLUSTER_H * scale / 2f, 0f);
        pose.scale(scale, scale, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        for (int i = 0; i < total; i++) {
            int[] b = BUBBLES[i];
            boolean full = i >= total - present;
            int u = full ? b[0] : b[0] + DARK_OFFSET_X;
            g.blit(TEXTURE, b[0] - CLUSTER_X, b[1] - CLUSTER_Y, (float) u, (float) b[1], b[2], b[3],
                    TEX_SIZE, TEX_SIZE);
        }

        pose.popPose();
    }

    // ---------------------------------------------------------------- badge

    /** Reveals the yellow ring clockwise, starting at the left corner, like a clock wipe. */
    private static void drawXpRing(GuiGraphics g, float progress) {
        progress = Mth.clamp(progress, 0f, 1f);
        if (progress <= 0f) return;

        float phiMax = progress * (float) (Math.PI * 2.0);
        int steps = Math.max(1, (int) Math.ceil(phiMax / RING_STEP));

        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
        ringVertex(buf, m, BADGE_CX, BADGE_CY);
        // Emitted counter-clockwise (from the end of the wipe back to the start) so the
        // triangles face the viewer like the rest of the GUI.
        for (int i = steps; i >= 0; i--) {
            float phi = Math.min(phiMax, i * RING_STEP);
            float dx = -(float) Math.cos(phi); // phi = 0 -> left, growing phi goes up (clockwise on screen)
            float dy = -(float) Math.sin(phi);
            float k = RING_REACH / Math.max(Math.abs(dx), Math.abs(dy)); // hit the square around the ring
            ringVertex(buf, m, BADGE_CX + dx * k, BADGE_CY + dy * k);
        }
        tesselator.end();
    }

    private static void ringVertex(BufferBuilder buf, Matrix4f m, float x, float y) {
        buf.vertex(m, x, y, 0f).uv(x / TEX_SIZE, (y + RING_SHEET_OFFSET_Y) / TEX_SIZE).endVertex();
    }

    private static void drawLevel(GuiGraphics g, int level) {
        String text = Integer.toString(Math.max(0, level));
        int total = text.length() * DIGIT_W + (text.length() - 1) * DIGIT_GAP;
        int x = Math.round(BADGE_CX - total / 2f);
        int y = Math.round(BADGE_CY - DIGIT_H / 2f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        for (int i = 0; i < text.length(); i++) {
            int digit = text.charAt(i) - '0';
            g.blit(TEXTURE, x, y, (float) (DIGIT_U + digit * DIGIT_STEP), (float) DIGIT_V,
                    DIGIT_W, DIGIT_H, TEX_SIZE, TEX_SIZE);
            x += DIGIT_W + DIGIT_GAP;
        }
    }

    /** Value of a valor_items attribute, or 0 when the attribute (or the mod) is not there. */
    private static double attributeValue(Player player, String path) {
        ResourceLocation id = new ResourceLocation("valor_items", path);
        if (!ForgeRegistries.ATTRIBUTES.containsKey(id)) return 0.0;
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id);
        AttributeInstance instance = attribute != null ? player.getAttribute(attribute) : null;
        return instance != null ? instance.getValue() : 0.0;
    }
}
