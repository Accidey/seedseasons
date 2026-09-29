package com.xulai.seedseasons;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import sereneseasons.api.season.Season;
import sereneseasons.init.ModFertility;

public final class SeasonMapping {

    private static final String[] SEASON_NAMES = {"spring", "summer", "autumn", "winter"};
    private static final Season[] SEASONS = {Season.SPRING, Season.SUMMER, Season.AUTUMN, Season.WINTER};
    private static final int[] SEASON_BITS = {1, 2, 4, 8};

    private static volatile Map<String, Integer> configured = Map.of();
    private static volatile Map<Season, Set<String>> plants = Map.of();
    private static volatile Set<String> seasonalIds = Set.of();
    private static volatile Map<String, Integer> tooltipSeasons = Map.of();
    private static volatile boolean syncPending;
    private static volatile int configVersion;

    private SeasonMapping() {
    }

    public static int configVersion() {
        return configVersion;
    }

    public static String[] seasonNames() {
        return SEASON_NAMES.clone();
    }

    public static int seasonMask(String raw) {
        int mask = 0;
        for (String token : raw.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            mask |= bitOf(token);
        }
        return mask;
    }

    public static String seasonWords(int mask) {
        List<String> words = new ArrayList<>();
        for (int i = 0; i < SEASON_NAMES.length; i++) {
            if ((mask & SEASON_BITS[i]) != 0) {
                words.add(SEASON_NAMES[i]);
            }
        }
        return String.join(" ", words);
    }

    public static boolean isSeasonalCrop(BlockState state) {
        return seasonalIds.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    public static void requestReload() {
        configured = parse();
        GrowthDays.parse();
        configVersion++;
        syncPending = true;
    }

    public static void operate() {
        if (syncPending) {
            syncPending = false;
            ModFertility.populate();
        }
    }

    public static void mergeInto(Set<String> springPlants, Set<String> summerPlants, Set<String> autumnPlants, Set<String> winterPlants, Set<String> allPlants, Map<String, Integer> seedSeasons) {
        syncPending = false;
        GrowthDays.resolve();

        Map<Season, Set<String>> resolvedPlants = new EnumMap<>(Season.class);
        for (Season season : SEASONS) {
            resolvedPlants.put(season, new HashSet<>());
        }
        Set<String> resolvedIds = new HashSet<>();
        Map<String, Integer> resolvedTooltips = new HashMap<>();

        for (Map.Entry<String, Integer> entry : configured.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                continue;
            }
            int mask = entry.getValue();

            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isPresent()) {
                add(resolvedPlants, resolvedIds, entry.getKey(), mask);
                resolvedTooltips.merge(entry.getKey(), mask, (first, second) -> first | second);
                if (item.get() instanceof BlockItem blockItem) {
                    add(resolvedPlants, resolvedIds, BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString(), mask);
                } else {
                    SeedSeasons.LOGGER.warn("Configured id {} is not a block item, only its tooltip will change", entry.getKey());
                }
                continue;
            }

            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
            if (block.isEmpty()) {
                SeedSeasons.LOGGER.warn("Configured id {} is neither an item nor a block, ignored", entry.getKey());
                continue;
            }
            add(resolvedPlants, resolvedIds, entry.getKey(), mask);
            Item blockItem = block.get().asItem();
            if (blockItem != Items.AIR) {
                resolvedTooltips.merge(BuiltInRegistries.ITEM.getKey(blockItem).toString(), mask, (first, second) -> first | second);
            }
        }

        plants = resolvedPlants;
        seasonalIds = Set.copyOf(resolvedIds);
        tooltipSeasons = Map.copyOf(resolvedTooltips);

        springPlants.addAll(plants.get(Season.SPRING));
        summerPlants.addAll(plants.get(Season.SUMMER));
        autumnPlants.addAll(plants.get(Season.AUTUMN));
        winterPlants.addAll(plants.get(Season.WINTER));
        allPlants.addAll(seasonalIds);
        tooltipSeasons.forEach((id, mask) -> seedSeasons.merge(id, mask, (first, second) -> first | second));
    }

    public static int addEntry(String itemId, int mask) {
        Filtered filtered = filter(SeedSeasonsConfig.seedSeasons(), itemId);
        List<String> updated = new ArrayList<>(filtered.entries());
        int merged = filtered.removedMask() | mask;
        updated.add(itemId + " " + seasonWords(merged));
        if (!SeedSeasonsConfig.saveSeasons(updated)) {
            return 0;
        }
        requestReload();
        return merged;
    }

    public static boolean removeEntry(String itemId) {
        Filtered filtered = filter(SeedSeasonsConfig.seedSeasons(), itemId);
        if (!filtered.matched()) {
            return false;
        }
        if (!SeedSeasonsConfig.saveSeasons(filtered.entries())) {
            return false;
        }
        requestReload();
        return true;
    }

    static Filtered filter(List<String> source, String itemId) {
        List<String> entries = new ArrayList<>();
        boolean matched = false;
        int removedMask = 0;
        for (String entry : source) {
            List<String> kept = new ArrayList<>();
            for (String chunk : entry.split(";")) {
                String trimmed = chunk.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                if (tokens[0].equals(itemId)) {
                    matched = true;
                    for (int i = 1; i < tokens.length; i++) {
                        removedMask |= bitOf(tokens[i].toLowerCase(Locale.ROOT));
                    }
                    continue;
                }
                kept.add(trimmed);
            }
            if (!kept.isEmpty()) {
                entries.add(String.join("; ", kept));
            }
        }
        return new Filtered(entries, matched, removedMask);
    }

    record Filtered(List<String> entries, boolean matched, int removedMask) {
    }

    private static void add(Map<Season, Set<String>> resolvedPlants, Set<String> resolvedIds, String id, int mask) {
        resolvedIds.add(id);
        for (int i = 0; i < SEASONS.length; i++) {
            if ((mask & SEASON_BITS[i]) != 0) {
                resolvedPlants.get(SEASONS[i]).add(id);
            }
        }
    }

    private static Map<String, Integer> parse() {
        Map<String, Integer> parsed = new HashMap<>();
        for (String entry : SeedSeasonsConfig.seedSeasons()) {
            for (String chunk : entry.split(";")) {
                String trimmed = chunk.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                ResourceLocation id = ResourceLocation.tryParse(tokens[0]);
                if (id == null) {
                    SeedSeasons.LOGGER.warn("Ignoring seed seasons entry with an invalid id: {}", trimmed);
                    continue;
                }
                int mask = 0;
                for (int i = 1; i < tokens.length; i++) {
                    int bit = bitOf(tokens[i].toLowerCase(Locale.ROOT));
                    if (bit == 0) {
                        SeedSeasons.LOGGER.warn("Ignoring unknown season '{}' in seed seasons entry: {}", tokens[i], trimmed);
                    }
                    mask |= bit;
                }
                if (mask == 0) {
                    SeedSeasons.LOGGER.warn("Ignoring seed seasons entry without a valid season: {}", trimmed);
                    continue;
                }
                parsed.merge(id.toString(), mask, (first, second) -> first | second);
            }
        }
        return Map.copyOf(parsed);
    }

    private static int bitOf(String token) {
        for (int i = 0; i < SEASON_NAMES.length; i++) {
            if (SEASON_NAMES[i].equals(token)) {
                return SEASON_BITS[i];
            }
        }
        return 0;
    }
}
