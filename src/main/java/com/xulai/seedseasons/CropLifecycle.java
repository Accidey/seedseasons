package com.xulai.seedseasons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    private static final Map<ResourceKey<Level>, Long> LAST_DAYS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new HashMap<>();

    private static final Set<BlockPos> DRAINED = new HashSet<>();

    private static final class Settlement {
        int crops;
        int grown;
        int young;
        int mature;
        int offSeason;
        int dry;
        int withered;
        int fresh;
        int stale;
        double total;
    }

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
        SeedSeasons.LOGGER.info("seedseasons settlement at day {}: {} loaded chunk(s) in {}", level.getDayTime() / 24000L, loaded.size(), level.dimension().location());
        RandomSource random = level.getRandom();
        Settlement total = new Settlement();
        for (long packed : new ArrayList<>(loaded)) {
            ChunkPos chunkPos = new ChunkPos(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                continue;
            }
            CropStates states = CropStates.of(chunk);
            Settlement settlement = new Settlement();
            discover(level, chunk, states, random, settlement);
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
                step(level, pos, state, age, entry.getValue(), random, removed, settlement);
            }
            for (BlockPos pos : stale) {
                states.remove(pos);
            }
            for (BlockPos pos : removed) {
                states.remove(pos);
            }
            settlement.stale = stale.size();
            settlement.crops = states.sources().size();
            for (CropState state : states.sources().values()) {
                settlement.total += state.progress();
            }
            merge(total, settlement);
            if (before > 0) {
                SeedSeasons.LOGGER.info("  chunk [{}, {}]: {} crop(s), {} grew, {} young, {} mature, {} off-season, {} dry, {} withered, {} new, {} stale, total progress {}", chunkPos.x, chunkPos.z, settlement.crops, settlement.grown, settlement.young, settlement.mature, settlement.offSeason, settlement.dry, settlement.withered, settlement.fresh, settlement.stale, String.format(Locale.ROOT, "%.3f", settlement.total));
                chunk.setUnsaved(true);
                CropStates.sync(chunk);
            }
        }
        if (total.crops > 0 || total.withered > 0) {
            SeedSeasons.LOGGER.info("  settlement total: {} crop(s), {} grew, {} young, {} mature, {} off-season, {} dry, {} withered, {} new, {} stale, total progress {}", total.crops, total.grown, total.young, total.mature, total.offSeason, total.dry, total.withered, total.fresh, total.stale, String.format(Locale.ROOT, "%.3f", total.total));
        }
        for (BlockPos pos : DRAINED) {
            WaterNetwork.drain(level, pos);
        }
        if (!DRAINED.isEmpty()) {
            SeedSeasons.LOGGER.info("  {} water block(s) consumed and removed", DRAINED.size());
        }
        DRAINED.clear();
    }

    private static void merge(Settlement total, Settlement part) {
        total.crops += part.crops;
        total.grown += part.grown;
        total.young += part.young;
        total.mature += part.mature;
        total.offSeason += part.offSeason;
        total.dry += part.dry;
        total.withered += part.withered;
        total.fresh += part.fresh;
        total.stale += part.stale;
        total.total += part.total;
    }

    private static void discover(ServerLevel level, LevelChunk chunk, CropStates states, RandomSource random, Settlement settlement) {
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
                        if (states.get(pos) == null) {
                            settlement.fresh++;
                        }
                        recordAt(level, chunk, pos, state, age, maxAge(age), random, false);
                    }
                }
            }
        }
    }

    private static boolean step(ServerLevel level, BlockPos pos, BlockState state, IntegerProperty age, CropState record, RandomSource random, List<BlockPos> removed, Settlement settlement) {
        long now = level.getDayTime();
        int max = maxAge(age);
        int current = state.getValue(age);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

        if (current >= max) {
            long overMature = (long) (SeedSeasonsConfig.overMatureDays() * TICKS_PER_DAY);
            if (record.matureSince() > 0L && overMature > 0L && now - record.matureSince() > overMature) {
                settlement.withered++;
                removed.add(pos);
                level.destroyBlock(pos, false);
                return true;
            }
            settlement.mature++;
            return false;
        }

        BlockPos farmlandPos = pos.below();
        if (SeedSeasonsConfig.enableCropWater() && level.getBlockState(farmlandPos).getBlock() instanceof FarmBlock) {
            BlockPos sourcePos = WaterNetwork.servingSource(level, farmlandPos);
            if (sourcePos == null) {
                settlement.dry++;
                if (record.drySince() <= 0L) {
                    record.setDrySince(now);
                } else if (SeedSeasonsConfig.dryWitherDays() > 0.0D && now - record.drySince() >= SeedSeasonsConfig.dryWitherDays() * TICKS_PER_DAY) {
                    settlement.withered++;
                    removed.add(pos);
                    level.destroyBlock(pos, false);
                    level.setBlock(farmlandPos, Blocks.DIRT.defaultBlockState(), 3);
                }
                return true;
            }
            if (!WaterNetwork.isInfinite(level, sourcePos)) {
                DRAINED.add(sourcePos);
            }
            record.setDrySince(now);
        }

        boolean fertile = ModFertility.isCropFertile(blockId, level, pos);
        int season = seasonOrdinal(level);

        if (record.lastSeason() != season) {
            record.setLastSeason(season);
            if (!fertile && record.progress() >= NEAR_MATURE_PROGRESS && random.nextFloat() < SeedSeasonsConfig.nearMatureChance()) {
                settlement.grown++;
                level.setBlock(pos, state.setValue(age, max), 3);
                record.setProgress(1.0D);
                record.setMatureSince(now);
                record.setLastAge(max);
                return true;
            }
        }

        if (!fertile && random.nextFloat() < SeedSeasonsConfig.witherChance()) {
            settlement.withered++;
            removed.add(pos);
            level.destroyBlock(pos, false);
            return true;
        }
        if (!fertile) {
            settlement.offSeason++;
            return true;
        }

        long minimum = (long) (SeedSeasonsConfig.minGrowHours() * 1000.0D);
        if (minimum > 0L && record.plantedAt() > 0L && now - record.plantedAt() < minimum) {
            settlement.young++;
            return true;
        }

        settlement.grown++;
        double grown = Math.min(1.0D, record.progress() + 1.0D / Math.max(1.0D, record.totalTicks() / TICKS_PER_DAY));
        if (grown >= 1.0D - 1.0E-9D) {
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

    private static CropState recordAt(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state, IntegerProperty age, int max, RandomSource random, boolean sync) {
        long now = level.getDayTime();
        int current = state.getValue(age);
        int version = SeasonMapping.configVersion();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        CropStates states = CropStates.of(chunk);
        CropState record = states.get(pos);
        boolean replaced = record != null && (current != record.lastAge() || (!record.blockId().isEmpty() && !record.blockId().equals(blockId)));
        if (record == null) {
            record = new CropState((double) current / max, now, rollTotalTicks(blockId, random), current >= max ? now : -1L, -1L, 0, seasonOrdinal(level));
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
