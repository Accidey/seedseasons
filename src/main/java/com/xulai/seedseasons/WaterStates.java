package com.xulai.seedseasons;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.chunk.LevelChunk;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class WaterStates implements INBTSerializable<CompoundTag> {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SeedSeasons.MODID);

    public static final Supplier<AttachmentType<WaterStates>> TYPE = ATTACHMENT_TYPES.register("water_states",
            () -> AttachmentType.serializable(WaterStates::new).build());

    private static final String POSITION = "pos";
    private static final String SOURCES = "sources";

    private final Map<BlockPos, WaterSource> sources = new HashMap<>();

    public static WaterStates of(LevelChunk chunk) {
        return chunk.getData(TYPE);
    }

    public WaterSource get(BlockPos pos) {
        return sources.get(pos);
    }

    public void put(BlockPos pos, WaterSource source) {
        sources.put(pos, source);
    }

    public void remove(BlockPos pos) {
        sources.remove(pos);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        sources.forEach((pos, source) -> {
            CompoundTag entry = source.save();
            entry.putLong(POSITION, pos.asLong());
            list.add(entry);
        });
        CompoundTag tag = new CompoundTag();
        tag.put(SOURCES, list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        sources.clear();
        ListTag list = tag.getList(SOURCES, CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            sources.put(BlockPos.of(entry.getLong(POSITION)), WaterSource.load(entry));
        }
    }
}
