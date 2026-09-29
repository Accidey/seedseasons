package com.xulai.seedseasons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public final class RainSystem {

    private static final double TICKS_PER_DAY = 24000.0D;
    private static final long HANDLING_INTERVAL = 100L;
    private static final int CANDIDATE_LIMIT = 4096;

    private static final int[][] SEASON_WEIGHTS = {
            {25, 30, 22, 15, 8},
            {10, 20, 25, 25, 20},
            {22, 28, 25, 15, 10},
            {40, 28, 18, 10, 4},
    };

    private static final class RainState {
        int level;
        long startedAt;
        long stableUntil;
        long lastRoll;
        long lastHandled;
        final Map<BlockPos, Double> puddles = new HashMap<>();
    }

    private static final Map<ResourceKey<Level>, RainState> STATES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new HashMap<>();

    private RainSystem() {
    }

    public static void onChunkLoad(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel)) {
            return;
        }
        LOADED_CHUNKS.computeIfAbsent(chunk.getLevel().dimension(), key -> new HashSet<>()).add(chunk.getPos().toLong());
    }

    public static void onChunkUnload(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel)) {
            return;
        }
        Set<Long> chunks = LOADED_CHUNKS.get(chunk.getLevel().dimension());
        if (chunks != null) {
            chunks.remove(chunk.getPos().toLong());
        }
    }

    public static void serverTick(MinecraftServer server) {
        if (!SeedSeasonsConfig.enableRainWater()) {
            STATES.clear();
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            RainState state = STATES.get(level.dimension());
            if (isRaining(level)) {
                if (state == null) {
                    state = begin(level);
                }
                handle(level, state);
            } else if (state != null) {
                STATES.remove(level.dimension());
            }
        }
    }

    private static boolean isRaining(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && level.getLevelData().isRaining();
    }

    public static boolean freeWatering(ServerLevel level, BlockPos pos) {
        if (!SeedSeasonsConfig.enableRainWater()) {
            return false;
        }
        return STATES.containsKey(level.dimension()) && level.isRainingAt(pos);
    }

    public static boolean rainGrowth(ServerLevel level, BlockPos pos) {
        if (!SeedSeasonsConfig.enableRainWater()) {
            return false;
        }
        RainState state = STATES.get(level.dimension());
        if (state == null) {
            return false;
        }
        long required = (long) (SeedSeasonsConfig.minRainHours() * 1000.0D);
        if (required > 0L && level.getGameTime() - state.startedAt < required) {
            return false;
        }
        return level.isRainingAt(pos);
    }

    private static RainState begin(ServerLevel level) {
        RainState state = new RainState();
        long now = level.getGameTime();
        state.startedAt = now;
        state.stableUntil = now + randomStableTicks(level);
        state.lastRoll = now;
        state.lastHandled = now;
        state.level = rollLevel(level);
        STATES.put(level.dimension(), state);
        announce(level, "seedseasons.rain.started", state.level);
        return state;
    }

    private static void handle(ServerLevel level, RainState state) {
        long now = level.getGameTime();
        if (now - state.lastHandled >= HANDLING_INTERVAL) {
            long elapsed = now - state.lastHandled;
            state.lastHandled = now;
            advancePuddles(level, state, elapsed);
            sampleHollows(level, state);
        }
        if (now >= state.stableUntil) {
            long interval = (long) (SeedSeasonsConfig.rainCheckHours() * 1000.0D);
            if (interval > 0L && now - state.lastRoll >= interval) {
                state.lastRoll = now;
                reRoll(level, state);
            }
        }
    }

    private static void reRoll(ServerLevel level, RainState state) {
        RandomSource random = level.getRandom();
        int roll = random.nextInt(4);
        int next = state.level;
        if (roll == 1) {
            next = Math.min(4, state.level + 1);
        } else if (roll == 2) {
            next = Math.max(0, state.level - 1);
        }
        if (next != state.level) {
            boolean heavier = next > state.level;
            state.level = next;
            state.stableUntil = level.getGameTime() + randomStableTicks(level);
            announce(level, heavier ? "seedseasons.rain.increased" : "seedseasons.rain.decreased", next);
        }
    }

    private static long randomStableTicks(ServerLevel level) {
        return (long) (level.getRandom().nextInt(5) + 1) * 1000L;
    }

    private static int rollLevel(ServerLevel level) {
        Season season = SeasonHelper.getSeasonState(level).getSeason();
        int[] weights = season != null ? SEASON_WEIGHTS[season.ordinal()] : new int[]{20, 20, 20, 20, 20};
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        if (total <= 0) {
            return 0;
        }
        int roll = level.getRandom().nextInt(total);
        for (int index = 0; index < weights.length; index++) {
            roll -= weights[index];
            if (roll < 0) {
                return index;
            }
        }
        return 0;
    }

    private static void advancePuddles(ServerLevel level, RainState state, long elapsed) {
        if (state.puddles.isEmpty()) {
            return;
        }
        double fillTicks = fillDays(state.level) * TICKS_PER_DAY;
        Iterator<Map.Entry<BlockPos, Double>> iterator = state.puddles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Double> entry = iterator.next();
            BlockPos pos = entry.getKey();
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            BlockState current = chunk.getBlockState(pos);
            if (!current.isAir() || !current.getFluidState().isEmpty() || !chunk.getBlockState(pos.below()).getFluidState().isEmpty()) {
                iterator.remove();
                continue;
            }
            double progress = entry.getValue() + elapsed / fillTicks;
            if (progress >= 1.0D) {
                iterator.remove();
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            } else {
                entry.setValue(progress);
            }
        }
    }

    private static void sampleHollows(ServerLevel level, RainState state) {
        Set<Long> loaded = LOADED_CHUNKS.get(level.dimension());
        if (loaded == null || loaded.isEmpty()) {
            return;
        }
        for (long packed : new ArrayList<>(loaded)) {
            if (state.puddles.size() >= CANDIDATE_LIMIT) {
                return;
            }
            ChunkPos chunkPos = new ChunkPos(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk != null) {
                sampleColumn(level, chunk, state);
            }
        }
    }

    private static void sampleColumn(ServerLevel level, LevelChunk chunk, RainState state) {
        RandomSource random = level.getRandom();
        int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
        int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        BlockPos pos = new BlockPos(x, surface, z);
        if (state.puddles.containsKey(pos) || state.puddles.size() >= CANDIDATE_LIMIT) {
            return;
        }
        BlockState at = chunk.getBlockState(pos);
        if (!at.isAir() || !at.getFluidState().isEmpty() || !chunk.getBlockState(pos.below()).getFluidState().isEmpty()) {
            return;
        }
        if (!level.isRainingAt(pos)) {
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int nx = pos.getX() + direction.getStepX();
            int nz = pos.getZ() + direction.getStepZ();
            LevelChunk neighbor = level.getChunkSource().getChunkNow(nx >> 4, nz >> 4);
            if (neighbor == null) {
                return;
            }
            if (neighbor.getHeight(Heightmap.Types.WORLD_SURFACE, nx & 15, nz & 15) <= surface) {
                return;
            }
        }
        state.puddles.put(pos, 0.0D);
    }

    private static double fillDays(int level) {
        return switch (level) {
            case 0 -> SeedSeasonsConfig.drizzleFillDays();
            case 1 -> SeedSeasonsConfig.lightRainFillDays();
            case 2 -> SeedSeasonsConfig.moderateRainFillDays();
            case 3 -> SeedSeasonsConfig.heavyRainFillDays();
            default -> SeedSeasonsConfig.torrentialFillDays();
        };
    }

    private static void announce(ServerLevel level, String key, int rainLevel) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(Component.translatable(key, Component.translatable("seedseasons.rain.level." + rainLevel)).withStyle(ChatFormatting.GRAY), false);
        }
    }
}
