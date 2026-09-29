package com.xulai.seedseasons;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.chunk.LevelChunk;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CropStates implements INBTSerializable<CompoundTag> {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SeedSeasons.MODID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CropStates> CODEC = StreamCodec.of(
            (buffer, states) -> {
                buffer.writeVarInt(states.sources().size());
                states.sources().forEach((pos, state) -> {
                    buffer.writeBlockPos(pos);
                    buffer.writeDouble(state.progress());
                    buffer.writeLong(state.lastUpdate());
                    buffer.writeLong(state.totalTicks());
                });
            },
            buffer -> {
                CropStates states = new CropStates();
                int size = buffer.readVarInt();
                for (int index = 0; index < size; index++) {
                    BlockPos pos = buffer.readBlockPos();
                    states.put(pos, new CropState(buffer.readDouble(), buffer.readLong(), buffer.readLong(), -1L, -1L, 0, 0));
                }
                return states;
            });

    public static final java.util.function.Supplier<AttachmentType<CropStates>> TYPE = ATTACHMENT_TYPES.register("crop_states",
            () -> AttachmentType.serializable(CropStates::new).sync(CODEC).build());

    private static final String POSITION = "pos";
    private static final String STATES = "states";

    private final Map<BlockPos, CropState> states = new HashMap<>();

    public static CropStates of(LevelChunk chunk) {
        return chunk.getData(TYPE);
    }

    public static void sync(LevelChunk chunk) {
        chunk.syncData(TYPE.get());
    }

    Map<BlockPos, CropState> sources() {
        return states;
    }

    public CropState get(BlockPos pos) {
        return states.get(pos);
    }

    public void put(BlockPos pos, CropState state) {
        states.put(pos, state);
    }

    public void remove(BlockPos pos) {
        states.remove(pos);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        states.forEach((pos, state) -> {
            CompoundTag entry = state.save();
            entry.putLong(POSITION, pos.asLong());
            list.add(entry);
        });
        CompoundTag tag = new CompoundTag();
        tag.put(STATES, list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        states.clear();
        ListTag list = tag.getList(STATES, CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            states.put(BlockPos.of(entry.getLong(POSITION)), CropState.load(entry));
        }
    }
}
