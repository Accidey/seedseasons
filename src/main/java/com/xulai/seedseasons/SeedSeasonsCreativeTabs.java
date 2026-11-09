package com.xulai.seedseasons;

import net.minecraft.world.item.CreativeModeTabs;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 把灌溉机挂进原版"功能方块"创造栏页签。
 * 原版页签物品列表是硬编码的，mod 物品需要通过 NeoForge 的页签构建事件追加；
 * 物品标签（minecraft:functional_blocks）不参与页签填充。
 */
@EventBusSubscriber(modid = SeedSeasons.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SeedSeasonsCreativeTabs {

    private SeedSeasonsCreativeTabs() {
    }

    @SubscribeEvent
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SeedSeasonsBlocks.IRRIGATOR_ITEM);
        }
    }
}
