package com.tencuchay.valorindicator.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tencuchay.valorindicator.ValorIndicator;
import com.tencuchay.valorindicator.config.ValorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

/**
 * Player HUD in the bottom-left corner, built from textures/gui/health_bar.png (128x128):
 * diamond badge with the level, health/mana/food bars, oxygen bubbles and an XP ring.
 *
 * All coordinates below are sheet pixels; the player block sits at (0,0) of the sheet and is
 * drawn with its top-left corner as the local origin.
 */
public final class HealthBarHud {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ValorIndicator.MOD_ID, "textures/gui/health_bar.png");
    private static final int TEX_SIZE = 128;

    private static final int MARGIN = 6; // distance from the screen edges (before scaling)

    // Player block, cut in two so the ally bar below it is not picked up
    private static final int BLOCK_HEIGHT = 36;
    private static final int BADGE_W = 36, BADGE_H = 36;              // badge + start of the frame
    private static final int BARS_X = 36, BARS_W = 92, BARS_H = 26;   // frame with the bars

    // Bars inside the block (full bar as drawn in the sheet)
    private static final int HP_X = 36, HP_Y = 12, HP_W = 90, HP_H = 8;
    private static final int MANA_X = 36, MANA_Y = 21, MANA_W = 90, MANA_H = 3;
    private static final int FOOD_X = 36, FOOD_Y = 26, FOOD_W = 90, FOOD_H = 3;

    // Filled bar swatches in health_bar.png. The frames at the top of the sheet stay dark;
    // the filled portion is copied from these rows so a bar can shrink cleanly from right to left.
    private static final int HEALTH_FILL_U = 36, HEALTH_FILL_V = 33;
    private static final int FOOD_FILL_U = 36, FOOD_FILL_V = 90;
    private static final int SATURATION_FILL_U = 36, SATURATION_FILL_V = 83;
    private static final int MANA_FILL_U = 36, MANA_FILL_V = 69;

    // Two compact 2x5 bubble grids in the texture: full bubbles at x=6 and empty bubbles at x=21.
    private static final int BUBBLE_FULL_U = 6, BUBBLE_EMPTY_U = 21, BUBBLE_V = 77;
    private static final int BUBBLE_W = 5, BUBBLE_H = 4, BUBBLES_PER_COLUMN = 5;
    private static final int EMPTY_COLOR = 0xFF0A0E12;   // shown where the bar is empty
    private static final int TICK_COLOR = 0x99000000;
    private static final int TICK_HEIGHT = 5;
    // Shield (valor_items:shield), #C4C4C4 on average
    private static final int SHIELD_TOP = 0xFFD0D0D0;
    private static final int SHIELD_BOTTOM = 0xFFB8B8B8;
    // Magic shield (valor_items:magic_shield), #B77CC2 on average
    private static final int MAGIC_SHIELD_TOP = 0xFFC48ED0;
    private static final int MAGIC_SHIELD_BOTTOM = 0xFFAA6AB4;

    // Badge center (the XP ring is drawn around it)
    private static final float BADGE_CX = 18f, BADGE_CY = 18f;

    // XP ring in the sheet: 32x32 at (2,39), center (18,55). On screen it is centered on the badge,
    // so a point at local y corresponds to sheet row y + 37.
    private static final float RING_SHEET_OFFSET_Y = 37f;
    private static final float RING_REACH = 16.5f;        // half size of the area that can be revealed
    private static final float RING_STEP = 0.04f;         // radians per slice of the wipe

    // Level digits in the sheet: 0-9, each 4x10, one every 8 px starting at (40,97).
    // The redesigned HUD sheet moved this font from row 50 to row 97.
    private static final int DIGIT_U = 40, DIGIT_V = 97, DIGIT_STEP = 8, DIGIT_W = 4, DIGIT_H = 10, DIGIT_GAP = 1;

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
        FoodData foodData = player.getFoodData();
        float foodRatio = Mth.clamp(foodData.getFoodLevel() / 20f, 0f, 1f);
        // The client already knows saturation. Showing it as a thin golden overlay gives the
        // useful AppleSkin saturation information without requiring AppleSkin as a dependency.
        float saturationRatio = Mth.clamp(Math.min(foodData.getSaturationLevel(), foodData.getFoodLevel()) / 20f, 0f, 1f);

        float scale = ValorConfig.HUD_SCALE.get().floatValue();

        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(MARGIN, screenHeight - MARGIN - BLOCK_HEIGHT * scale, 0f);
        pose.scale(scale, scale, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        g.blit(TEXTURE, 0, 0, 0f, 0f, BADGE_W, BADGE_H, TEX_SIZE, TEX_SIZE);
        g.blit(TEXTURE, BARS_X, 0, (float) BARS_X, 0f, BARS_W, BARS_H, TEX_SIZE, TEX_SIZE);

        int hpFilled = Math.round(HP_W * health / barMax);
        drawBarFill(g, HP_X, HP_Y, hpFilled, HP_H, HEALTH_FILL_U, HEALTH_FILL_V);
        g.fill(HP_X + hpFilled, HP_Y, HP_X + HP_W, HP_Y + HP_H, EMPTY_COLOR);
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
        int manaFilled = Math.round(MANA_W * manaRatio);
        drawBarFill(g, MANA_X, MANA_Y, manaFilled, MANA_H, MANA_FILL_U, MANA_FILL_V);
        g.fill(MANA_X + manaFilled, MANA_Y, MANA_X + MANA_W, MANA_Y + MANA_H, EMPTY_COLOR);

        int foodFilled = Math.round(FOOD_W * foodRatio);
        drawBarFill(g, FOOD_X, FOOD_Y, foodFilled, FOOD_H, FOOD_FILL_U, FOOD_FILL_V);
        g.fill(FOOD_X + foodFilled, FOOD_Y, FOOD_X + FOOD_W, FOOD_Y + FOOD_H, EMPTY_COLOR);
        // Golden saturation highlight, equivalent to AppleSkin's saturation overlay. It pulses
        // gently over the red hunger fill so the otherwise hidden saturation level is visible.
        int saturationFilled = Math.round(FOOD_W * saturationRatio);
        if (saturationFilled > 0) {
            float wave = (Mth.sin((player.tickCount + partialTick) * 0.15f) + 1f) * 0.5f;
            int highlightAlpha = Mth.floor(70f + 45f * wave);
            g.fill(FOOD_X, FOOD_Y, FOOD_X + saturationFilled, FOOD_Y + FOOD_H,
                    (highlightAlpha << 24) | 0x00FFD55F);
            // Fine yellow lower edge keeps the saturation value readable at every HUD scale.
            drawBarFill(g, FOOD_X, FOOD_Y + FOOD_H - 1, saturationFilled, 1,
                    SATURATION_FILL_U, SATURATION_FILL_V);
        }
        drawTicks(g, barMax);
        g.flush();

        drawXpRing(g, player.experienceProgress);
        drawLevel(g, player.experienceLevel);

        // Own heals and damage taken float up from just above the middle of the health bar.
        HudIndicatorRenderer.render(g, partialTick, HP_X + HP_W / 2f, 4f);

        pose.popPose();
        renderAirBubbles(g, player, screenWidth, screenHeight);
    }

    private static void drawBarFill(GuiGraphics g, int x, int y, int width, int height, int u, int v) {
        if (width > 0) {
            g.blit(TEXTURE, x, y, (float) u, (float) v, width, height, TEX_SIZE, TEX_SIZE);
        }
    }

    /**
     * Replaces Minecraft's air meter with the bubbles from the custom sheet. Like vanilla, it is
     * only visible after the player has started losing air. Each of the ten bubbles turns empty as
     * the air supply falls, and the compact grid sits immediately to the right of the hotbar.
     */
    private static void renderAirBubbles(GuiGraphics g, Player player, int screenWidth, int screenHeight) {
        int maxAir = Math.max(1, player.getMaxAirSupply());
        int air = player.getAirSupply();
        if (air >= maxAir) return;

        int fullBubbles = Mth.ceil(Mth.clamp(air / (float) maxAir, 0f, 1f) * 10f);
        int x = screenWidth / 2 + 96;
        int y = screenHeight - 42;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        for (int index = 0; index < 10; index++) {
            int column = index / BUBBLES_PER_COLUMN;
            int row = index % BUBBLES_PER_COLUMN;
            int sourceX = (index < fullBubbles ? BUBBLE_FULL_U : BUBBLE_EMPTY_U) + column * BUBBLE_W;
            g.blit(TEXTURE, x + column * BUBBLE_W, y + row * BUBBLE_H, (float) sourceX,
                    (float) (BUBBLE_V + row * BUBBLE_H), BUBBLE_W, BUBBLE_H, TEX_SIZE, TEX_SIZE);
        }
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
