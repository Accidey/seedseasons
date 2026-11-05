package com.xulai.seedseasons.mixin;

import com.xulai.seedseasons.SeedSeasonsConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

@Mixin(ServerLevel.class)
public abstract class SeasonalDayNightMixin {

    private static final long DAY_TICKS = 24000L;
    private static final long DAY_PHASE_TICKS = 12000L;

    @ModifyArg(
            method = "setDayTimePerTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/ServerLevelData;setDayTimePerTick(F)V"),
            index = 0)
    private float seedseasons$seasonalDayNightSpeed(float speed) {
        if (!SeedSeasonsConfig.enableSeasonalDayNight()) {
            return speed;
        }
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != Level.OVERWORLD) {
            return speed;
        }
        Season season = SeasonHelper.getSeasonState(level).getSeason();
        double dayPercent = dayPercentFor(season);
        double minutes = SeedSeasonsConfig.mcDayLengthMinutes();
        long timeOfDay = level.getDayTime() % DAY_TICKS;
        boolean day = timeOfDay < DAY_PHASE_TICKS;
        double ratio = day ? dayPercent : (1.0D - dayPercent);
        double seasonalSpeed = 10.0D / (minutes * ratio);
        return (float) seasonalSpeed;
    }

    private static double dayPercentFor(Season season) {
        if (season == null) {
            return 0.5D;
        }
        return switch (season) {
            case SPRING -> SeedSeasonsConfig.springDayPercent() / 100.0D;
            case SUMMER -> SeedSeasonsConfig.summerDayPercent() / 100.0D;
            case AUTUMN -> SeedSeasonsConfig.autumnDayPercent() / 100.0D;
            case WINTER -> SeedSeasonsConfig.winterDayPercent() / 100.0D;
        };
    }
}