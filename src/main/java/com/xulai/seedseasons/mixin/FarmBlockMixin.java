package com.xulai.seedseasons.mixin;

import com.xulai.seedseasons.SeedSeasonsConfig;
import com.xulai.seedseasons.WaterNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {

    @Inject(method = "isNearWater", at = @At("HEAD"), cancellable = true)
    private static void seedseasons$isNearWater(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Boolean hydration = WaterNetwork.hydration(level, pos);
        if (hydration != null) {
            cir.setReturnValue(hydration);
        }
    }

    @Redirect(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRainingAt(Lnet/minecraft/core/BlockPos;)Z"), require = 0)
    private boolean seedseasons$isRainingAt(ServerLevel level, BlockPos pos) {
        return !SeedSeasonsConfig.enableCropWater() && level.isRainingAt(pos);
    }
}
