package com.tencuchay.valorindicator.client;

import com.tencuchay.valorindicator.ValorIndicator;
import com.tencuchay.valorindicator.config.ValorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ValorIndicator.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            IndicatorManager.clear();
            return;
        }
        while (StatsKey.OPEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new StatsScreen());
            }
        }

        if (mc.isPaused()) return;

        IndicatorManager.tick();
    }

    /*
     * AppleSkin renders its saturation/exhaustion layers from the FOOD overlay event.  Cancelling
     * that event before AppleSkin handles it also suppresses those vanilla-positioned layers, so
     * they cannot be left floating over Valor's replacement food bar.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!ValorConfig.HUD_ENABLED.get()) return;

        ResourceLocation id = event.getOverlay().id();
        // Hearts, experience bar, armor, food and air are replaced by the Valor HUD.
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Drawn after clouds and weather so nothing in the world is painted over the numbers.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;

        IndicatorRenderer.render(event.getCamera(), event.getPartialTick());
    }
}
