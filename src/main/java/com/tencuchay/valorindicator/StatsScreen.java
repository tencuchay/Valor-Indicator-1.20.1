package com.tencuchay.valorindicator.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Supports hover tooltips, click-to-pin side panel with scrolling, and item textures for modifiers.
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

    private record ModifierDisplay(String name, ItemStack icon) {}

    private static final Stat ATTACK = new Stat("attack", () -> Attributes.ATTACK_DAMAGE, 1, false, true);
    private static final Stat MAX_HEALTH = new Stat("max_health", () -> Attributes.MAX_HEALTH, 1, false, true);
    private static final Stat ARMOR = new Stat("armor", () -> Attributes.ARMOR, 1, false, true);
    private static final Stat ATTACK_SPEED = new Stat("attack_speed", () -> Attributes.ATTACK_SPEED, 10, true, false); // 4.0 -> 40%
    private static final Stat CRIT_RATE = new Stat("crit_rate", () -> modded("valor_items", "crit_rate"), 100, true, false);
    private static final Stat HEALTH_REGEN = new Stat("health_regen", () -> modded("valor_items", "health_regen"), 1, false, false);
    private static final Stat LETHALITY = new Stat("lethality", () -> modded("valor_items", "lethality"), 1, false, false);

    private static final Stat MAGIC_POWER = new Stat("magic_power", () -> modded("valor_items", "magic_damage"), 1, false, true);
    private static final Stat MAX_MANA = new Stat("max_mana", () -> modded("valor_items", "max_mana"), 1, false, true);
    private static final Stat MAGIC_ARMOR = new Stat("magic_armor", () -> modded("valor_items", "magic_armor"), 1, false, true);
    private static final Stat MOVE_SPEED = new Stat("move_speed", () -> Attributes.MOVEMENT_SPEED, 1000, false, true); // 0.1 -> 100
    private static final Stat CRIT_DAMAGE = new Stat("crit_damage", () -> modded("valor_items", "crit_damage"), 100, true, false);
    private static final Stat MANA_REGEN = new Stat("mana_regen", () -> modded("valor_items", "mana_regen"), 1, false, false);
    private static final Stat ARMOR_PENETRATION_PERCENT = new Stat("armor_penetration_percent", () -> modded("valor_items", "armor_penetration_percent"), 100, true, false);

    private static final List<Stat> COLUMN_1 = List.of(ATTACK, MAX_HEALTH, ARMOR, ATTACK_SPEED, CRIT_RATE, HEALTH_REGEN, LETHALITY);
    private static final List<Stat> COLUMN_2 = List.of(MAGIC_POWER, MAX_MANA, MAGIC_ARMOR, MOVE_SPEED, CRIT_DAMAGE, MANA_REGEN, ARMOR_PENETRATION_PERCENT);

    private static final int PANEL_WIDTH = 480;
    private static final int SIDE_WIDTH = 240;
    private static final int HEADER = 34;
    private static final int ROW_HEIGHT = 18;
    private static final int MOD_ROW_HEIGHT = 20;
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
    private static final int RED = 0xFFFF5555;
    private static final int ACCENT_GOLD = 0xFFE0C068;

    /** Smaller differences count as "unchanged". */
    private static final double EPSILON = 1.0E-4;

    private static final DecimalFormat NUMBER =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    /** Stat currently selected for the detailed side panel (pinned). */
    private Stat selectedStat = null;

    /** Scroll offset in pixels for the side panel modifier list. */
    private int sideScrollOffset = 0;

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
        if (com.tencuchay.valorindicator.client.StatsKey.OPEN.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // left click
            if (isOverCloseButton(mouseX, mouseY)) {
                this.selectedStat = null;
                this.sideScrollOffset = 0;
                return true;
            }
            Stat clicked = getStatAt(mouseX, mouseY);
            if (clicked != null) {
                if (this.selectedStat == clicked) {
                    this.selectedStat = null;
                } else {
                    this.selectedStat = clicked;
                    this.sideScrollOffset = 0;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.selectedStat != null) {
            int w = Math.min(PANEL_WIDTH, this.width - 16);
            int rows = Math.max(COLUMN_1.size(), COLUMN_2.size());
            int h = HEADER + rows * ROW_HEIGHT + PADDING;
            int sideW = SIDE_WIDTH;
            int totalW = Math.min(this.width - 16, w + 8 + sideW);
            int left = Math.max(8, (this.width - totalW) / 2);
            int top = Math.max(8, (this.height - h) / 2);
            int sideLeft = left + w + 8;
            if (sideLeft + sideW > this.width - 4) {
                sideLeft = this.width - sideW - 4;
            }

            if (mouseX >= sideLeft && mouseX <= sideLeft + sideW && mouseY >= top && mouseY <= top + h) {
                int scrollStep = (int) (delta * MOD_ROW_HEIGHT);
                this.sideScrollOffset = Math.max(0, this.sideScrollOffset - scrollStep);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int w = Math.min(PANEL_WIDTH, this.width - 16);
        int columnWidth = (w - 2 * PADDING - COLUMN_GAP) / 2;
        int rows = Math.max(COLUMN_1.size(), COLUMN_2.size());
        int h = HEADER + rows * ROW_HEIGHT + PADDING;

        boolean showSide = (this.selectedStat != null);
        int sideW = SIDE_WIDTH;
        int totalW = showSide ? Math.min(this.width - 16, w + 8 + sideW) : w;

        int left = Math.max(8, (this.width - totalW) / 2);
        int top = Math.max(8, (this.height - h) / 2);

        int sideLeft = left + w + 8;
        if (sideLeft + sideW > this.width - 4) {
            sideLeft = this.width - sideW - 4;
        }

        // Main panel background + border
        g.fill(left, top, left + w, top + h, PANEL_COLOR);
        drawOutline(g, left, top, w, h, BORDER_COLOR);

        g.drawCenteredString(this.font, this.title, left + w / 2, top + 11, TITLE_COLOR);
        g.fill(left + PADDING, top + HEADER - 6, left + w - PADDING, top + HEADER - 5, BORDER_COLOR);

        // Thin line between the two columns
        int dividerX = left + PADDING + columnWidth + COLUMN_GAP / 2;
        g.fill(dividerX, top + HEADER - 3, dividerX + 1, top + h - PADDING + 3, DIVIDER_COLOR);

        Player player = Minecraft.getInstance().player;
        int col1X = left + PADDING;
        int col2X = left + PADDING + columnWidth + COLUMN_GAP;

        Stat hovered1 = drawColumn(g, COLUMN_1, col1X, columnWidth, top + HEADER, player, mouseX, mouseY);
        Stat hovered2 = drawColumn(g, COLUMN_2, col2X, columnWidth, top + HEADER, player, mouseX, mouseY);
        Stat hovered = hovered1 != null ? hovered1 : hovered2;

        if (showSide) {
            drawSidePanel(g, sideLeft, top, sideW, h, player, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Render hover tooltip on top of all panels
        if (hovered != null && hovered != this.selectedStat) {
            renderStatTooltip(g, hovered, mouseX, mouseY, player);
        }
    }

    private Stat drawColumn(GuiGraphics g, List<Stat> stats, int x, int width, int topY, Player player, int mouseX, int mouseY) {
        Stat hovered = null;
        int y = topY;
        for (int i = 0; i < stats.size(); i++) {
            Stat stat = stats.get(i);
            boolean isHovered = mouseX >= x - 4 && mouseX <= x + width + 4 && mouseY >= y - 3 && mouseY <= y + ROW_HEIGHT - 3;
            boolean isSelected = (this.selectedStat == stat);

            if (isSelected) {
                g.fill(x - 4, y - 3, x + width + 4, y + ROW_HEIGHT - 3, 0x35E0C068);
                drawOutline(g, x - 4, y - 3, width + 8, ROW_HEIGHT, ACCENT_GOLD);
            } else if (isHovered) {
                g.fill(x - 4, y - 3, x + width + 4, y + ROW_HEIGHT - 3, 0x25FFFFFF);
                drawOutline(g, x - 4, y - 3, width + 8, ROW_HEIGHT, 0x664A5A8C);
                hovered = stat;
            } else if (i % 2 == 0) {
                g.fill(x - 4, y - 3, x + width + 4, y + ROW_HEIGHT - 3, ROW_STRIPE);
            }

            int labelColor = isSelected ? 0xFFFFF0A0 : (isHovered ? WHITE : LABEL_COLOR);
            g.drawString(this.font, Component.translatable("stat.valor_indicator." + stat.langKey()),
                    x, y + 1, labelColor, false);
            drawValue(g, valueSegments(stat, player), x + width, y + 1);
            y += ROW_HEIGHT;
        }
        return hovered;
    }

    /** Draws the detailed breakdown side panel for the selected stat with item icons and scrolling. */
    private void drawSidePanel(GuiGraphics g, int x, int y, int width, int height, Player player, int mouseX, int mouseY) {
        if (this.selectedStat == null) return;
        Stat stat = this.selectedStat;

        // Background + border
        g.fill(x, y, x + width, y + height, PANEL_COLOR);
        drawOutline(g, x, y, width, height, BORDER_COLOR);

        // Header: Stat Name
        Component titleComp = Component.translatable("stat.valor_indicator." + stat.langKey());
        g.drawString(this.font, titleComp, x + PADDING, y + 11, ACCENT_GOLD, false);

        // Close button [X]
        int closeX = x + width - PADDING - 8;
        int closeY = y + 10;
        boolean overClose = mouseX >= closeX - 3 && mouseX <= closeX + 11 && mouseY >= closeY - 3 && mouseY <= closeY + 11;
        int closeColor = overClose ? RED : 0xFFAAAAAA;
        g.drawString(this.font, "✕", closeX, closeY, closeColor, false);

        // Divider below header
        g.fill(x + PADDING, y + HEADER - 6, x + width - PADDING, y + HEADER - 5, BORDER_COLOR);

        Attribute attribute = stat.attribute().get();
        AttributeInstance instance = (player != null && attribute != null) ? player.getAttribute(attribute) : null;
        double base = instance != null ? instance.getBaseValue() : 0.0;
        double total = instance != null ? instance.getValue() : 0.0;
        double bonus = total - base;

        int cy = y + HEADER;

        // Total display
        g.drawString(this.font, Component.translatable("stat.valor_indicator.breakdown.total_label"), x + PADDING, cy, LABEL_COLOR, false);
        int totalColor = Math.abs(bonus) >= EPSILON ? GREEN : WHITE;
        String totalStr = format(total, stat);
        g.drawString(this.font, totalStr, x + width - PADDING - this.font.width(totalStr), cy, totalColor, false);
        cy += 14;

        // Base display
        g.drawString(this.font, Component.translatable("stat.valor_indicator.breakdown.base_label"), x + PADDING, cy, LABEL_COLOR, false);
        String baseStr = format(base, stat);
        g.drawString(this.font, baseStr, x + width - PADDING - this.font.width(baseStr), cy, 0xFF888888, false);
        cy += 18;

        // Divider
        g.fill(x + PADDING, cy - 4, x + width - PADDING, cy - 3, DIVIDER_COLOR);

        // Modifiers section header
        g.drawString(this.font, Component.translatable("stat.valor_indicator.breakdown.modifiers"), x + PADDING, cy, ACCENT_GOLD, false);
        cy += 14;

        if (instance == null || instance.getModifiers().isEmpty()) {
            g.drawString(this.font, Component.translatable("stat.valor_indicator.breakdown.no_modifiers"), x + PADDING, cy, 0xFF666666, false);
            return;
        }

        List<AttributeModifier> modifiers = new ArrayList<>(instance.getModifiers());
        int listTop = cy;
        int listBottom = y + height - PADDING;
        int listHeight = listBottom - listTop;
        int totalContentHeight = modifiers.size() * MOD_ROW_HEIGHT;
        int maxScroll = Math.max(0, totalContentHeight - listHeight);

        if (this.sideScrollOffset > maxScroll) {
            this.sideScrollOffset = maxScroll;
        }

        // Enable scissor clipping for smooth scrolling
        g.enableScissor(x + PADDING - 2, listTop, x + width - PADDING + 2, listBottom);

        int drawY = listTop - this.sideScrollOffset;
        for (int i = 0; i < modifiers.size(); i++) {
            AttributeModifier mod = modifiers.get(i);
            if (drawY + MOD_ROW_HEIGHT >= listTop && drawY <= listBottom) {
                if (i % 2 == 0) {
                    g.fill(x + PADDING - 2, drawY - 1, x + width - PADDING + 2, drawY + MOD_ROW_HEIGHT - 1, ROW_STRIPE);
                }

                ModifierDisplay display = resolveModifier(mod.getName());

                // Render item texture
                if (!display.icon().isEmpty()) {
                    g.renderFakeItem(display.icon(), x + PADDING, drawY + 2);
                }

                String val = formatModifier(mod, stat);
                int valColor = mod.getAmount() >= 0 ? GREEN : RED;
                int valWidth = this.font.width(val);

                int rightMargin = (maxScroll > 0) ? 6 : 0;
                g.drawString(this.font, val, x + width - PADDING - rightMargin - valWidth, drawY + 6, valColor, false);

                int textStartX = x + PADDING + (display.icon().isEmpty() ? 0 : 20);
                int maxNameWidth = x + width - PADDING - rightMargin - valWidth - textStartX - 4;
                String name = display.name();
                if (this.font.width(name) > maxNameWidth && maxNameWidth > 10) {
                    name = this.font.plainSubstrByWidth(name, maxNameWidth - this.font.width("...")) + "...";
                }
                g.drawString(this.font, name, textStartX, drawY + 6, 0xFFDDDDDD, false);
            }
            drawY += MOD_ROW_HEIGHT;
        }

        g.disableScissor();

        // Draw scrollbar if content overflows
        if (maxScroll > 0) {
            int scrollbarX = x + width - PADDING + 1;
            g.fill(scrollbarX, listTop, scrollbarX + 2, listBottom, 0x40000000);

            int thumbHeight = Math.max(16, (int) ((float) listHeight / totalContentHeight * listHeight));
            int thumbY = listTop + (int) ((float) this.sideScrollOffset / maxScroll * (listHeight - thumbHeight));
            g.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, BORDER_COLOR);
        }
    }

    /** Hover tooltip for quick preview. */
    private void renderStatTooltip(GuiGraphics g, Stat stat, int mouseX, int mouseY, Player player) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("stat.valor_indicator." + stat.langKey()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        Attribute attribute = stat.attribute().get();
        AttributeInstance instance = (player != null && attribute != null) ? player.getAttribute(attribute) : null;
        double base = instance != null ? instance.getBaseValue() : 0.0;
        double total = instance != null ? instance.getValue() : 0.0;

        lines.add(Component.translatable("stat.valor_indicator.breakdown.base", format(base, stat)).withStyle(ChatFormatting.GRAY));

        if (instance != null && !instance.getModifiers().isEmpty()) {
            lines.add(Component.translatable("stat.valor_indicator.breakdown.modifiers").withStyle(ChatFormatting.YELLOW));
            for (AttributeModifier mod : instance.getModifiers()) {
                String val = formatModifier(mod, stat);
                ModifierDisplay display = resolveModifier(mod.getName());
                ChatFormatting color = mod.getAmount() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
                lines.add(Component.literal("  " + val + " ").withStyle(color).append(Component.literal(display.name()).withStyle(ChatFormatting.WHITE)));
            }
        } else {
            lines.add(Component.translatable("stat.valor_indicator.breakdown.no_modifiers").withStyle(ChatFormatting.DARK_GRAY));
        }

        lines.add(Component.translatable("stat.valor_indicator.breakdown.total", format(total, stat)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.empty());
        lines.add(Component.translatable("stat.valor_indicator.breakdown.hint").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));

        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private static String formatModifier(AttributeModifier mod, Stat stat) {
        double amount = mod.getAmount();
        AttributeModifier.Operation op = mod.getOperation();
        String sign = amount >= 0 ? "+" : "";

        if (op == AttributeModifier.Operation.ADDITION) {
            double scaled = amount * stat.scale();
            return sign + NUMBER.format(scaled) + (stat.percent() ? "%" : "");
        } else if (op == AttributeModifier.Operation.MULTIPLY_BASE) {
            return sign + NUMBER.format(amount * 100.0) + "% (Base)";
        } else {
            return sign + NUMBER.format(amount * 100.0) + "% (Total)";
        }
    }

    /** Resolves modifier names and matches them to official Valor items or vanilla items with their textures. */
    private static ModifierDisplay resolveModifier(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.unknown").getString(), ItemStack.EMPTY);
        }

        String lower = rawName.toLowerCase(Locale.ROOT);

        // 1. Check Claves Sancti specifically (including "infinite_crit_rate", "infinite_crit_damage", "gift_of_the_swift")
        if (lower.contains("infinite") || lower.contains("gift_of_the_swift") || lower.contains("claves_sancti")) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("valor_items", "claves_sancti"));
            ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            String itemName = item != null ? stack.getHoverName().getString() : "Thánh Kiếm";
            if (lower.contains("infinite")) {
                String sub = Component.translatable("stat.valor_indicator.breakdown.infinite").getString();
                return new ModifierDisplay(itemName + " (" + sub + ")", stack);
            } else if (lower.contains("gift_of_the_swift")) {
                String sub = Component.translatable("stat.valor_indicator.breakdown.swift").getString();
                return new ModifierDisplay(itemName + " (" + sub + ")", stack);
            } else {
                return new ModifierDisplay(itemName, stack);
            }
        }

        // 2. Check known valor_items relics
        String[] relics = {
                "amulet_of_longevity", "blitz_blade", "death_sickle",
                "devils_handshake", "fafnirs_talon", "fenrirs_fang", "gaias_standard",
                "hercules_madness", "mail_of_pain", "mantle_of_ra", "odins_will",
                "rankbreaker", "stikks_sting", "uriels_brand"
        };
        for (String relicId : relics) {
            if (lower.contains(relicId)) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("valor_items", relicId));
                if (item != null) {
                    ItemStack stack = new ItemStack(item);
                    return new ModifierDisplay(stack.getHoverName().getString(), stack);
                }
            }
        }

        // 3. Passives and events in valor_items
        if (lower.contains("break")) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("valor_items", "rankbreaker"));
            ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            String itemName = item != null ? stack.getHoverName().getString() : "Thương Xuyên Phá";
            String name = itemName + " (" + Component.translatable("stat.valor_indicator.breakdown.break").getString() + ")";
            return new ModifierDisplay(name, stack);
        }
        if (lower.contains("wildwalk")) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("valor_items", "rankbreaker"));
            ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            String itemName = item != null ? stack.getHoverName().getString() : "Thương Xuyên Phá";
            String name = itemName + " (" + Component.translatable("stat.valor_indicator.breakdown.wildwalk").getString() + ")";
            return new ModifierDisplay(name, stack);
        }
        if (lower.contains("magic_guardian") || lower.contains("devils_bane")) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("valor_items", "uriels_brand"));
            ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            String itemName = item != null ? stack.getHoverName().getString() : "Gươm Uriel";
            String sub = lower.contains("magic_guardian")
                    ? Component.translatable("stat.valor_indicator.breakdown.guardian").getString()
                    : Component.translatable("stat.valor_indicator.breakdown.bane").getString();
            return new ModifierDisplay(itemName + " (" + sub + ")", stack);
        }
        if (lower.contains("mana_per_level") || lower.contains("mana_level")) {
            ItemStack stack = new ItemStack(Items.EXPERIENCE_BOTTLE);
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.level").getString(), stack);
        }
        if (lower.contains("adrenaline")) {
            ItemStack stack = new ItemStack(Items.POTION);
            return new ModifierDisplay("Adrenaline", stack);
        }
        if (lower.contains("flame_wraith")) {
            ItemStack stack = new ItemStack(Items.BLAZE_POWDER);
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.flame_wraith").getString(), stack);
        }
        if (lower.contains("winds_blessing")) {
            ItemStack stack = new ItemStack(Items.FEATHER);
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.winds_blessing").getString(), stack);
        }

        // 3. Vanilla and Curios equipment
        if (rawName.equalsIgnoreCase("Weapon modifier") || lower.contains("weapon")) {
            ItemStack stack = new ItemStack(Items.IRON_SWORD);
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.weapon").getString(), stack);
        }
        if (rawName.equalsIgnoreCase("Armor modifier") || lower.contains("armor")) {
            ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.armor").getString(), stack);
        }
        if (rawName.equalsIgnoreCase("Armor toughness") || lower.contains("toughness")) {
            ItemStack stack = new ItemStack(Items.DIAMOND_CHESTPLATE);
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.toughness").getString(), stack);
        }
        if (rawName.equalsIgnoreCase("Tool modifier") || lower.contains("tool")) {
            ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.tool").getString(), stack);
        }
        if (lower.contains("curios")) {
            ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING);
            return new ModifierDisplay(Component.translatable("stat.valor_indicator.breakdown.mod.curios").getString(), stack);
        }

        // Fallback: Title Case
        String clean = rawName.replace("valor_items:", "").replace('_', ' ').trim();
        String[] words = clean.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1));
        }
        return new ModifierDisplay(sb.toString(), ItemStack.EMPTY);
    }

    private Stat getStatAt(double mouseX, double mouseY) {
        int w = Math.min(PANEL_WIDTH, this.width - 16);
        int columnWidth = (w - 2 * PADDING - COLUMN_GAP) / 2;
        int rows = Math.max(COLUMN_1.size(), COLUMN_2.size());
        int h = HEADER + rows * ROW_HEIGHT + PADDING;

        int sideW = SIDE_WIDTH;
        boolean showSide = (this.selectedStat != null);
        int totalW = showSide ? Math.min(this.width - 16, w + 8 + sideW) : w;
        int left = Math.max(8, (this.width - totalW) / 2);
        int top = Math.max(8, (this.height - h) / 2);

        int col1X = left + PADDING;
        int col2X = left + PADDING + columnWidth + COLUMN_GAP;
        int startY = top + HEADER;

        for (int i = 0; i < COLUMN_1.size(); i++) {
            int y = startY + i * ROW_HEIGHT;
            if (mouseX >= col1X - 4 && mouseX <= col1X + columnWidth + 4 && mouseY >= y - 3 && mouseY <= y + ROW_HEIGHT - 3) {
                return COLUMN_1.get(i);
            }
        }
        for (int i = 0; i < COLUMN_2.size(); i++) {
            int y = startY + i * ROW_HEIGHT;
            if (mouseX >= col2X - 4 && mouseX <= col2X + columnWidth + 4 && mouseY >= y - 3 && mouseY <= y + ROW_HEIGHT - 3) {
                return COLUMN_2.get(i);
            }
        }
        return null;
    }

    private boolean isOverCloseButton(double mouseX, double mouseY) {
        if (this.selectedStat == null) return false;
        int w = Math.min(PANEL_WIDTH, this.width - 16);
        int rows = Math.max(COLUMN_1.size(), COLUMN_2.size());
        int h = HEADER + rows * ROW_HEIGHT + PADDING;
        int sideW = SIDE_WIDTH;
        int totalW = Math.min(this.width - 16, w + 8 + sideW);
        int left = Math.max(8, (this.width - totalW) / 2);
        int top = Math.max(8, (this.height - h) / 2);
        int sideLeft = left + w + 8;
        if (sideLeft + sideW > this.width - 4) {
            sideLeft = this.width - sideW - 4;
        }
        int closeX = sideLeft + sideW - PADDING - 8;
        int closeY = top + 10;
        return mouseX >= closeX - 4 && mouseX <= closeX + 12 && mouseY >= closeY - 4 && mouseY <= closeY + 12;
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y, x + 1, y + height, color);
        g.fill(x + width - 1, y, x + width, y + height, color);
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
