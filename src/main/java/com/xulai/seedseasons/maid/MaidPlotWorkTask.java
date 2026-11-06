package com.xulai.seedseasons.maid;

import java.util.Optional;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import com.xulai.seedseasons.CropLifecycle;
import com.xulai.seedseasons.SeedSeasonsConfig;
import com.xulai.seedseasons.WaterNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class MaidPlotWorkTask extends Behavior<EntityMaid> {

    private static final double REACH = 2.5D;
    private static final double REACH_SQUARED = REACH * REACH;
    private static final long COOLDOWN_TICKS = 20L;

    private final MaidIrrigationMoveTask move;
    private BlockPos lastPos;
    private long nextReadyAt;

    public MaidPlotWorkTask(MaidIrrigationMoveTask move) {
        super(ImmutableMap.of(InitEntities.TARGET_POS.get(), MemoryStatus.VALUE_PRESENT));
        this.move = move;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        BlockPos target = target(maid);
        if (target == null) {
            return false;
        }
        if (!MaidCompat.enabled(maid)) {
            return move.hasOwnClaim();
        }
        return level.getGameTime() >= nextReadyAt && reached(maid, target);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        BlockPos target = target(maid);
        if (target == null) {
            return;
        }
        if (!MaidCompat.enabled(maid)) {
            release(maid);
            return;
        }
        boolean own = move.isOwnTarget(target);
        if (work(level, maid, target)) {
            lastPos = target;
            nextReadyAt = gameTime + COOLDOWN_TICKS;
        }
        if (own) {
            release(maid);
        }
    }

    private void release(EntityMaid maid) {
        move.finishOwnTarget();
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static boolean reached(EntityMaid maid, BlockPos pos) {
        return maid.distanceToSqr(Vec3.atCenterOf(pos)) <= REACH_SQUARED;
    }

    private boolean work(ServerLevel level, EntityMaid maid, BlockPos target) {
        BlockState state = level.getBlockState(target);
        if (state.getBlock() instanceof FarmBlock) {
            return waterField(level, maid, target) | fertilizeCrop(level, maid, target.above());
        }
        FluidState fluid = level.getFluidState(target);
        if (fluid.isSource() && fluid.is(FluidTags.WATER)) {
            return MaidBags.drawWater(level, maid, target);
        }
        if (SeedSeasonsConfig.enableCropWater() && WaterNetwork.canHoldWater(level, target) && MaidIrrigationMoveTask.thirstsForSpot(level, target)) {
            return MaidBags.pourWater(level, maid, target);
        }
        return lastPos != null && lastPos.equals(target);
    }

    private boolean waterField(ServerLevel level, EntityMaid maid, BlockPos farmland) {
        if (!CropLifecycle.thirstyAt(level, farmland.above())) {
            return false;
        }
        BlockPos spot = WaterNetwork.refillSpot(level, farmland);
        if (spot == null || !reached(maid, spot)) {
            return false;
        }
        return MaidBags.pourWater(level, maid, spot);
    }

    private boolean fertilizeCrop(ServerLevel level, EntityMaid maid, BlockPos cropPos) {
        return CropLifecycle.canFertilize(level, cropPos) && MaidBags.fertilize(level, maid, cropPos);
    }

    private static BlockPos target(EntityMaid maid) {
        Optional<PositionTracker> tracker = maid.getBrain().getMemory(InitEntities.TARGET_POS.get());
        return tracker.map(PositionTracker::currentBlockPosition).orElse(null);
    }
}
