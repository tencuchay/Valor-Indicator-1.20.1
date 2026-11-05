package com.tencuchay.valorindicator.network;

import com.tencuchay.valorindicator.ValorIndicator;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ValorNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ValorIndicator.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private ValorNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, IndicatorPacket.class,
                IndicatorPacket::encode, IndicatorPacket::decode, IndicatorPacket::handle);
    }
}
