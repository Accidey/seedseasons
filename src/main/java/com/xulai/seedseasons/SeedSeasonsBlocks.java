package com.xulai.seedseasons;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SeedSeasonsBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SeedSeasons.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SeedSeasons.MODID);

    public static final DeferredBlock<IrrigatorBlock> IRRIGATOR = BLOCKS.register("irrigator",
            () -> new IrrigatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> IRRIGATOR_ITEM = ITEMS.registerSimpleBlockItem(IRRIGATOR);

    private SeedSeasonsBlocks() {
    }
}
