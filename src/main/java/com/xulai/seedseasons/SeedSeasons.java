package com.xulai.seedseasons;

import com.mojang.logging.LogUtils;
import com.xulai.seedseasons.client.SeedSeasonsClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(SeedSeasons.MODID)
public class SeedSeasons {

    public static final String MODID = "seedseasons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SeedSeasons(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SeedSeasonsConfig.SPEC);
        CropStates.ATTACHMENT_TYPES.register(modEventBus);
        WaterStates.ATTACHMENT_TYPES.register(modEventBus);
        if (FMLEnvironment.dist.isClient()) {
            SeedSeasonsClient.register(modContainer);
        }
    }
}
