package com.xulai.seedseasons.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.xulai.seedseasons.CropLifecycle;
import com.xulai.seedseasons.RainWater;
import com.xulai.seedseasons.SeedSeasonsConfig;
import com.xulai.seedseasons.WaterNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class MaidIrrigationMoveTask extends MaidMoveToBlockTask {

    private static final int SPOT_SCAN = 4;
    private static final int SPOT_PROBES = 8;

    private BlockPos ownTarget;
    private boolean placeWater;
    private boolean drawWater;
    private boolean fertilize;
    private int spotProbes;

    public MaidIrrigationMoveTask(float movementSpeed) {
        super(movementSpeed, 2);
    }

    public boolean isOwnTarget(BlockPos pos) {
        return ownTarget != null && ownTarget.equals(pos);
    }

    public void finishOwnTarget() {
        ownTarget = null;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return super.checkExtraStartConditions(level, maid) && MaidCompat.enabled(maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        boolean water = SeedSeasonsConfig.enableCropWater();
        spotProbes = 0;
        placeWater = water && MaidBags.hasWaterBucket(maid);
        drawWater = water && MaidBags.hasEmptyBucket(maid) && !MaidBags.hasWaterBucket(maid);
        fertilize = SeedSeasonsConfig.enableCropLifecycle() && SeedSeasonsConfig.boneMealPerDay() > 0 && MaidBags.hasBoneMeal(maid);
        if (placeWater || drawWater || fertilize) {
            searchForDestination(level, maid);
        }
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (placeWater && spotProbes < SPOT_PROBES && WaterNetwork.canHoldWater(level, pos)) {
            spotProbes++;
            if (thirstsForSpot(level, pos)) {
                ownTarget = pos.immutable();
                return true;
            }
        }
        if (drawWater && WaterNetwork.canDrawFrom(level, pos)) {
            ownTarget = pos.immutable();
            return true;
        }
        if (fertilize && state.getBlock() instanceof FarmBlock && CropLifecycle.canFertilize(level, pos.above())) {
            ownTarget = pos.immutable();
            return true;
        }
        return false;
    }

    static boolean thirstsForSpot(ServerLevel level, BlockPos spot) {
        for (BlockPos farmland : BlockPos.betweenClosed(spot.offset(-SPOT_SCAN, -1, -SPOT_SCAN), spot.offset(SPOT_SCAN, 0, SPOT_SCAN))) {
            if (!WaterNetwork.loaded(level, farmland)) {
                continue;
            }
            BlockState state = level.getBlockState(farmland);
            if (!(state.getBlock() instanceof FarmBlock)) {
                continue;
            }
            BlockPos cropPos = farmland.above();
            if (!CropLifecycle.isManaged(level.getBlockState(cropPos))) {
                continue;
            }
            if (WaterNetwork.needsWater(level, farmland) && !RainWater.sufficient(level, cropPos)) {
                return true;
            }
        }
        return false;
    }
}
