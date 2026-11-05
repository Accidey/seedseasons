package com.xulai.seedseasons;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class RainWater {

    private static final Map<ResourceKey<Level>, Long> RAIN_STARTED_AT = new HashMap<>();

    private RainWater() {
    }

    public static void tick(MinecraftServer server) {
        if (!SeedSeasonsConfig.enableRainGrowth()) {
            RAIN_STARTED_AT.clear();
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (isRaining(level)) {
                RAIN_STARTED_AT.computeIfAbsent(level.dimension(), key -> level.getDayTime());
            } else {
                RAIN_STARTED_AT.remove(level.dimension());
            }
        }
    }

    public static boolean sufficient(ServerLevel level, BlockPos pos) {
        if (!SeedSeasonsConfig.enableRainGrowth()) {
            return false;
        }
        Long startedAt = RAIN_STARTED_AT.get(level.dimension());
        if (startedAt == null) {
            return false;
        }
        long required = (long) (SeedSeasonsConfig.minRainHours() * 1000.0D);
        if (required > 0L && level.getDayTime() - startedAt < required) {
            return false;
        }
        return level.isRainingAt(pos);
    }

    private static boolean isRaining(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && level.getLevelData().isRaining();
    }
}