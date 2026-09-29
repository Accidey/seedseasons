package com.xulai.seedseasons.client;

import com.xulai.seedseasons.CropLifecycle;
import com.xulai.seedseasons.GrowthDays;
import com.xulai.seedseasons.SeedSeasons;
import com.xulai.seedseasons.SeedSeasonsConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = SeedSeasons.MODID, value = Dist.CLIENT)
public final class CropTooltips {

    private CropTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!SeedSeasonsConfig.enableCropLifecycle()) {
            return;
        }
        ItemStack stack = event.getItemStack();

        if (stack.getItem() instanceof BoneMealItem) {
            if (SeedSeasonsConfig.boneMealPerDay() <= 0) {
                event.getToolTip().add(Component.translatable("seedseasons.tooltip.bone_meal_disabled").withStyle(ChatFormatting.GRAY));
                return;
            }
            event.getToolTip().add(Component.translatable("seedseasons.tooltip.bone_meal",
                    SeedSeasonsConfig.boneMealPerDay(),
                    Math.round(SeedSeasonsConfig.boneMealProgress() * 100.0D)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable("seedseasons.tooltip.bone_meal_warning").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        if (!CropLifecycle.isManaged(blockItem.getBlock().defaultBlockState())) {
            return;
        }
        event.getToolTip().add(Component.translatable("seedseasons.tooltip.growth_days", GrowthDays.display(GrowthDays.ofItem(stack.getItem()))));
    }
}
