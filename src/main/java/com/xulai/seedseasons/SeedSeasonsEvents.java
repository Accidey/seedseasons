package com.xulai.seedseasons;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import sereneseasons.init.ModFertility;

@EventBusSubscriber(modid = SeedSeasons.MODID)
public final class SeedSeasonsEvents {

    private static final SuggestionProvider<CommandSourceStack> SEASON_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String head = lastSpace < 0 ? "" : remaining.substring(0, lastSpace + 1);
        String token = (lastSpace < 0 ? remaining : remaining.substring(lastSpace + 1)).toLowerCase(Locale.ROOT);
        String typed = head.toLowerCase(Locale.ROOT);
        for (String season : SeasonMapping.seasonNames()) {
            if (season.startsWith(token) && !typed.contains(season)) {
                builder.suggest(head + season);
            }
        }
        return builder.buildFuture();
    };

    private SeedSeasonsEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("seedseasons")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("seasons", StringArgumentType.greedyString())
                                .suggests(SEASON_SUGGESTIONS)
                                .executes(SeedSeasonsEvents::addHeldItem)))
                .then(Commands.literal("remove")
                        .executes(SeedSeasonsEvents::removeHeldItem))
                .then(Commands.literal("growth")
                        .then(Commands.argument("days", DoubleArgumentType.doubleArg(0.01D, 1000.0D))
                                .executes(SeedSeasonsEvents::setGrowthDays))
                        .then(Commands.literal("remove")
                                .executes(SeedSeasonsEvents::removeGrowthDays))));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SeasonMapping.operate();
        CropLifecycle.dailyTick(event.getServer());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            CropLifecycle.onChunkLoad(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            CropLifecycle.onChunkUnload(chunk);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getItemStack().is(Items.WATER_BUCKET)) {
            refillWater(level, event);
            return;
        }
        if (event.getItemStack().getItem() instanceof BoneMealItem && SeedSeasonsConfig.enableCropLifecycle()) {
            warnOutOfSeason(level, event);
        }
    }

    private static void refillWater(ServerLevel level, PlayerInteractEvent.RightClickBlock event) {
        if (!SeedSeasonsConfig.enableCropWater()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!WaterNetwork.refill(level, pos)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(event.getHand(), new ItemStack(Items.BUCKET));
        }
        WaterSource source = WaterNetwork.sourceAt(level, pos);
        if (source != null) {
            player.displayClientMessage(Component.translatable("seedseasons.message.water_refilled", GrowthDays.trim(source.level())), true);
        }
    }

    private static void warnOutOfSeason(ServerLevel level, PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!CropLifecycle.isManaged(state)) {
            return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!ModFertility.isCropFertile(blockId, level, pos)) {
            player.displayClientMessage(Component.translatable("seedseasons.message.out_of_season_fertilizer", state.getBlock().getName()), true);
        }
    }

    private static int addHeldItem(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack held = heldStack(source);
        if (held.isEmpty()) {
            return 0;
        }

        int mask = SeasonMapping.seasonMask(StringArgumentType.getString(context, "seasons"));
        if (mask == 0) {
            source.sendFailure(Component.translatable("commands.seedseasons.no_season"));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int merged = SeasonMapping.addEntry(itemId, mask);
        if (merged == 0) {
            source.sendFailure(Component.translatable("commands.seedseasons.not_saved"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.seedseasons.added", itemId, SeasonMapping.seasonWords(merged)), true);
        if (!(held.getItem() instanceof BlockItem)) {
            source.sendSuccess(() -> Component.translatable("commands.seedseasons.no_block", itemId), false);
        }
        return 1;
    }

    private static int removeHeldItem(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack held = heldStack(source);
        if (held.isEmpty()) {
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!SeasonMapping.removeEntry(itemId)) {
            source.sendFailure(Component.translatable("commands.seedseasons.not_listed", itemId));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.seedseasons.removed", itemId), true);
        return 1;
    }

    private static int setGrowthDays(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack held = heldStack(source);
        if (held.isEmpty()) {
            return 0;
        }

        double days = DoubleArgumentType.getDouble(context, "days");
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!GrowthDays.set(itemId, days)) {
            source.sendFailure(Component.translatable("commands.seedseasons.not_saved"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.seedseasons.growth_set", itemId, GrowthDays.trim(days)), true);
        return 1;
    }

    private static int removeGrowthDays(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack held = heldStack(source);
        if (held.isEmpty()) {
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!GrowthDays.remove(itemId)) {
            source.sendFailure(Component.translatable("commands.seedseasons.growth_not_listed", itemId));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.seedseasons.growth_removed", itemId), true);
        return 1;
    }

    private static ItemStack heldStack(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.seedseasons.player_only"));
            return ItemStack.EMPTY;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.translatable("commands.seedseasons.no_item"));
            return ItemStack.EMPTY;
        }
        return held;
    }
}
