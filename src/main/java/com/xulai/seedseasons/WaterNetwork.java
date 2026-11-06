package com.xulai.seedseasons;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

public final class WaterNetwork {

    private static final int RADIUS = 4;
    private static final int MAX_POOL_FOR_DRAW = 64;
    private static final long CACHE_TICKS = 100L;
    private static final Direction[] PLANE = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private static final Map<ResourceKey<Level>, Map<BlockPos, Set<Long>>> SERVED_CROPS = new HashMap<>();

    private WaterNetwork() {
    }

    public static Boolean hydration(LevelReader level, BlockPos farmlandPos) {
        if (!(level instanceof ServerLevel) || !SeedSeasonsConfig.enableCropWater()) {
            return null;
        }
        return Boolean.TRUE;
    }

    public static BlockPos servingSource(Level level, BlockPos farmlandPos) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(farmlandPos.offset(-RADIUS, 0, -RADIUS), farmlandPos.offset(RADIUS, 1, RADIUS))) {
            if (!isWaterSource(level, pos)) {
                continue;
            }
            BlockPos anchor = poolAnchor(level, pos);
            if (anchor == null || !servedFarmlands(level, anchor).contains(farmlandPos.asLong())) {
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

    public static boolean loaded(Level level, BlockPos pos) {
        return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public static boolean needsWater(Level level, BlockPos farmlandPos) {
        return servingSource(level, farmlandPos) == null;
    }

    public static BlockPos refillSpot(Level level, BlockPos farmlandPos) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(farmlandPos.offset(-RADIUS, 0, -RADIUS), farmlandPos.offset(RADIUS, 0, RADIUS))) {
            if (pos.equals(farmlandPos) || !loaded(level, pos) || !canHoldWater(level, pos)) {
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

    public static boolean canHoldWater(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }
        if (!level.getBlockState(pos.below()).isSolid()) {
            return false;
        }
        for (Direction direction : PLANE) {
            BlockPos neighbor = pos.relative(direction);
            BlockState beside = level.getBlockState(neighbor);
            if (!beside.isSolid() && beside.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean canDrawFrom(Level level, BlockPos waterPos) {
        if (!isWaterSource(level, waterPos)) {
            return false;
        }
        int size = poolSize(level, waterPos, MAX_POOL_FOR_DRAW);
        if (size >= MAX_POOL_FOR_DRAW) {
            return true;
        }
        BlockPos anchor = poolAnchor(level, waterPos);
        if (anchor == null) {
            return false;
        }
        return size - 1 >= requiredSources(croppedCount(level, servedFarmlands(level, anchor)));
    }

    public static WaterSource sourceAt(Level level, BlockPos waterPos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(waterPos.getX() >> 4, waterPos.getZ() >> 4);
        if (chunk == null) {
            return null;
        }
        return WaterStates.of(chunk).getOrCreate(waterPos);
    }

    public static void registerServedCrop(ServerLevel level, BlockPos waterPos, BlockPos cropPos) {
        SERVED_CROPS.computeIfAbsent(level.dimension(), key -> new HashMap<>())
                .computeIfAbsent(waterPos.immutable(), key -> new HashSet<>()).add(cropPos.asLong());
    }

    public static void drainForDay(ServerLevel level) {
        Map<BlockPos, Set<Long>> served = SERVED_CROPS.get(level.dimension());
        if (served == null || served.isEmpty()) {
            return;
        }
        SERVED_CROPS.remove(level.dimension());
        Map<BlockPos, Set<Long>> poolCrops = new HashMap<>();
        Map<BlockPos, Set<BlockPos>> poolSources = new HashMap<>();
        for (Map.Entry<BlockPos, Set<Long>> entry : served.entrySet()) {
            BlockPos source = entry.getKey();
            if (!isWaterSource(level, source)) {
                continue;
            }
            BlockPos anchor = poolAnchor(level, source);
            if (anchor == null) {
                continue;
            }
            poolSources.computeIfAbsent(anchor, key -> new HashSet<>()).add(source);
            poolCrops.computeIfAbsent(anchor, key -> new HashSet<>()).addAll(entry.getValue());
        }
        for (Map.Entry<BlockPos, Set<Long>> entry : poolCrops.entrySet()) {
            BlockPos anchor = entry.getKey();
            Set<BlockPos> sources = poolSources.get(anchor);
            if (sources == null || sources.isEmpty()) {
                continue;
            }
            int cropCount = entry.getValue().size();
            int required = requiredSources(cropCount);
            int toDrain = Math.min(required, sources.size());
            if (toDrain <= 0) {
                continue;
            }
            List<BlockPos> sorted = new ArrayList<>(sources);
            sorted.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(anchor)).thenComparingLong(BlockPos::asLong));
            for (int index = 0; index < toDrain; index++) {
                drain(level, sorted.get(index));
            }
        }
    }

    private static int requiredSources(int cropCount) {
        return (cropCount + SeedSeasonsConfig.waterPerSource() - 1) / SeedSeasonsConfig.waterPerSource();
    }

    private static int poolSize(LevelReader level, BlockPos waterPos, int cap) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(waterPos);
        seen.add(waterPos);
        while (!queue.isEmpty() && seen.size() < cap) {
            BlockPos current = queue.poll();
            for (Direction direction : PLANE) {
                BlockPos neighbor = current.relative(direction);
                if (isWaterSource(level, neighbor) && seen.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return seen.size();
    }

    private static int croppedCount(Level level, Set<Long> farmlands) {
        int count = 0;
        for (long packed : farmlands) {
            BlockPos farmland = BlockPos.of(packed);
            if (loaded(level, farmland) && CropLifecycle.isManaged(level.getBlockState(farmland.above()))) {
                count++;
            }
        }
        return count;
    }

    private static BlockPos poolAnchor(Level level, BlockPos waterPos) {
        WaterSource source = sourceAt(level, waterPos);
        if (source == null) {
            return null;
        }
        long gameTime = level.getGameTime();
        if (source.poolAnchorValid(gameTime)) {
            BlockPos cached = source.poolAnchor();
            return isWaterSource(level, cached) ? cached : null;
        }
        Set<BlockPos> pool = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(waterPos);
        pool.add(waterPos);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : PLANE) {
                BlockPos neighbor = current.relative(direction);
                if (isWaterSource(level, neighbor) && pool.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        BlockPos anchor = pool.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElse(waterPos);
        for (BlockPos pos : pool) {
            WaterSource member = sourceAt(level, pos);
            if (member != null) {
                member.setPoolAnchor(anchor, gameTime + CACHE_TICKS);
            }
        }
        return anchor;
    }

    private static Set<Long> servedFarmlands(Level level, BlockPos anchor) {
        if (!isWaterSource(level, anchor)) {
            return Set.of();
        }
        WaterSource anchorSource = sourceAt(level, anchor);
        if (anchorSource == null) {
            return Set.of();
        }
        long gameTime = level.getGameTime();
        if (anchorSource.cacheValid(gameTime)) {
            return anchorSource.served();
        }
        Set<BlockPos> pool = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(anchor);
        pool.add(anchor);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : PLANE) {
                BlockPos neighbor = current.relative(direction);
                if (isWaterSource(level, neighbor) && pool.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        Set<Long> uniqueFarmlands = new HashSet<>();
        for (BlockPos water : pool) {
            for (BlockPos pos : BlockPos.betweenClosed(water.offset(-RADIUS, -1, -RADIUS), water.offset(RADIUS, 0, RADIUS))) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk != null && chunk.getBlockState(pos).getBlock() instanceof FarmBlock) {
                    uniqueFarmlands.add(pos.asLong());
                }
            }
        }
        List<BlockPos> sorted = new ArrayList<>();
        for (long packed : uniqueFarmlands) {
            sorted.add(BlockPos.of(packed));
        }
        sorted.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(anchor)).thenComparingLong(BlockPos::asLong));
        int limit = Math.min(pool.size() * SeedSeasonsConfig.waterPerSource(), sorted.size());
        Set<Long> served = new HashSet<>();
        for (int index = 0; index < limit; index++) {
            served.add(sorted.get(index).asLong());
        }
        anchorSource.setServed(served, gameTime + CACHE_TICKS);
        return served;
    }

    public static void drain(ServerLevel level, BlockPos waterPos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(waterPos.getX() >> 4, waterPos.getZ() >> 4);
        if (chunk != null) {
            WaterStates.of(chunk).remove(waterPos);
        }
        level.setBlock(waterPos, Blocks.AIR.defaultBlockState(), 2);
    }

    private static boolean isWaterSource(LevelReader level, BlockPos pos) {
        if (level instanceof Level chunkLevel) {
            LevelChunk chunk = chunkLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return false;
            }
        }
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && fluid.isSource();
    }
}