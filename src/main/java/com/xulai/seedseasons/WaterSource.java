package com.xulai.seedseasons;

import java.util.Set;

public final class WaterSource {

    private Set<Long> served = Set.of();
    private long servedUntil;

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
