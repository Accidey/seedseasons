package com.xulai.seedseasons.mixin;

import java.util.HashMap;
import java.util.Set;

import com.xulai.seedseasons.SeasonMapping;

import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import sereneseasons.init.ModFertility;

@Mixin(ModFertility.class)
public abstract class ModFertilityMixin {

    @Shadow private static Set<String> springPlants;
    @Shadow private static Set<String> summerPlants;
    @Shadow private static Set<String> autumnPlants;
    @Shadow private static Set<String> winterPlants;
    @Shadow private static Set<String> allListedPlants;
    @Shadow private static HashMap<String, Integer> seedSeasons;

    @Inject(method = "isCrop", at = @At("RETURN"), cancellable = true)
    private static void seedseasons$isCrop(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && SeasonMapping.isSeasonalCrop(state)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "populate", at = @At("TAIL"))
    private static void seedseasons$populate(CallbackInfo ci) {
        SeasonMapping.mergeInto(springPlants, summerPlants, autumnPlants, winterPlants, allListedPlants, seedSeasons);
    }
}
