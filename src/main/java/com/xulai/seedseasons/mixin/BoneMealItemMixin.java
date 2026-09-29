package com.xulai.seedseasons.mixin;

import com.xulai.seedseasons.CropLifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoneMealItem.class)
public abstract class BoneMealItemMixin {

    @Inject(method = "applyBonemeal", at = @At("HEAD"), cancellable = true)
    private static void seedseasons$applyBonemeal(ItemStack stack, Level level, BlockPos pos, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (CropLifecycle.onBoneMeal(level, pos, stack, player)) {
            cir.setReturnValue(true);
        }
    }
}
