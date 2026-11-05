package com.tencuchay.valorindicator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Stats panel opened with the U key, in two columns.
 * Rows with a breakdown look like "301 (263+38)": total, then (base + bonus from gear/effects).
 * Attributes from other mods are looked up by id, so this works with or without them installed.
 */
public final class StatsScreen extends Screen {

    /**
     * langKey: stat.valor_indicator.<langKey>.
     * scale: the attribute value is multiplied by this before it is shown (1 = as is).
     * percent: add a % sign.
     * breakdown: show (base+bonus) after the total.
     */
    private record Stat(String langKey, Supplier<Attribute> attribute, double scale, boolean percent, boolean breakdown) {}

    private record Segment(String text, int color) {}

    private static final Stat ATTACK = new Stat("attack", () -> Attributes.ATTACK_DAMAGE, 1, false, true);
    private static final Stat MAX_HEALTH = new Stat("max_health", () -> Attributes.MAX_HEALTH, 1, false, true);
    private static final Stat ARMOR = new Stat("armor", () -> Attributes.ARMOR, 1, false, true);
    private static final Stat ATTACK_SPEED = new Stat("attack_speed", () -> Attributes.ATTACK_SPEED, 10, true, false); // 4.0 -> 40%
    private static final Stat CRIT_RATE = new Stat("crit_rate", () -> modded("valor_items", "crit_rate"), 100, true, false);
    private static final Stat HEALTH_REGEN = new Stat("health_regen", () -> modded("valor_items", "health_regen"), 1, false, false);

    private static final Stat MAGIC_POWER = new Stat("magic_power", () -> modded("valor_items", "magic_damage"), 1, false, true);
    private static final Stat MAX_MANA = new Stat("max_mana", () -> modded("valor_items", "max_mana"), 1, false, true);
    private static final Stat MAGIC_ARMOR = new Stat("magic_armor", () -> modded("valor_items", "magic_armor"), 1, false, true);
    private static final Stat MOVE_SPEED = new Stat("move_speed", () -> Attributes.MOVEMENT_SPEED, 1000, false, true); // 0.1 -> 100
    private static final Stat CRIT_DAMAGE = new Stat("crit_damage", () -> modded("valor_items", "crit_damage"), 100, true, false);
    private static final Stat MANA_REGEN = new Stat("mana_regen", () -> modded("valor_items", "mana_regen"), 1, false, false);

    private static final List<Stat> COLUMN_1 = List.of(ATTACK, MAX_HEALTH, ARMOR, ATTACK_SPEED, CRIT_RATE, HEALTH_REGEN);
    private static final List<Stat> COLUMN_2 = List.of(MAGIC_POWER, MAX_MANA, MAGIC_ARMOR, MOVE_SPEED, CRIT_DAMAGE, MANA_REGEN);

    private static final int PANEL_WIDTH = 480;
    private static final int HEADER = 34;
    private static final int ROW_HEIGHT = 18;
    private static final int PADDING = 12;
    private static final int COLUMN_GAP = 16;

    private static final int PANEL_COLOR = 0xF0141A2E;
    private static final int BORDER_COLOR = 0xFF4A5A8C;
    private static final int DIVIDER_COLOR = 0x553A4A7A;
    private static final int ROW_STRIPE = 0x18FFFFFF;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFB8C0DC;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;

    /** Smaller differences count as "unchanged". */
    private static final double EPSILON = 1.0E-4;

    private static final DecimalFormat NUMBER =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public StatsScreen() {
        super(Component.translatable("screen.valor_indicator.stats.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Pressing the same key again closes the screen.
        if (StatsKey.OPEN.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int w = Math.min(PANEL_WIDTH, this.width - 16);
        int columnWidth = (w - 2 * PADDING - COLUMN_GAP) / 2;
        int rows = Math.max(COLUMN_1.size(), COLUMN_2.size());
        int h = HEADER + rows * ROW_HEIGHT + PADDING;
        int left = (this.width - w) / 2;
        int top = (this.height - h) / 2;

        // panel + border
        g.fill(left, top, left + w, top + h, PANEL_COLOR);
        g.fill(left, top, left + w, top + 1, BORDER_COLOR);
        g.fill(left, top + h - 1, left + w, top + h, BORDER_COLOR);
        g.fill(left, top, left + 1, top + h, BORDER_COLOR);
        g.fill(left + w - 1, top, left + w, top + h, BORDER_COLOR);

        g.drawCenteredString(this.font, this.title, this.width / 2, top + 11, TITLE_COLOR);
        g.fill(left + PADDING, top + HEADER - 6, left + w - PADDING, top + HEADER - 5, BORDER_COLOR);

        // thin line between the two columns
        int dividerX = left + PADDING + columnWidth + COLUMN_GAP / 2;
        g.fill(dividerX, top + HEADER - 3, dividerX + 1, top + h - PADDING + 3, DIVIDER_COLOR);

        Player player = Minecraft.getInstance().player;
        drawColumn(g, COLUMN_1, left + PADDING, columnWidth, top + HEADER, player);
        drawColumn(g, COLUMN_2, left + PADDING + columnWidth + COLUMN_GAP, columnWidth, top + HEADER, player);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawColumn(GuiGraphics g, List<Stat> stats, int x, int width, int topY, Player player) {
        int y = topY;
        for (int i = 0; i < stats.size(); i++) {
            Stat stat = stats.get(i);
            if (i % 2 == 0) {
                g.fill(x - 4, y - 3, x + width + 4, y + ROW_HEIGHT - 3, ROW_STRIPE);
            }
            g.drawString(this.font, Component.translatable("stat.valor_indicator." + stat.langKey()),
                    x, y + 1, LABEL_COLOR, false);
            drawValue(g, valueSegments(stat, player), x + width, y + 1);
            y += ROW_HEIGHT;
        }
    }

    /** Draws the segments right-aligned so that their end is at rightX. */
    private void drawValue(GuiGraphics g, List<Segment> segments, int rightX, int y) {
        int total = 0;
        for (Segment s : segments) total += this.font.width(s.text());
        int x = rightX - total;
        for (Segment s : segments) {
            g.drawString(this.font, s.text(), x, y, s.color(), false);
            x += this.font.width(s.text());
        }
    }

    private static List<Segment> valueSegments(Stat stat, Player player) {
        List<Segment> out = new ArrayList<>();

        Attribute attribute = stat.attribute().get();
        AttributeInstance instance = (player != null && attribute != null) ? player.getAttribute(attribute) : null;
        double total = instance != null ? instance.getValue() : 0.0;
        double base = instance != null ? instance.getBaseValue() : 0.0;

        if (!stat.breakdown()) {
            boolean changed = instance != null && Math.abs(total - base) >= EPSILON;
            out.add(new Segment(format(total, stat), changed ? GREEN : WHITE));
            return out;
        }

        double bonus = total - base;
        if (Math.abs(bonus) < EPSILON) {
            out.add(new Segment(format(total, stat), WHITE));
            return out;
        }

        int color = GREEN; // any change from the default is green, also when it is a decrease
        out.add(new Segment(format(total, stat), color));
        out.add(new Segment(" (", WHITE));
        out.add(new Segment(format(base, stat), WHITE));
        out.add(new Segment((bonus > 0 ? "+" : "-") + format(Math.abs(bonus), stat), color));
        out.add(new Segment(")", WHITE));
        return out;
    }

    private static String format(double value, Stat stat) {
        value *= stat.scale();
        if (Math.abs(value) < 0.005) value = 0.0;
        return NUMBER.format(value) + (stat.percent() ? "%" : "");
    }

    /** Attribute from another mod by id, or null when that mod / attribute is not present. */
    private static Attribute modded(String namespace, String path) {
        ResourceLocation id = new ResourceLocation(namespace, path);
        return ForgeRegistries.ATTRIBUTES.containsKey(id) ? ForgeRegistries.ATTRIBUTES.getValue(id) : null;
    }
}
