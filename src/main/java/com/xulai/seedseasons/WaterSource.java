package com.xulai.seedseasons;

import java.util.Set;

import net.minecraft.core.BlockPos;

public final class WaterSource {

    private BlockPos poolAnchor;
    private long poolAnchorUntil;
    private Set<Long> served = Set.of();
    private long servedUntil;

    public BlockPos poolAnchor() {
        return poolAnchor;
    }

    public boolean poolAnchorValid(long gameTime) {
        return poolAnchor != null && gameTime < poolAnchorUntil;
    }

    public void setPoolAnchor(BlockPos poolAnchor, long until) {
        this.poolAnchor = poolAnchor;
        this.poolAnchorUntil = until;
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
}
