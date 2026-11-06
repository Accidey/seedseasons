package com.xulai.seedseasons;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.gameevent.GameEvent;

public final class CauldronRain {

    private static final int FILL_LEVELS = 3;
    private static final double GAME_TICKS_PER_HOUR = 1000.0D;
    private static final double SPREAD_MIN = 0.67D;
    private static final double SPREAD_MAX = 1.33D;
    private static final long DRY_POLL_GAME_TICKS = 1000L;

    private static final Map<ResourceKey<Level>, Map<Long, Long>> READY_AT = new HashMap<>();

    private CauldronRain() {
    }

    public static void arm(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !SeedSeasonsConfig.enableCauldronRainFill()) {
            return;
        }
        if (!(state.getBlock() instanceof AbstractCauldronBlock)) {
            return;
        }
        readyAt(serverLevel, pos).putIfAbsent(pos.asLong(), serverLevel.getDayTime() + stepGameTicks(serverLevel));
        schedule(serverLevel, pos, state.getBlock());
    }

    public static void advance(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AbstractCauldronBlock)) {
            return;
        }
        if (!SeedSeasonsConfig.enableCauldronRainFill()) {
            READY_AT.remove(level.dimension());
            return;
        }
        Map<Long, Long> ready = readyAt(level, pos);
        long now = level.getDayTime();
        Long stamp = ready.get(pos.asLong());
        if (stamp == null) {
            ready.put(pos.asLong(), now + stepGameTicks(level));
            schedule(level, pos, state.getBlock());
            return;
        }
        if (now < stamp) {
            schedule(level, pos, state.getBlock());
            return;
        }
        if (!level.isRainingAt(pos.above())) {
            schedule(level, pos, state.getBlock());
            return;
        }
        long step = stepGameTicks(level);
        ready.put(pos.asLong(), now + step);
        fillOneStep(level, pos, state);
        BlockState nowState = level.getBlockState(pos);
        if (nowState.getBlock() instanceof AbstractCauldronBlock) {
            schedule(level, pos, nowState.getBlock());
        }
    }

    private static void schedule(ServerLevel level, BlockPos pos, Block block) {
        if (level.getBlockTicks().hasScheduledTick(pos, block)) {
            return;
        }
        Long stamp = READY_AT.get(level.dimension()).get(pos.asLong());
        long wait = stamp == null ? stepGameTicks(level) : Math.max(0L, stamp - level.getDayTime());
        if (stamp != null && wait == 0L && !level.isRainingAt(pos.above())) {
            wait = DRY_POLL_GAME_TICKS;
        }
        level.scheduleTick(pos, block, toRealTicks(level, wait));
    }

    private static void fillOneStep(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CauldronBlock) {
            replace(level, pos, Blocks.WATER_CAULDRON.defaultBlockState());
            return;
        }
        if (!state.is(Blocks.WATER_CAULDRON)) {
            return;
        }
        int fill = state.getValue(LayeredCauldronBlock.LEVEL);
        if (fill >= LayeredCauldronBlock.MAX_FILL_LEVEL) {
            return;
        }
        replace(level, pos, state.cycle(LayeredCauldronBlock.LEVEL));
    }

    private static void replace(ServerLevel level, BlockPos pos, BlockState next) {
        level.setBlockAndUpdate(pos, next);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(next));
    }

    private static long stepGameTicks(ServerLevel level) {
        double hours = SeedSeasonsConfig.cauldronFillHours() / FILL_LEVELS;
        double spread = SPREAD_MIN + level.getRandom().nextDouble() * (SPREAD_MAX - SPREAD_MIN);
        return Math.max(1L, Math.round(hours * GAME_TICKS_PER_HOUR * spread));
    }

    private static int toRealTicks(ServerLevel level, long gameTicks) {
        if (gameTicks <= 0L) {
            return 1;
        }
        float perTick = level.getDayTimePerTick();
        double speed = perTick > 0.0F ? perTick : 1.0D;
        return (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE - 1L, Math.round(gameTicks / speed)));
    }

    private static Map<Long, Long> readyAt(ServerLevel level, BlockPos pos) {
        return READY_AT.computeIfAbsent(level.dimension(), key -> new HashMap<>());
    }

    public static void onChunkLoad(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level) || !SeedSeasonsConfig.enableCauldronRainFill()) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(CauldronRain::isCauldron)) {
                continue;
            }
            int minY = level.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isCauldron(state)) {
                            arm(level, new BlockPos(minX + x, minY + y, minZ + z), state);
                        }
                    }
                }
            }
        }
    }

    private static boolean isCauldron(BlockState state) {
        return state.getBlock() instanceof AbstractCauldronBlock;
    }
}
