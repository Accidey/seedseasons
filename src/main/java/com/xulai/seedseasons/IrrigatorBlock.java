package com.xulai.seedseasons;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.xulai.seedseasons.client.SeedSeasonsClient;

public class IrrigatorBlock extends Block implements EntityBlock {

    /** 有红石信号时机器激活。 */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** true = 没水（红灯），false = 有水。由方块实体维护。 */
    public static final BooleanProperty DRY = BooleanProperty.create("dry");

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public IrrigatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(POWERED, Boolean.FALSE)
                .setValue(DRY, Boolean.TRUE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, DRY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))
                .setValue(DRY, Boolean.TRUE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * 红石信号变化：更新激活状态，决定黑/绿/红三种外观的开关。
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        boolean hasSignal = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != hasSignal) {
            level.setBlock(pos, state.setValue(POWERED, hasSignal), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IrrigatorBlockEntity(pos, state);
    }

    /**
     * 客户端 ticker：给被灌溉的耕地撒雨滴粒子（喷灌雨幕）。
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != SeedSeasonsBlockEntities.IRRIGATOR.get()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<IrrigatorBlockEntity>) IrrigatorBlockEntity::clientTick;
        return ticker;
    }

    /**
     * 空手或手持其他物品右键：打开水位条界面。
     * 手持水桶/空桶但存不进、取不出时也会由 useItemOn 落到本方法。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof IrrigatorBlockEntity) {
            if (level.isClientSide) {
                SeedSeasonsClient.openIrrigatorScreen(pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * 手持水桶：存入；手持空桶：取出；存不进/取不出时打开水位条界面。
     * 客户端用同步的水量做同样的判定来决定是否打开界面（纯客户端 Screen，无 Menu）。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof IrrigatorBlockEntity irrigator)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean isBucket = stack.getItem() == Items.WATER_BUCKET;
        boolean isEmptyBucket = stack.getItem() == Items.BUCKET;
        if (!isBucket && !isEmptyBucket) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int water = irrigator.getWater();
        boolean canStore = isBucket && water + IrrigatorBlockEntity.BUCKET_MILLIBUCKETS <= irrigator.getCapacity();
        boolean canDraw = isEmptyBucket && water >= IrrigatorBlockEntity.BUCKET_MILLIBUCKETS;
        if (level.isClientSide) {
            if (canStore || canDraw) {
                return ItemInteractionResult.SUCCESS;
            }
            SeedSeasonsClient.openIrrigatorScreen(pos);
            return ItemInteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        if (canStore) {
            return storeWater(serverLevel, pos, irrigator, player, hand);
        }
        if (canDraw) {
            return drawWater(serverLevel, pos, irrigator, player, hand);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult storeWater(ServerLevel level, BlockPos pos, IrrigatorBlockEntity irrigator, Player player, InteractionHand hand) {
        IFluidHandler tank = irrigator.getExposedTank();
        FluidStack water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, IrrigatorBlockEntity.BUCKET_MILLIBUCKETS);
        if (tank.fill(water, IFluidHandler.FluidAction.SIMULATE) < IrrigatorBlockEntity.BUCKET_MILLIBUCKETS) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        tank.fill(water, IFluidHandler.FluidAction.EXECUTE);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        return ItemInteractionResult.SUCCESS;
    }

    private static ItemInteractionResult drawWater(ServerLevel level, BlockPos pos, IrrigatorBlockEntity irrigator, Player player, InteractionHand hand) {
        if (irrigator.getWater() < IrrigatorBlockEntity.BUCKET_MILLIBUCKETS) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        irrigator.getExposedTank().drain(IrrigatorBlockEntity.BUCKET_MILLIBUCKETS, IFluidHandler.FluidAction.EXECUTE);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, new ItemStack(Items.WATER_BUCKET));
        }
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        return ItemInteractionResult.SUCCESS;
    }
}
