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
    private static final long WAKE_GAME_TICKS = 300L;

    private static final Map<ResourceKey<Level>, Map<Long, Pot>> POTS = new HashMap<>();

    private static final class Pot {
        private long readyTicks;
        private long lastSeen;

        private Pot(long readyTicks, long lastSeen) {
            this.readyTicks = readyTicks;
            this.lastSeen = lastSeen;
        }
    }

    private CauldronRain() {
    }

    public static void arm(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !SeedSeasonsConfig.enableCauldronRainFill()) {
            return;
        }
        if (!(state.getBlock() instanceof AbstractCauldronBlock)) {
            return;
        }
        pots(serverLevel).putIfAbsent(pos.asLong(), new Pot(stepGameTicks(serverLevel), serverLevel.getDayTime()));
        schedule(serverLevel, pos, state.getBlock());
    }

    public static void advance(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AbstractCauldronBlock)) {
            return;
        }
        if (!SeedSeasonsConfig.enableCauldronRainFill()) {
            POTS.remove(level.dimension());
            return;
        }
        Map<Long, Pot> pots = pots(level);
        long now = level.getDayTime();
        Pot pot = pots.get(pos.asLong());
        if (pot == null) {
            pots.put(pos.asLong(), new Pot(stepGameTicks(level), now));
        } else {
            accumulateRain(pot, now, level.isRainingAt(pos.above()));
            drainCredit(level, pos, pot);
        }
        schedule(level, pos, level.getBlockState(pos).getBlock());
    }

    private static void drainCredit(ServerLevel level, BlockPos pos, Pot pot) {
        if (pot.readyTicks > 0L) {
            return;
        }
        pot.readyTicks = stepGameTicks(level);
        fillOneStep(level, pos, level.getBlockState(pos));
    }

    private static void accumulateRain(Pot pot, long now, boolean raining) {
        long elapsed = Math.max(0L, now - pot.lastSeen);
        pot.lastSeen = now;
        if (raining) {
            pot.readyTicks -= elapsed;
        }
    }

    private static void schedule(ServerLevel level, BlockPos pos, Block block) {
        if (!(block instanceof AbstractCauldronBlock) || level.getBlockTicks().hasScheduledTick(pos, block)) {
            return;
        }
        Pot pot = pots(level).get(pos.asLong());
        long wait = pot == null ? stepGameTicks(level) : Math.min(Math.max(pot.readyTicks, 1L), WAKE_GAME_TICKS);
        level.scheduleTick(pos, block, toRealTicks(level, wait));
    }

    private static boolean fillOneStep(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CauldronBlock) {
            replace(level, pos, Blocks.WATER_CAULDRON.defaultBlockState());
            return true;
        }
        if (!state.is(Blocks.WATER_CAULDRON)) {
            return false;
        }
        int fill = state.getValue(LayeredCauldronBlock.LEVEL);
        if (fill >= LayeredCauldronBlock.MAX_FILL_LEVEL) {
            return false;
        }
        replace(level, pos, state.cycle(LayeredCauldronBlock.LEVEL));
        return true;
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
        float perTick = level.getDayTimePerTick();
        double speed = perTick > 0.0F ? perTick : 1.0D;
        return (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE - 1L, Math.round(gameTicks / speed)));
    }

    private static Map<Long, Pot> pots(ServerLevel level) {
        return POTS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
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
