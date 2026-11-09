package com.xulai.seedseasons.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class SeedSeasonsClient {

    private SeedSeasonsClient() {
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * 只在客户端分支被调用：打开灌溉机的水位条界面。
     */
    public static void openIrrigatorScreen(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            IrrigatorScreen.open(level, pos);
        }
    }
}
