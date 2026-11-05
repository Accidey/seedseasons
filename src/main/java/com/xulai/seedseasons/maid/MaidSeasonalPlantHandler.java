package com.xulai.seedseasons.maid;

import com.github.tartaricacid.touhoulittlemaid.api.task.ISpecialCropHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.xulai.seedseasons.CropLifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import sereneseasons.init.ModFertility;

public final class MaidSeasonalPlantHandler implements ISpecialCropHandler {

    @Override
    public boolean canPlant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        if (outOfSeason(maid, basePos.above(), seed)) {
            return false;
        }
        return ISpecialCropHandler.super.canPlant(maid, basePos, baseState, seed);
    }

    private static boolean outOfSeason(EntityMaid maid, BlockPos cropPos, ItemStack seed) {
        if (!MaidCompat.enabled(maid) || !(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!(seed.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState plant = blockItem.getBlock().defaultBlockState();
        if (!CropLifecycle.isManaged(plant)) {
            return false;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
        return !ModFertility.isCropFertile(blockId, level, cropPos);
    }
}
