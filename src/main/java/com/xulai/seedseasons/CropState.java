package com.xulai.seedseasons;

import net.minecraft.nbt.CompoundTag;

public final class CropState {

    private static final String PROGRESS = "progress";
    private static final String LAST_UPDATE = "update";
    private static final String TOTAL_TICKS = "total";
    private static final String MATURE_SINCE = "mature";
    private static final String BONE_MEAL_DAY = "meal_day";
    private static final String BONE_MEAL_USES = "meal_uses";
    private static final String PENDING_BONE_MEAL = "pending";
    private static final String LAST_SEASON = "season";
    private static final String LAST_AGE = "last_age";
    private static final String DRY_SINCE = "dry_since";
    private static final String PLANTED_AT = "planted";
    private static final String BLOCK_ID = "block";
    private static final String CONFIG_VERSION = "config_version";

    private double progress;
    private long lastUpdate;
    private long totalTicks;
    private long matureSince;
    private long boneMealDay;
    private int boneMealUses;
    private int pendingBoneMeal;
    private int lastSeason;
    private int lastAge;
    private long drySince;
    private long plantedAt;
    private String blockId = "";
    private int configVersion;

    public CropState(double progress, long lastUpdate, long totalTicks, long matureSince, long boneMealDay, int boneMealUses, int lastSeason, int pendingBoneMeal) {
        this.progress = progress;
        this.lastUpdate = lastUpdate;
        this.totalTicks = totalTicks;
        this.matureSince = matureSince;
        this.boneMealDay = boneMealDay;
        this.boneMealUses = boneMealUses;
        this.lastSeason = lastSeason;
        this.pendingBoneMeal = pendingBoneMeal;
    }

    public double progress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public long lastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public long totalTicks() {
        return totalTicks;
    }

    public void setTotalTicks(long totalTicks) {
        this.totalTicks = totalTicks;
    }

    public long matureSince() {
        return matureSince;
    }

    public void setMatureSince(long matureSince) {
        this.matureSince = matureSince;
    }

    public long boneMealDay() {
        return boneMealDay;
    }

    public void setBoneMealDay(long boneMealDay) {
        this.boneMealDay = boneMealDay;
    }

    public int boneMealUses() {
        return boneMealUses;
    }

    public void setBoneMealUses(int boneMealUses) {
        this.boneMealUses = boneMealUses;
    }

    public int pendingBoneMeal() {
        return pendingBoneMeal;
    }

    public void setPendingBoneMeal(int pendingBoneMeal) {
        this.pendingBoneMeal = pendingBoneMeal;
    }

    public int lastSeason() {
        return lastSeason;
    }

    public void setLastSeason(int lastSeason) {
        this.lastSeason = lastSeason;
    }

    public int lastAge() {
        return lastAge;
    }

    public void setLastAge(int lastAge) {
        this.lastAge = lastAge;
    }

    public long drySince() {
        return drySince;
    }

    public void setDrySince(long drySince) {
        this.drySince = drySince;
    }

    public long plantedAt() {
        return plantedAt;
    }

    public void setPlantedAt(long plantedAt) {
        this.plantedAt = plantedAt;
    }

    public String blockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public int configVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }

    public void retime(long totalTicks, int configVersion) {
        this.totalTicks = totalTicks;
        this.configVersion = configVersion;
    }

    public void reset(double progress, long lastUpdate, long totalTicks, int age, long matureSince) {
        this.progress = progress;
        this.lastUpdate = lastUpdate;
        this.totalTicks = totalTicks;
        this.lastAge = age;
        this.matureSince = matureSince;
        this.drySince = -1L;
        this.plantedAt = lastUpdate;
        this.pendingBoneMeal = 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(PROGRESS, progress);
        tag.putLong(LAST_UPDATE, lastUpdate);
        tag.putLong(TOTAL_TICKS, totalTicks);
        tag.putLong(MATURE_SINCE, matureSince);
        tag.putLong(BONE_MEAL_DAY, boneMealDay);
        tag.putInt(BONE_MEAL_USES, boneMealUses);
        tag.putInt(PENDING_BONE_MEAL, pendingBoneMeal);
        tag.putInt(LAST_SEASON, lastSeason);
        tag.putInt(LAST_AGE, lastAge);
        tag.putLong(DRY_SINCE, drySince);
        tag.putLong(PLANTED_AT, plantedAt);
        tag.putString(BLOCK_ID, blockId);
        tag.putInt(CONFIG_VERSION, configVersion);
        return tag;
    }

    public static CropState load(CompoundTag tag) {
        CropState state = new CropState(
                tag.getDouble(PROGRESS),
                tag.getLong(LAST_UPDATE),
                tag.getLong(TOTAL_TICKS),
                tag.getLong(MATURE_SINCE),
                tag.getLong(BONE_MEAL_DAY),
                tag.getInt(BONE_MEAL_USES),
                tag.getInt(LAST_SEASON),
                tag.getInt(PENDING_BONE_MEAL));
        state.setLastAge(tag.getInt(LAST_AGE));
        state.setPendingBoneMeal(tag.getInt(PENDING_BONE_MEAL));
        state.setDrySince(tag.getLong(DRY_SINCE));
        state.setPlantedAt(tag.getLong(PLANTED_AT));
        state.setBlockId(tag.getString(BLOCK_ID));
        state.setConfigVersion(tag.getInt(CONFIG_VERSION));
        return state;
    }
}
