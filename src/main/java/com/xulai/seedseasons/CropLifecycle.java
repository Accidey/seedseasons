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
import net.minecraft.world.level.chunk.LevelChunkSection;

import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModFertility;

public final class CropLifecycle {

    private static final double TICKS_PER_DAY = 24000.0D;
    private static final double MIN_GROWTH_DAYS = 0.05D;
    private static final double NEAR_MATURE_PROGRESS = 0.75D;
    private static final double MATURE_SNAP_PROGRESS = 0.98D;

    private static final Map<ResourceKey<Level>, Long> LAST_DAYS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new HashMap<>();

    private static final Set<BlockPos> DRAINED = new HashSet<>();

    private CropLifecycle() {
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

    public static void onPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (!SeedSeasonsConfig.enableCropLifecycle()) {
            return;
        }
        IntegerProperty age = ageProperty(state);
        if (age == null || !isManaged(state)) {
            return;
        }
        recordAt(level, level.getChunkAt(pos), pos, state, age, maxAge(age), level.getRandom(), true);
    }

    public static void onBroken(ServerLevel level, BlockPos pos) {
        if (!SeedSeasonsConfig.enableCropLifecycle()) {
            return;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        CropStates states = CropStates.of(chunk);
        if (states.get(pos) == null) {
            return;
        }
        states.remove(pos);
        chunk.setUnsaved(true);
        CropStates.sync(chunk);
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
        recordAt(level, chunk, pos, state, age, maxAge(age), random, true);
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
        Set<Long> loaded = LOADED_CHUNKS.get(level.dimension());
        if (loaded == null || loaded.isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (long packed : new ArrayList<>(loaded)) {
            ChunkPos chunkPos = new ChunkPos(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                continue;
            }
            CropStates states = CropStates.of(chunk);
            discover(level, chunk, states, random);
            List<BlockPos> stale = new ArrayList<>();
            List<BlockPos> removed = new ArrayList<>();
            int before = states.sources().size();
            for (Map.Entry<BlockPos, CropState> entry : states.sources().entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = chunk.getBlockState(pos);
                IntegerProperty age = ageProperty(state);
                if (age == null || !isManaged(state)) {
                    stale.add(pos);
                    continue;
                }
                step(level, pos, state, age, entry.getValue(), random, removed);
            }
            for (BlockPos pos : stale) {
                states.remove(pos);
            }
            for (BlockPos pos : removed) {
                states.remove(pos);
            }
            if (before > 0) {
                chunk.setUnsaved(true);
                CropStates.sync(chunk);
            }
        }
        for (BlockPos pos : DRAINED) {
            WaterNetwork.drain(level, pos);
        }
        DRAINED.clear();
    }

    private static void discover(ServerLevel level, LevelChunk chunk, CropStates states, RandomSource random) {
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(CropLifecycle::isManaged)) {
                continue;
            }
            int minY = level.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!isManaged(state)) {
                            continue;
                        }
                        IntegerProperty age = ageProperty(state);
                        if (age == null) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(minX + x, minY + y, minZ + z);
                        recordAt(level, chunk, pos, state, age, maxAge(age), random, false);
                    }
                }
            }
        }
    }

    private static boolean step(ServerLevel level, BlockPos pos, BlockState state, IntegerProperty age, CropState record, RandomSource random, List<BlockPos> removed) {
        long now = level.getDayTime();
        int max = maxAge(age);
        int current = state.getValue(age);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        BlockPos farmlandPos = pos.below();
        if (SeedSeasonsConfig.enableCropWater() && level.getBlockState(farmlandPos).getBlock() instanceof FarmBlock) {
            BlockPos sourcePos = WaterNetwork.servingSource(level, farmlandPos);
            if (sourcePos == null) {
                if (RainSystem.rainGrowth(level, pos)) {
                    record.setDrySince(now);
                } else {
                    if (record.drySince() <= 0L) {
                        record.setDrySince(now);
                    } else if (SeedSeasonsConfig.dryWitherDays() > 0.0D && now - record.drySince() >= SeedSeasonsConfig.dryWitherDays() * TICKS_PER_DAY) {
                        removed.add(pos);
                        level.destroyBlock(pos, false);
                        level.setBlock(farmlandPos, Blocks.DIRT.defaultBlockState(), 3);
                    }
                    return true;
                }
            } else {
                if (!WaterNetwork.isInfinite(level, sourcePos) && !RainSystem.freeWatering(level, pos)) {
                    DRAINED.add(sourcePos);
                }
                record.setDrySince(now);
            }
        }

        int pending = record.pendingBoneMeal();
        if (pending > 0) {
            record.setPendingBoneMeal(0);
            double boosted = Math.min(1.0D, record.progress() + pending * SeedSeasonsConfig.boneMealProgress());
            if (boosted >= MATURE_SNAP_PROGRESS) {
                boosted = 1.0D;
            }
            int target = Math.min(max, Math.max((int) (boosted * max), current + pending));
            boosted = Math.max(boosted, (double) target / max);
            record.setProgress(boosted);
            record.setLastUpdate(now);
            if (target > current) {
                level.setBlock(pos, state.setValue(age, target), 3);
                record.setLastAge(target);
            }
            if (boosted >= 1.0D && record.matureSince() <= 0L) {
                record.setMatureSince(now);
            }
            current = target;
        }

        if (current >= max) {
            long overMature = (long) (SeedSeasonsConfig.overMatureDays() * TICKS_PER_DAY);
            if (record.matureSince() > 0L && overMature > 0L && now - record.matureSince() >= overMature) {
                removed.add(pos);
                level.destroyBlock(pos, false);
                return true;
            }
            return false;
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
        if (!fertile) {
            return true;
        }

        long minimum = (long) (SeedSeasonsConfig.minGrowHours() * 1000.0D);
        if (minimum > 0L && record.plantedAt() > 0L && now - record.plantedAt() < minimum) {
            return true;
        }

        double grown = Math.min(1.0D, record.progress() + 1.0D / Math.max(1.0D, record.totalTicks() / TICKS_PER_DAY));
        if (grown >= MATURE_SNAP_PROGRESS) {
            grown = 1.0D;
        }
        record.setProgress(grown);
        record.setLastUpdate(now);
        int target = Math.min(max, (int) (grown * max));
        if (target > current) {
            level.setBlock(pos, state.setValue(age, target), 3);
            record.setLastAge(target);
        }
        if (grown >= 1.0D && record.matureSince() <= 0L) {
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
        CropState record = recordAt(serverLevel, chunk, pos, state, age, max, serverLevel.getRandom(), true);

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
        record.setPendingBoneMeal(record.pendingBoneMeal() + 1);
        record.setLastUpdate(now);
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

    public static int pendingBoneMealAt(Level level, BlockPos pos) {
        if (!(level.getChunkAt(pos) instanceof LevelChunk chunk)) {
            return -1;
        }
        CropState state = CropStates.of(chunk).get(pos);
        return state == null ? -1 : state.pendingBoneMeal();
    }

    private static CropState recordAt(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state, IntegerProperty age, int max, RandomSource random, boolean sync) {
        long now = level.getDayTime();
        int current = state.getValue(age);
        int version = SeasonMapping.configVersion();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        CropStates states = CropStates.of(chunk);
        CropState record = states.get(pos);
        boolean replaced = record != null && (current != record.lastAge() || (!record.blockId().isEmpty() && !record.blockId().equals(blockId)));
        if (record == null) {
            record = new CropState((double) current / max, now, rollTotalTicks(blockId, random), current >= max ? now : -1L, -1L, 0, seasonOrdinal(level), 0);
            record.setLastAge(current);
            record.setPlantedAt(now);
            record.setBlockId(blockId);
            record.setConfigVersion(version);
            states.put(pos, record);
            chunk.setUnsaved(true);
            if (sync) {
                CropStates.sync(chunk);
            }
        } else if (replaced) {
            record.reset((double) current / max, now, rollTotalTicks(blockId, random), current, current >= max ? now : -1L);
            record.setBlockId(blockId);
            record.setConfigVersion(version);
            if (sync) {
                CropStates.sync(chunk);
            }
        } else if (record.configVersion() != version) {
            record.retime(rollTotalTicks(blockId, random), version);
            if (sync) {
                CropStates.sync(chunk);
            }
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
