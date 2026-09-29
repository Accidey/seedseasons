package com.xulai.seedseasons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;

import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModFertility;

public final class CropLifecycle {

    private static final double TICKS_PER_DAY = 24000.0D;
    private static final double MIN_GROWTH_DAYS = 0.05D;
    private static final double NEAR_MATURE_PROGRESS = 0.75D;

    private static final Map<ResourceKey<Level>, Long> LAST_DAYS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> CROP_CHUNKS = new HashMap<>();

    private CropLifecycle() {
    }

    public static void onChunkLoad(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel)) {
            return;
        }
        if (!CropStates.of(chunk).sources().isEmpty()) {
            track(chunk);
        }
    }

    public static void onChunkUnload(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel)) {
            return;
        }
        Set<Long> chunks = CROP_CHUNKS.get(chunk.getLevel().dimension());
        if (chunks != null) {
            chunks.remove(chunk.getPos().toLong());
        }
    }

    private static void track(LevelChunk chunk) {
        CROP_CHUNKS.computeIfAbsent(chunk.getLevel().dimension(), key -> new HashSet<>()).add(chunk.getPos().toLong());
    }

    public static boolean isManaged(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof NetherWartBlock
                || block instanceof CocoaBlock
                || block instanceof SweetBerryBushBlock;
    }

    public static boolean onRandomTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!isManaged(state) || !SeedSeasonsConfig.enableCropLifecycle()) {
            return false;
        }
        IntegerProperty age = ageProperty(state);
        if (age == null) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        recordAt(level, chunk, pos, state, age, maxAge(age), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), random);
        return true;
    }

    public static void dailyTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            long day = level.getDayTime() / 24000L;
            Long last = LAST_DAYS.get(level.dimension());
            LAST_DAYS.put(level.dimension(), day);
            if (last == null || last == day || !SeedSeasonsConfig.enableCropLifecycle()) {
                continue;
            }
            sweep(level);
        }
    }

    private static void sweep(ServerLevel level) {
        Set<Long> tracked = CROP_CHUNKS.get(level.dimension());
        if (tracked == null || tracked.isEmpty()) {
            return;
        }
        SeedSeasons.LOGGER.info("seedseasons daily crop settlement at day {}: {} tracked chunk(s) in {}", level.getDayTime() / 24000L, tracked.size(), level.dimension().location());
        RandomSource random = level.getRandom();
        for (long packed : new ArrayList<>(tracked)) {
            ChunkPos chunkPos = new ChunkPos(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                continue;
            }
            CropStates states = CropStates.of(chunk);
            if (states.sources().isEmpty()) {
                continue;
            }
            List<BlockPos> stale = new ArrayList<>();
            List<BlockPos> removed = new ArrayList<>();
            boolean changed = false;
            for (Map.Entry<BlockPos, CropState> entry : states.sources().entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = chunk.getBlockState(pos);
                IntegerProperty age = ageProperty(state);
                if (age == null || !isManaged(state)) {
                    stale.add(pos);
                    continue;
                }
                if (step(level, pos, state, age, entry.getValue(), random, removed)) {
                    changed = true;
                }
            }
            for (BlockPos pos : stale) {
                states.remove(pos);
            }
            for (BlockPos pos : removed) {
                states.remove(pos);
            }
            if (changed || !stale.isEmpty() || !removed.isEmpty()) {
                chunk.setUnsaved(true);
                CropStates.sync(chunk);
            }
        }
    }

    private static boolean step(ServerLevel level, BlockPos pos, BlockState state, IntegerProperty age, CropState record, RandomSource random, List<BlockPos> removed) {
        long now = level.getDayTime();
        int max = maxAge(age);
        int current = state.getValue(age);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        if (current >= max) {
            long overMature = (long) (SeedSeasonsConfig.overMatureDays() * TICKS_PER_DAY);
            if (record.matureSince() > 0L && overMature > 0L && now - record.matureSince() > overMature) {
                removed.add(pos);
                level.destroyBlock(pos, false);
                return true;
            }
            return false;
        }

        BlockPos farmlandPos = pos.below();
        if (SeedSeasonsConfig.enableCropWater() && level.getBlockState(farmlandPos).getBlock() instanceof FarmBlock) {
            BlockPos sourcePos = WaterNetwork.servingSource(level, farmlandPos);
            WaterSource source = sourcePos == null ? null : WaterNetwork.sourceAt(level, sourcePos);
            if (source == null || source.level() <= 0.0D) {
                if (record.drySince() <= 0L) {
                    record.setDrySince(now);
                } else if (SeedSeasonsConfig.dryWitherDays() > 0.0D && now - record.drySince() > SeedSeasonsConfig.dryWitherDays() * TICKS_PER_DAY) {
                    removed.add(pos);
                    level.destroyBlock(pos, false);
                    level.setBlock(farmlandPos, Blocks.DIRT.defaultBlockState(), 3);
                }
                return true;
            }
            if (!WaterNetwork.isInfinite(level, sourcePos)) {
                WaterNetwork.consume(level, sourcePos, 1.0D);
            }
            record.setDrySince(-1L);
        }

        boolean fertile = ModFertility.isCropFertile(blockId, level, pos);
        int season = seasonOrdinal(level);

        if (record.lastSeason() != season) {
            record.setLastSeason(season);
            if (!fertile && record.progress() >= NEAR_MATURE_PROGRESS && random.nextFloat() < SeedSeasonsConfig.nearMatureChance()) {
                level.setBlock(pos, state.setValue(age, max), 3);
                record.setProgress(1.0D);
                record.setMatureSince(now);
                record.setLastAge(max);
                return true;
            }
        }

        if (!fertile && random.nextFloat() < SeedSeasonsConfig.witherChance()) {
            removed.add(pos);
            level.destroyBlock(pos, false);
            return true;
        }

        record.setProgress(Math.min(1.0D, record.progress() + 1.0D / Math.max(1.0D, record.totalTicks() / TICKS_PER_DAY)));
        record.setLastUpdate(now);
        int target = Math.min(max, (int) (record.progress() * max));
        if (target > current) {
            level.setBlock(pos, state.setValue(age, target), 3);
            record.setLastAge(target);
        }
        if (record.progress() >= 1.0D && record.matureSince() <= 0L) {
            record.setMatureSince(now);
        }
        return true;
    }

    public static boolean onBoneMeal(Level level, BlockPos pos, ItemStack stack, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !SeedSeasonsConfig.enableCropLifecycle()) {
            return false;
        }
        BlockState state = serverLevel.getBlockState(pos);
        IntegerProperty age = ageProperty(state);
        if (age == null || !isManaged(state)) {
            return false;
        }
        int max = maxAge(age);
        int current = state.getValue(age);
        if (current >= max) {
            return false;
        }
        if (SeedSeasonsConfig.boneMealPerDay() <= 0) {
            return true;
        }

        long now = serverLevel.getDayTime();
        LevelChunk chunk = serverLevel.getChunkAt(pos);
        CropStates states = CropStates.of(chunk);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        CropState record = recordAt(serverLevel, chunk, pos, state, age, max, blockId, serverLevel.getRandom());

        long day = now / 24000L;
        if (record.boneMealDay() != day) {
            record.setBoneMealDay(day);
            record.setBoneMealUses(0);
        }

        if (record.boneMealUses() >= SeedSeasonsConfig.boneMealPerDay()) {
            states.remove(pos);
            serverLevel.destroyBlock(pos, false);
            if (player != null) {
                player.displayClientMessage(Component.translatable("seedseasons.message.over_fertilized", state.getBlock().getName().withStyle(ChatFormatting.GRAY)), true);
            }
            stack.shrink(1);
            chunk.setUnsaved(true);
            CropStates.sync(chunk);
            return true;
        }

        record.setBoneMealUses(record.boneMealUses() + 1);
        double progress = Math.min(1.0D, record.progress() + SeedSeasonsConfig.boneMealProgress());
        int target = Math.min(max, (int) (progress * max));
        if (target <= current) {
            target = Math.min(max, current + 1);
            progress = Math.max(progress, (double) target / max);
        }
        record.setProgress(progress);
        record.setLastUpdate(now);

        if (target > current) {
            serverLevel.setBlock(pos, state.setValue(age, target), 3);
            record.setLastAge(target);
        }
        if (progress >= 1.0D && record.matureSince() <= 0L) {
            record.setMatureSince(now);
        }
        chunk.setUnsaved(true);
        CropStates.sync(chunk);
        stack.shrink(1);
        return true;
    }

    public static double progressAt(Level level, BlockPos pos) {
        if (!(level.getChunkAt(pos) instanceof LevelChunk chunk)) {
            return -1.0D;
        }
        CropState state = CropStates.of(chunk).get(pos);
        return state == null ? -1.0D : state.progress();
    }

    private static CropState recordAt(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state, IntegerProperty age, int max, String blockId, RandomSource random) {
        long now = level.getDayTime();
        int current = state.getValue(age);
        int version = SeasonMapping.configVersion();
        CropStates states = CropStates.of(chunk);
        CropState record = states.get(pos);
        if (record == null) {
            record = new CropState((double) current / max, now, rollTotalTicks(blockId, random), current >= max ? now : -1L, -1L, 0, seasonOrdinal(level));
            record.setLastAge(current);
            record.setConfigVersion(version);
            states.put(pos, record);
            chunk.setUnsaved(true);
            CropStates.sync(chunk);
            track(chunk);
        } else if (current != record.lastAge()) {
            record.reset((double) current / max, now, rollTotalTicks(blockId, random), current, current >= max ? now : -1L);
            record.setConfigVersion(version);
            CropStates.sync(chunk);
        } else if (record.configVersion() != version) {
            record.retime(rollTotalTicks(blockId, random), version);
            CropStates.sync(chunk);
        }
        return record;
    }

    private static long rollTotalTicks(String blockId, RandomSource random) {
        double days = GrowthDays.of(blockId);
        int variance = SeedSeasonsConfig.growthDayVariance();
        if (variance > 0) {
            days += random.nextInt(variance * 2 + 1) - variance;
        }
        return Math.max(1L, (long) (Math.max(MIN_GROWTH_DAYS, days) * TICKS_PER_DAY));
    }

    private static IntegerProperty ageProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && "age".equals(property.getName())) {
                return integer;
            }
        }
        return null;
    }

    private static int maxAge(IntegerProperty age) {
        int max = 0;
        for (int value : age.getPossibleValues()) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static int seasonOrdinal(ServerLevel level) {
        Season season = SeasonHelper.getSeasonState(level).getSeason();
        return season == null ? -1 : season.ordinal();
    }
}
