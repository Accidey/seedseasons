package com.xulai.seedseasons.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

final class MaidBags {

    private MaidBags() {
    }

    static boolean hasWaterBucket(EntityMaid maid) {
        return ItemsUtil.isStackIn(maid.getAvailableInv(true), stack -> stack.is(Items.WATER_BUCKET));
    }

    static boolean hasEmptyBucket(EntityMaid maid) {
        return ItemsUtil.isStackIn(maid.getAvailableInv(true), stack -> stack.is(Items.BUCKET));
    }

    static boolean hasBoneMeal(EntityMaid maid) {
        return ItemsUtil.isStackIn(maid.getAvailableInv(true), stack -> stack.getItem() instanceof BoneMealItem);
    }

    static boolean pourWater(ServerLevel level, EntityMaid maid, BlockPos spot) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        int slot = ItemsUtil.findStackSlot(inv, stack -> stack.is(Items.WATER_BUCKET));
        if (slot < 0 || !level.setBlock(spot, Blocks.WATER.defaultBlockState(), 11)) {
            return false;
        }
        inv.setStackInSlot(slot, new ItemStack(Items.BUCKET));
        level.playSound(null, spot, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        maid.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    static boolean drawWater(ServerLevel level, EntityMaid maid, BlockPos source) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        int slot = ItemsUtil.findStackSlot(inv, stack -> stack.is(Items.BUCKET));
        if (slot < 0 || inv.getStackInSlot(slot).isEmpty() || !isStillWater(level, source)) {
            return false;
        }
        level.setBlock(source, Blocks.AIR.defaultBlockState(), 512);
        inv.getStackInSlot(slot).shrink(1);
        ItemHandlerHelper.insertItemStacked(inv, new ItemStack(Items.WATER_BUCKET), false);
        level.playSound(null, source, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        maid.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    static boolean fertilize(ServerLevel level, EntityMaid maid, BlockPos cropPos) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        int slot = ItemsUtil.findStackSlot(inv, stack -> stack.getItem() instanceof BoneMealItem);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = inv.getStackInSlot(slot);
        BoneMealItem.applyBonemeal(stack, level, cropPos, null);
        inv.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        maid.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static boolean isStillWater(ServerLevel level, BlockPos source) {
        FluidState fluid = level.getFluidState(source);
        return fluid.isSource() && fluid.is(FluidTags.WATER);
    }
}
