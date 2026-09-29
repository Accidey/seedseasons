package com.xulai.seedseasons.mixin;

import com.xulai.seedseasons.CropLifecycle;
import com.xulai.seedseasons.SeedSeasonsConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import snownee.jade.addon.vanilla.CropProgressProvider;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

@Mixin(CropProgressProvider.class)
public abstract class JadeCropProgressMixin {

    @Inject(method = "appendTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void seedseasons$realProgress(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config, CallbackInfo ci) {
        if (!SeedSeasonsConfig.enableCropLifecycle() || !CropLifecycle.isManaged(accessor.getBlockState())) {
            return;
        }
        double progress = CropLifecycle.progressAt(accessor.getLevel(), accessor.getPosition());
        if (progress < 0.0D) {
            return;
        }
        MutableComponent component;
        if (progress < 1.0D) {
            component = IThemeHelper.get().info(String.format("%.0f%%", progress * 100.0D));
        } else {
            component = IThemeHelper.get().success(Component.translatable("tooltip.jade.crop_mature"));
        }
        tooltip.add(Component.translatable("tooltip.jade.crop_growth", component));
        if (SeedSeasonsConfig.boneMealPerDay() > 0) {
            int pending = CropLifecycle.pendingBoneMealAt(accessor.getLevel(), accessor.getPosition());
            if (pending >= 0) {
                tooltip.add(Component.translatable(pending > 0 ? "seedseasons.jade.fertilized" : "seedseasons.jade.not_fertilized")
                        .withStyle(pending > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        ci.cancel();
    }
}
