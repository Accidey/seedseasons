package com.xulai.seedseasons.mixin;

import com.xulai.seedseasons.CauldronRain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractCauldronBlock.class)
public abstract class AbstractCauldronBlockMixin {

    @Inject(method = "onPlace", at = @At("TAIL"))
    private void seedseasons$armRainFill(BlockState state, Level level, BlockPos pos, BlockState previous, boolean movedByPiston, CallbackInfo ci) {
        CauldronRain.arm(level, pos, state);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void seedseasons$rainFill(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CauldronRain.advance(level, pos, state);
    }
}
