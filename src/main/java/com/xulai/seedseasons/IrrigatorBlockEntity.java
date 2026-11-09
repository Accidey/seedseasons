package com.xulai.seedseasons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class IrrigatorBlockEntity extends BlockEntity {

    /** 一桶水的毫巴数。 */
    public static final int BUCKET_MILLIBUCKETS = 1000;
    /** 贴图/UI 里的总容量（桶）。 */
    public static final int CAPACITY_BUCKETS = 100;
    /** 覆盖半径：耕地相对灌溉机的水平距离。 */
    public static final int RADIUS = 8;
    /** 覆盖的垂直半径。 */
    public static final int RADIUS_Y = 1;
    /** 服务耕地缓存的时长（tick）。 */
    private static final long CACHE_TICKS = 100L;

    private final IrrigatorTank tank = new IrrigatorTank(CAPACITY_BUCKETS * BUCKET_MILLIBUCKETS);

    private long servedCacheUntil;
    private final Set<Long> servedFarmlands = new HashSet<>();
    private final List<BlockPos> servedFarmlandList = new ArrayList<>();
    private double waterCarry;

    public IrrigatorBlockEntity(BlockPos pos, BlockState state) {
        super(SeedSeasonsBlockEntities.IRRIGATOR.get(), pos, state);
    }

    /**
     * 顶部暴露给管道模组的流体处理器：只收水，收满拒收，存量可被抽走。
     */
    public IFluidHandler getExposedTank() {
        return tank;
    }

    /**
     * 是否正在供水：启用、有红石信号且存有水。
     */
    public boolean isWatering() {
        if (!SeedSeasonsConfig.enableIrrigator() || tank.isEmpty()) {
            return false;
        }
        BlockState state = getBlockState();
        return state.getBlock() instanceof IrrigatorBlock && state.getValue(IrrigatorBlock.POWERED);
    }

    /**
     * 耕地是否被这台灌溉机服务：覆盖半径内、按距离取最近的若干块（可配置）。
     * 结果缓存 100 tick，和原版水塘的缓存节奏一致。服务端权威判定，客户端亦可复用（数据均已同步）。
     */
    public boolean servesFarmland(Level level, BlockPos farmlandPos) {
        if (!SeedSeasonsConfig.enableIrrigator()) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (gameTime >= servedCacheUntil) {
            rebuildServedCache(level, gameTime);
        }
        return servedFarmlands.contains(farmlandPos.asLong());
    }

    private void rebuildServedCache(Level level, long gameTime) {
        servedFarmlands.clear();
        servedFarmlandList.clear();
        servedCacheUntil = gameTime + CACHE_TICKS;
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                worldPosition.offset(-RADIUS, -RADIUS_Y, -RADIUS),
                worldPosition.offset(RADIUS, RADIUS_Y + 1, RADIUS))) {
            if (pos.equals(worldPosition) || !WaterNetwork.loaded(level, pos)) {
                continue;
            }
            if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.FarmBlock) {
                found.add(pos.immutable());
            }
        }
        int limit = SeedSeasonsConfig.irrigatorFarmlands();
        if (found.size() > limit) {
            found.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(worldPosition))
                    .thenComparingLong(BlockPos::asLong));
            found.subList(limit, found.size()).clear();
        }
        for (BlockPos pos : found) {
            servedFarmlands.add(pos.asLong());
            servedFarmlandList.add(pos);
        }
    }

    /**
     * 每日结算扣水：每 MC 天消耗 1 桶 ÷ 配置天数，小数部分滚入下一天。
     */
    public void consumeDailyWater() {
        if (tank.isEmpty() || SeedSeasonsConfig.irrigatorDaysPerBucket() <= 0.0D) {
            return;
        }
        waterCarry += BUCKET_MILLIBUCKETS / SeedSeasonsConfig.irrigatorDaysPerBucket();
        int take = (int) waterCarry;
        if (take > 0) {
            waterCarry -= take;
            tank.drain(take, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** 粒子刷出间隔（tick）：每 100 tick（5 秒）出现一瞬雨幕。 */
    private static final int PARTICLE_INTERVAL_TICKS = 100;
    /** 单次雨幕刷出的雨滴数量。 */
    private static final int DROPS_PER_BURST = 24;

    /** 客户端粒子计时器（无需持久化）。 */
    private int particleTimer;

    /**
     * 客户端每 tick：正在供水的灌溉机每 5 秒给自己圈内的耕地撒一批原版雨滴粒子，模拟喷灌雨幕。
     * 判定全部用已同步的客户端数据（方块状态 + 水箱），不发任何网络包。
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, IrrigatorBlockEntity be) {
        if (!be.isWatering()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime >= be.servedCacheUntil) {
            be.rebuildServedCache(level, gameTime);
        }
        if (be.servedFarmlandList.isEmpty()) {
            return;
        }
        if (++be.particleTimer < PARTICLE_INTERVAL_TICKS) {
            return;
        }
        be.particleTimer = 0;
        RandomSource random = level.random;
        for (int i = 0; i < DROPS_PER_BURST; i++) {
            BlockPos farm = be.servedFarmlandList.get(random.nextInt(be.servedFarmlandList.size()));
            double x = farm.getX() + random.nextDouble();
            double y = farm.getY() + 1.05D + random.nextDouble() * 0.45D;
            double z = farm.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.RAIN, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    private void onTankChanged() {
        setChanged();
        Level level = this.level;
        if (level == null || level.isClientSide) {
            return;
        }
        updateWaterState();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * 把"缺水"同步进方块状态，驱动亮绿/亮红/黑三种外观。
     */
    private void updateWaterState() {
        Level level = this.level;
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof IrrigatorBlock && state.getValue(IrrigatorBlock.DRY) != tank.isEmpty()) {
            level.setBlock(worldPosition, state.setValue(IrrigatorBlock.DRY, tank.isEmpty()), Block.UPDATE_CLIENTS);
        }
    }

    public int getWater() {
        return tank.getFluidAmount();
    }

    public int getCapacity() {
        return tank.getCapacity();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("waterCarry", waterCarry);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("tank")) {
            tank.readFromNBT(registries, tag.getCompound("tank"));
        }
        waterCarry = tag.contains("waterCarry") ? tag.getDouble("waterCarry") : 0.0D;
        updateWaterState();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private class IrrigatorTank extends FluidTank {

        private IrrigatorTank(int capacity) {
            super(capacity, stack -> stack.getFluid() == Fluids.WATER);
        }

        @Override
        protected void onContentsChanged() {
            IrrigatorBlockEntity.this.onTankChanged();
        }
    }
}
