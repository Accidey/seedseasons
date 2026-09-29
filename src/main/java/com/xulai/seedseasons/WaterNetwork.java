package com.xulai.seedseasons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

public final class WaterNetwork {

    private static final int RADIUS = 4;
    private static final long CACHE_TICKS = 20L;

    private WaterNetwork() {
    }

    public static Boolean hydration(LevelReader level, BlockPos farmlandPos) {
        if (!(level instanceof ServerLevel serverLevel) || !SeedSeasonsConfig.enableCropWater()) {
            return null;
        }
        BlockPos sourcePos = servingSource(serverLevel, farmlandPos);
        if (sourcePos == null) {
            return Boolean.FALSE;
        }
        return sourceAt(serverLevel, sourcePos) != null;
    }

    public static BlockPos servingSource(ServerLevel level, BlockPos farmlandPos) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(farmlandPos.offset(-RADIUS, 0, -RADIUS), farmlandPos.offset(RADIUS, 1, RADIUS))) {
            if (!isWaterSource(level, pos)) {
                continue;
            }
            WaterSource source = sourceAt(level, pos);
            if (source == null || !servedFarmlands(level, pos, source).contains(farmlandPos.asLong())) {
                continue;
            }
            double distance = pos.distSqr(farmlandPos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    public static WaterSource sourceAt(ServerLevel level, BlockPos waterPos) {
        if (!isWaterSource(level, waterPos)) {
            return null;
        }
        LevelChunk chunk = level.getChunkAt(waterPos);
        WaterStates states = WaterStates.of(chunk);
        WaterSource source = states.get(waterPos);
        if (source == null) {
            source = new WaterSource(SeedSeasonsConfig.waterPerSource());
            states.put(waterPos, source);
            chunk.setUnsaved(true);
        } else if (source.level() > SeedSeasonsConfig.waterPerSource()) {
            source.setLevel(SeedSeasonsConfig.waterPerSource());
            chunk.setUnsaved(true);
        }
        return source;
    }

    public static void consume(ServerLevel level, BlockPos waterPos, double amount) {
        WaterSource source = sourceAt(level, waterPos);
        if (source == null || amount <= 0.0D) {
            return;
        }
        source.setLevel(Math.max(0.0D, source.level() - amount));
        level.getChunkAt(waterPos).setUnsaved(true);
    }

    public static boolean refill(ServerLevel level, BlockPos waterPos) {
        WaterSource source = sourceAt(level, waterPos);
        if (source == null) {
            return false;
        }
        source.setLevel(SeedSeasonsConfig.waterPerSource());
        level.getChunkAt(waterPos).setUnsaved(true);
        return true;
    }

    private static boolean isWaterSource(LevelReader level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && fluid.isSource();
    }

    private static Set<Long> servedFarmlands(ServerLevel level, BlockPos waterPos, WaterSource source) {
        long gameTime = level.getGameTime();
        if (source.cacheValid(gameTime)) {
            return source.served();
        }
        List<BlockPos> farmlands = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(waterPos.offset(-RADIUS, -1, -RADIUS), waterPos.offset(RADIUS, 0, RADIUS))) {
            if (level.getBlockState(pos).getBlock() instanceof FarmBlock) {
                farmlands.add(pos.immutable());
            }
        }
        farmlands.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(waterPos)).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        Set<Long> served = new HashSet<>();
        int limit = Math.min(SeedSeasonsConfig.waterPerSource(), farmlands.size());
        for (int index = 0; index < limit; index++) {
            served.add(farmlands.get(index).asLong());
        }
        source.setServed(served, gameTime + CACHE_TICKS);
        return served;
    }
}
