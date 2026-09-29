package com.xulai.seedseasons;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class WaterStates {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SeedSeasons.MODID);

    public static final Supplier<AttachmentType<WaterStates>> TYPE = ATTACHMENT_TYPES.register("water_states",
            () -> AttachmentType.builder(WaterStates::new).build());

    private final Map<BlockPos, WaterSource> sources = new HashMap<>();

    public static WaterStates of(LevelChunk chunk) {
        return chunk.getData(TYPE);
    }

    public WaterSource get(BlockPos pos) {
        return sources.get(pos);
    }

    public WaterSource getOrCreate(BlockPos pos) {
        return sources.computeIfAbsent(pos, key -> new WaterSource());
    }

    public void remove(BlockPos pos) {
        sources.remove(pos);
    }
}
