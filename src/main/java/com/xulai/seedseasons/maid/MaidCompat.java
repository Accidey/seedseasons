package com.xulai.seedseasons.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.xulai.seedseasons.SeedSeasonsConfig;

import net.minecraft.resources.ResourceLocation;

final class MaidCompat {

    static final ResourceLocation FARM_TASK = ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "farm");

    private MaidCompat() {
    }

    static boolean enabled(EntityMaid maid) {
        return SeedSeasonsConfig.enableMaidCompat() && maid != null && maid.getTask() != null && FARM_TASK.equals(maid.getTask().getUid());
    }
}
