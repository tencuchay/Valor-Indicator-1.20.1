package com.tencuchay.valorindicator.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Key that opens the stats screen (default: U, can be changed in Controls). */
public final class StatsKey {
    public static final KeyMapping OPEN = new KeyMapping(
            "key.valor_indicator.open_stats",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            "key.categories.valor_indicator");

    private StatsKey() {}
}
