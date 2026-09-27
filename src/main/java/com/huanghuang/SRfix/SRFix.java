package com.huanghuang.SRfix;

import com.huanghuang.SRfix.util.SRfixConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SRFix.MODID)
public class SRFix {
    public static final String MODID = "srfix";
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings({"removal", "deprecation"})
    public SRFix() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info(">>> Better Summoning Rituals 已经成功加载！");

        event.enqueueWork(() -> {
            SRfixConfig.load();
        });
    }
}
