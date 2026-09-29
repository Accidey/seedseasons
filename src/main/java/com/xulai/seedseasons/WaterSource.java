package com.xulai.seedseasons;

import java.util.Set;

import net.minecraft.nbt.CompoundTag;

public final class WaterSource {

    private static final String LEVEL = "level";

    private double level;
    private Set<Long> served = Set.of();
    private long servedUntil;

    public WaterSource(double level) {
        this.level = level;
    }

    public double level() {
        return level;
    }

    public void setLevel(double level) {
        this.level = level;
    }

    public Set<Long> served() {
        return served;
    }

    public boolean cacheValid(long gameTime) {
        return gameTime < servedUntil;
    }

    public void setServed(Set<Long> served, long servedUntil) {
        this.served = served;
        this.servedUntil = servedUntil;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(LEVEL, level);
        return tag;
    }

    public static WaterSource load(CompoundTag tag) {
        return new WaterSource(tag.getDouble(LEVEL));
    }
}
