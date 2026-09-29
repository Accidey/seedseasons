package com.xulai.seedseasons.mixin;

import com.xulai.seedseasons.CropLifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void seedseasons$randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!CropLifecycle.onRandomTick(level, pos, state, random)) {
            state.randomTick(level, pos, random);
        }
    }
}
