package com.xulai.seedseasons.maid;

import java.util.List;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.crop.SpecialCropManager;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;

import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.level.block.Blocks;

@LittleMaidExtension
public final class SeedSeasonsMaidExtension implements ILittleMaid, IExtraMaidBrain {

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(this);
    }

    @Override
    public void registerSpecialCropHandler(SpecialCropManager register) {
        register.addCrop(Blocks.FARMLAND, new MaidSeasonalPlantHandler());
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> getWorkBehaviors() {
        MaidIrrigationMoveTask move = new MaidIrrigationMoveTask(0.6F);
        return Lists.newArrayList(
                Pair.<Integer, BehaviorControl<? super EntityMaid>>of(6, new MaidPlotWorkTask(move)),
                Pair.<Integer, BehaviorControl<? super EntityMaid>>of(7, move));
    }
}
