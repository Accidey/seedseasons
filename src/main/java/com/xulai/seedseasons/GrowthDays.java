package com.xulai.seedseasons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class GrowthDays {

    private static volatile Map<String, Double> byId = Map.of();
    private static volatile Map<String, Double> byBlock = Map.of();

    private GrowthDays() {
    }

    public static double of(String blockId) {
        Double days = byBlock.get(blockId);
        return days != null ? days : SeedSeasonsConfig.defaultGrowthDays();
    }

    public static double ofItem(Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        Double days = byId.get(itemId);
        if (days != null) {
            return days;
        }
        if (item instanceof BlockItem blockItem) {
            days = byBlock.get(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
            if (days != null) {
                return days;
            }
        }
        return SeedSeasonsConfig.defaultGrowthDays();
    }

    public static boolean set(String itemId, double days) {
        String value = trim(days);
        SeasonMapping.Filtered filtered = SeasonMapping.filter(SeedSeasonsConfig.growthDays(), itemId);
        List<String> updated = new ArrayList<>(filtered.entries());
        updated.add(itemId + " " + value);
        if (!SeedSeasonsConfig.saveGrowthDays(updated)) {
            return false;
        }
        requestReload();
        return true;
    }

    public static boolean remove(String itemId) {
        SeasonMapping.Filtered filtered = SeasonMapping.filter(SeedSeasonsConfig.growthDays(), itemId);
        if (!filtered.matched()) {
            return false;
        }
        if (!SeedSeasonsConfig.saveGrowthDays(filtered.entries())) {
            return false;
        }
        requestReload();
        return true;
    }

    static void parse() {
        Map<String, Double> parsed = new HashMap<>();
        for (String entry : SeedSeasonsConfig.growthDays()) {
            for (String chunk : entry.split(";")) {
                String trimmed = chunk.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] tokens = trimmed.split("\\s+");
                ResourceLocation id = ResourceLocation.tryParse(tokens[0]);
                if (id == null) {
                    SeedSeasons.LOGGER.warn("Ignoring growth days entry with an invalid id: {}", trimmed);
                    continue;
                }
                if (tokens.length < 2) {
                    SeedSeasons.LOGGER.warn("Ignoring growth days entry without a day count: {}", trimmed);
                    continue;
                }
                double days;
                try {
                    days = Double.parseDouble(tokens[1]);
                } catch (NumberFormatException exception) {
                    SeedSeasons.LOGGER.warn("Ignoring growth days entry with an invalid day count: {}", trimmed);
                    continue;
                }
                if (days <= 0.0D) {
                    SeedSeasons.LOGGER.warn("Ignoring growth days entry with a non-positive day count: {}", trimmed);
                    continue;
                }
                parsed.put(id.toString(), days);
            }
        }
        byId = Map.copyOf(parsed);
    }

    static void resolve() {
        Map<String, Double> resolved = new HashMap<>(byId);
        for (Map.Entry<String, Double> entry : byId.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                continue;
            }
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                if (item instanceof BlockItem blockItem) {
                    resolved.put(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString(), entry.getValue());
                }
            });
        }
        byBlock = Map.copyOf(resolved);
    }

    private static void requestReload() {
        SeasonMapping.requestReload();
    }

    public static String trim(double days) {
        String text = String.format(Locale.ROOT, "%.3f", days);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    public static String display(double days) {
        String text = trim(days);
        return text.indexOf('.') < 0 ? text + ".0" : text;
    }
}
