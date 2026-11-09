package com.xulai.seedseasons;

import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SeedSeasonsBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, SeedSeasons.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IrrigatorBlockEntity>> IRRIGATOR =
            BLOCK_ENTITIES.register("irrigator",
                    () -> BlockEntityType.Builder.of(IrrigatorBlockEntity::new, SeedSeasonsBlocks.IRRIGATOR.get()).build(null));

    private SeedSeasonsBlockEntities() {
    }
}
