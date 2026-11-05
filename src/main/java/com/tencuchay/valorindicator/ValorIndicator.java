package com.tencuchay.valorindicator;

import com.tencuchay.valorindicator.config.ValorConfig;
import com.tencuchay.valorindicator.network.ValorNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ValorIndicator.MOD_ID)
public class ValorIndicator {
    public static final String MOD_ID = "valor_indicator";

    public ValorIndicator() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        // Colors / scale / view distance are purely visual, so they live in the CLIENT config.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ValorConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ValorNetwork::register);
    }
}
