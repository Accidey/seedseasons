package com.xulai.seedseasons;

import java.util.List;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = SeedSeasons.MODID)
public final class SeedSeasonsConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> SEED_SEASONS;
    private static final ModConfigSpec.BooleanValue ENABLE_CROP_LIFECYCLE;
    private static final ModConfigSpec.DoubleValue DEFAULT_GROWTH_DAYS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> GROWTH_DAYS;
    private static final ModConfigSpec.DoubleValue WITHER_CHANCE;
    private static final ModConfigSpec.DoubleValue NEAR_MATURE_CHANCE;
    private static final ModConfigSpec.DoubleValue OVER_MATURE_DAYS;
    private static final ModConfigSpec.IntValue GROWTH_DAY_VARIANCE;
    private static final ModConfigSpec.DoubleValue MIN_GROW_HOURS;
    private static final ModConfigSpec.IntValue BONE_MEAL_PER_DAY;
    private static final ModConfigSpec.DoubleValue BONE_MEAL_PROGRESS;
    private static final ModConfigSpec.BooleanValue ENABLE_CROP_WATER;
    private static final ModConfigSpec.IntValue WATER_PER_SOURCE;
    private static final ModConfigSpec.BooleanValue CONSUME_NATURAL_WATER;
    private static final ModConfigSpec.BooleanValue CONSUME_INFINITE_WATER;
    private static final ModConfigSpec.DoubleValue DRY_WITHER_DAYS;

    private static volatile ModConfig modConfig;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SEED_SEASONS = builder
                .comment(
                        " 每行一条：<物品或方块 id> <季节...>，季节之间用空格分隔，可选 spring、summer、autumn、winter。",
                        " 例：\"minecraft:potato spring summer autumn\"、\"minecraft:wheat_seeds spring summer autumn winter\"。",
                        " 这里的季节会和数据包标签合并取并集：静谧四季内置的作物、以及其他模组为它适配的数据包都能照常生效，这里只做补充。",
                        " 所以数据包里已经标好的作物，删掉这里的条目也不会失去季节限制；想让它们不受限制得改数据包。",
                        " 默认留空：数据包照常生效，这里只写你想额外补充或延长季节的作物；列表为空时即不额外施加任何限制。",
                        " 物品按它种下去的方块生效，所以写种子物品或作物方块都可以。",
                        " 同一个 id 写多条时季节会叠加，想减少季节就把该 id 的季节写在一行里。",
                        " 写错的条目会被单独忽略并在日志里提示，不影响其它条目和作物。",
                        " 手持物品执行 /seedseasons add <季节...> 写入（对同一作物重复执行会累加季节）、/seedseasons remove 移除整条。",
                        " 保存文件或应用配置界面即生效，不需要重启游戏。",
                        "",
                        " One entry per line: <item or block id> <seasons...>, seasons separated by spaces.",
                        " Valid seasons: spring, summer, autumn, winter.",
                        " Example: \"minecraft:potato spring summer autumn\", \"minecraft:wheat_seeds spring summer autumn winter\".",
                        " These seasons are merged as a union with the datapack tags: crops shipped by Serene Seasons and by other mods that adapt to it keep working, and this file only adds to them.",
                        " A crop already tagged by a datapack keeps its seasons even when its entry is deleted here, so unrestricting it takes a datapack change.",
                        " Empty by default: the datapack stays in charge, so only list the crops whose seasons you want to add to or extend.",
                        " Items work through the block they plant, so the seed item and the crop block behave the same.",
                        " Entries for the same id stack, so list every wanted season of an id in one entry.",
                        " Entries that cannot be parsed are ignored individually with a log warning, without affecting the other entries or crops.",
                        " Holding an item, run /seedseasons add <seasons...> to write it here (repeating it for one crop adds up its seasons), or /seedseasons remove to delete the whole entry.",
                        " Saving the file or applying the config screen takes effect live, no restart needed.")
                .defineListAllowEmpty("seedSeasons", List.of(), () -> "", value -> value instanceof String);

        ENABLE_CROP_LIFECYCLE = builder
                .comment(
                        "",
                        " 是否启用作物生命周期：以 MC 天为单位的成长、非当季枯死、成熟过久枯死。",
                        " 关闭后所有作物恢复原版随机刻生长，四季的季节限制仍然生效。",
                        "",
                        " Whether the crop lifecycle is enabled: day-based growth, out-of-season withering and over-maturity withering.",
                        " When disabled, crops grow by vanilla random ticks again while the season restrictions of Serene Seasons still apply.")
                .define("enableCropLifecycle", true);

        DEFAULT_GROWTH_DAYS = builder
                .comment(
                        "",
                        " 没有在 growthDays 里单独列出的作物（比如其他模组的作物），成熟所需的天数。",
                        " 1 天 = 一个完整昼夜循环，所以日夜长短的设置会直接改变现实耗时。",
                        " 默认 24 天 = 一个季节一熟，和下面默认值的推算口径一致。",
                        " 例：0.5 表示半天成熟；2 表示两天成熟。",
                        "",
                        " Maturation time in MC days for crops that are not listed in growthDays, such as crops from other mods.",
                        " One day is a full day-night cycle, so the configured day and night length applies directly.",
                        " The default of 24 days equals one harvest per season, matching how the defaults below are derived.",
                        " Example: 0.5 means half a day, 2 means two days.")
                .defineInRange("defaultGrowthDays", 24.0D, 0.01D, 1000.0D);

        GROWTH_DAYS = builder
                .comment(
                        "",
                        " 每行一条：<物品或方块 id> <天数>，天数可以是小数。",
                        " 例：\"minecraft:wheat 2\"、\"minecraft:carrot 0.5\"；写种子物品或作物方块都一样。",
                        " 默认值按「一个季节一熟、只能种一季的作物两熟」推算：一个季节 = 3 个子季节 × 子季节时长（四季默认 8 天）= 24 天，",
                        " 所以能跨 2 个及以上季节生长的作物给 24 天（每季收一次），只在 1 个季节生长的作物给 12 天（当季收两次）。",
                        " 如果改了四季的子季节时长，这里要按同样比例调整。",
                        " 同一个 id 写多条时以最后一条为准；写错的条目会被单独忽略并在日志里提示。",
                        " 手持物品执行 /seedseasons growth <天数> 写入、/seedseasons growth remove 移除。",
                        " 只对作物类方块生效：作物、茎、下界疣、可可豆、甜浆果丛及其子类；竹子、藤蔓等保持原版。",
                        "",
                        " One entry per line: <item or block id> <days>, the day count may be fractional.",
                        " Example: \"minecraft:wheat 2\", \"minecraft:carrot 0.5\"; the seed item and the crop block behave the same.",
                        " The defaults follow one harvest per season, and two for crops limited to a single season: a season is 3 sub-seasons times the sub-season length (8 days by default) = 24 days,",
                        " so crops that grow in two or more seasons get 24 days and crops limited to one season get 12 days.",
                        " If you change the Serene Seasons sub-season length, rescale these values the same way.",
                        " The last entry of an id wins; broken entries are ignored individually with a log warning.",
                        " Holding an item, run /seedseasons growth <days> to write it here, or /seedseasons growth remove to delete it.",
                        " Only crop blocks are affected: crops, stems, nether wart, cocoa, sweet berry bush and their subclasses; bamboo and vines stay vanilla.")
                .defineListAllowEmpty("growthDays", List.of(
                        "minecraft:wheat_seeds 24",
                        "minecraft:carrot 24",
                        "minecraft:nether_wart 24",
                        "minecraft:sweet_berries 24",
                        "minecraft:potato 12",
                        "minecraft:beetroot_seeds 12",
                        "minecraft:melon_seeds 12",
                        "minecraft:pumpkin_seeds 12",
                        "minecraft:torchflower_seeds 12",
                        "minecraft:pitcher_pod 12",
                        "minecraft:cocoa_beans 12"
                ), () -> "", value -> value instanceof String);

        WITHER_CHANCE = builder
                .comment(
                        "",
                        " 非当季作物到达生长节点时枯死（直接消失）的概率，每次生长机会判定一次。",
                        " 0 表示不会枯死、只是停止生长；1 表示必定枯死。",
                        "",
                        " Chance for an out-of-season crop to wither and vanish when its growth is due, rolled once per growth attempt.",
                        " 0 never withers (the crop just stops growing), 1 always withers.")
                .defineInRange("witherChance", 1.0D, 0.0D, 1.0D);

        NEAR_MATURE_CHANCE = builder
                .comment(
                        "",
                        " 季节变化后，已经长到 3/4 以上（快成熟）却已不合时令的作物，被直接催熟的概率，避免换季把快收成的作物清掉。",
                        " 和枯死一样，这个判定只在作物下一次「触发生长」时进行，不是换季瞬间立即结算。",
                        "",
                        " Chance for a crop that is at least three quarters grown but now out of season to be ripened when the season changes, instead of being lost.",
                        " Like withering, this is only rolled on the crop's next growth attempt, not instantly when the season turns.")
                .defineInRange("nearMatureChance", 0.8D, 0.0D, 1.0D);

        OVER_MATURE_DAYS = builder
                .comment(
                        "",
                        " 成熟后满这么多天仍未收获就枯死（直接消失），0 表示不因过熟枯死。",
                        " 判定只在每天 6 点结算时做一次，所以「第 N 天 6 点成熟」的作物会在第 N+1 天 6 点（正好 24 小时后）枯死；浇水只解除缺水计时，不会救回过熟。",
                        "",
                        " Days a crop may stay mature without being harvested before it withers and vanishes, 0 disables it.",
                        " The check runs once at each 6 AM settlement, so a crop maturing at day N 6 AM withers at day N+1 6 AM, exactly 24 hours later; watering clears the thirst timer only and does not save it from over-maturity.")
                .defineInRange("overMatureDays", 1.0D, 0.0D, 1000.0D);

        GROWTH_DAY_VARIANCE = builder
                .comment(
                        "",
                        " 成熟天数的随机浮动：每株作物种下时会在「配置天数 ± 该值」之间取一个随机天数，所以同一批作物不会同一天一起成熟。",
                        " 例：默认 1 时，配置 2 天的作物实际会在 1、2、3 天成熟；设为 0 关闭浮动，所有作物按配置天数整批成熟。",
                        "",
                        " Random spread of the maturation time: every crop rolls its own total from the configured day count plus or minus this value, so a batch of crops does not ripen on the same day.",
                        " Example: with the default 1, a crop configured as 2 days actually ripens in 1, 2 or 3 days; 0 disables the spread.")
                .defineInRange("growthDayVariance", 1, 0, 30);

        MIN_GROW_HOURS = builder
                .comment(
                        "",
                        " 作物种下后要满这么多小时，才会在每天 6 点结算时推进成长，免得 5:59 种下、6:00 就白拿一天进度。",
                        " 时间按游戏内时钟算：默认 12 时，下午 6 点之后种下的作物要等到再下一次 6 点结算才涨。",
                        " 骨粉不受这条限制，种下就能用。",
                        " 设为 0 表示种下当天就能参与下一次结算。",
                        "",
                        " Hours a crop must have been planted before a 6 AM settlement may advance its growth, so planting at 5:59 AM does not earn a free day.",
                        " The clock is the in-game one: with the default 12, anything planted after 6 PM waits for the settlement after the next one.",
                        " Bone meal ignores this rule and works right after planting.",
                        " 0 lets a crop grow at the very next settlement.")
                .defineInRange("minGrowHours", 12.0D, 0.0D, 24.0D);

        BONE_MEAL_PER_DAY = builder
                .comment(
                        "",
                        " 每株作物每个 MC 天最多能用几次骨粉。超过次数再用，作物会直接枯死消失。",
                        " 设为 0 表示作物完全不接受骨粉。",
                        "",
                        " Bone meal uses allowed per crop per MC day. Using it beyond that limit withers the crop.",
                        " 0 means crops do not accept bone meal at all.")
                .defineInRange("boneMealPerDay", 1, 0, 64);

        BONE_MEAL_PROGRESS = builder
                .comment(
                        "",
                        " 每次施肥增加的成熟进度比例，0.1 表示一次长 10%。",
                        " 实际效果取「该进度」与「一个完整生长阶段」中更大的那个：阶段数少的作物（可可豆只有 2 级）按 10% 算不足一级，仍会直接长一级，所以每次有效施肥都看得见变化。",
                        "",
                        " Maturity progress added by each accepted bone meal, 0.1 means 10% per use.",
                        " The actual gain is the larger of that progress and one full growth stage, so crops with few stages (cocoa has only two) still advance a visible stage per use.")
                .defineInRange("boneMealProgress", 0.1D, 0.01D, 1.0D);

        ENABLE_CROP_WATER = builder
                .comment(
                        "",
                        " 是否启用作物耗水：耕地需要水源供水，种了作物的田会持续把水喝掉。",
                        " 只要田里有作物就耗水，包括已经成熟还没收的；缺水一样会让成熟作物枯死。",
                        " 关闭后耕地回到原版行为（水源无限），作物不会缺水。",
                        "",
                        " Whether planted crops consume water from a nearby water source.",
                        " Any crop in the plot drains water, mature and unharvested ones included, and thirst kills them just the same.",
                        " When disabled, farmland behaves like vanilla with unlimited water and crops never dry out.")
                .define("enableCropWater", true);

        WATER_PER_SOURCE = builder
                .comment(
                        "",
                        " 一格水能滋润的耕地数：只滋润离它最近的这么多块，其余的作物拿不到水。",
                        " 每天 6 点结算时，只要这些耕地里种着任意一株作物，结算完成长后这格水就会被替换成空气；一块作物都没种时水不消耗。",
                        " 水被抽走后，到第二天 6 点前没补回来，作物就会枯死、耕地变回泥土（补水就是重新倒一格静止水源）。",
                        " 只有静止的水源方块算数，流动的水不算。",
                        "",
                        " How many farmland plots one water source keeps watered: only the nearest ones, crops on the rest get nothing.",
                        " At each 6 AM settlement, if any of those plots holds a crop, the water block is replaced with air right after the growth is applied; an empty field leaves the water alone.",
                        " A crop that is not watered again before the next 6 AM withers and its farmland reverts to dirt.",
                        " Only still water source blocks count, flowing water does not.")
                .defineInRange("waterPerSource", 10, 1, 1000);

        CONSUME_NATURAL_WATER = builder
                .comment(
                        "",
                        " 河流、海洋等水构成的生物群系里的水，是否也照常被作物消耗。",
                        " 默认 true：河水海水一样要省着用，抽干了就得补水。",
                        " 设为 false：这些水永远不会减少（按原版 #minecraft:is_river、#minecraft:is_ocean 标签判定），鼓励在河边、岸边种植。",
                        "",
                        " Whether crops also drain water inside water biomes such as rivers and oceans.",
                        " Defaults to true, so river and sea water has to be managed and refilled like any other source.",
                        " Set it to false to make that water never run out, judged by the vanilla #minecraft:is_river and #minecraft:is_ocean tags, which rewards farming along rivers and coasts.")
                .define("consumeNaturalWater", true);

        CONSUME_INFINITE_WATER = builder
                .comment(
                        "",
                        " 原版意义上的「无限水」（周围有两格及以上水源、挖掉就会被原版补回来的水）是否也照常被作物消耗。",
                        " 这一条和上面的生物群系判定互相独立：默认 true，玩家自建的无限水池、天然湖泊一样会被抽干。",
                        " 设为 false：这类水不再减少（注意它同时也覆盖天然湖泊，因为它们和玩家水池在机制上无法区分）。",
                        "",
                        " Whether vanilla-style infinite water (a source that has two or more neighbouring sources and would be refilled by vanilla) is also drained by crops.",
                        " This is independent of the biome check above: it defaults to true, so player-built infinite pools and natural lakes both run dry.",
                        " Set it to false to keep that water from ever running out, which also covers lakes because they are mechanically indistinguishable from player pools.")
                .define("consumeInfiniteWater", true);

        DRY_WITHER_DAYS = builder
                .comment(
                        "",
                        " 缺水（水源耗尽，或这块田没被任何水源覆盖）后，作物先停止生长，再过这么多 MC 天就枯死消失、耕地变回泥土。",
                        " 设为 0 表示只停止生长，永不因缺水枯死。",
                        "",
                        " After losing water the crop stops growing, and after this many MC days it withers away and the farmland reverts to dirt.",
                        " 0 means the crop only stops growing and never dies of thirst.")
                .defineInRange("dryWitherDays", 1.0D, 0.0D, 1000.0D);

        SPEC = builder.build();
    }

    private SeedSeasonsConfig() {
    }

    public static List<String> seedSeasons() {
        return List.copyOf(SEED_SEASONS.get());
    }

    public static List<String> growthDays() {
        return List.copyOf(GROWTH_DAYS.get());
    }

    public static boolean enableCropLifecycle() {
        return ENABLE_CROP_LIFECYCLE.get();
    }

    public static double defaultGrowthDays() {
        return DEFAULT_GROWTH_DAYS.get();
    }

    public static double witherChance() {
        return WITHER_CHANCE.get();
    }

    public static double nearMatureChance() {
        return NEAR_MATURE_CHANCE.get();
    }

    public static double overMatureDays() {
        return OVER_MATURE_DAYS.get();
    }

    public static int growthDayVariance() {
        return GROWTH_DAY_VARIANCE.get();
    }

    public static double minGrowHours() {
        return MIN_GROW_HOURS.get();
    }

    public static int boneMealPerDay() {
        return BONE_MEAL_PER_DAY.get();
    }

    public static double boneMealProgress() {
        return BONE_MEAL_PROGRESS.get();
    }

    public static boolean enableCropWater() {
        return ENABLE_CROP_WATER.get();
    }

    public static int waterPerSource() {
        return WATER_PER_SOURCE.get();
    }

    public static boolean consumeNaturalWater() {
        return CONSUME_NATURAL_WATER.get();
    }

    public static boolean consumeInfiniteWater() {
        return CONSUME_INFINITE_WATER.get();
    }

    public static double dryWitherDays() {
        return DRY_WITHER_DAYS.get();
    }

    public static boolean saveSeasons(List<String> entries) {
        return save(SEED_SEASONS, entries);
    }

    public static boolean saveGrowthDays(List<String> entries) {
        return save(GROWTH_DAYS, entries);
    }

    private static boolean save(ModConfigSpec.ConfigValue<List<? extends String>> value, List<String> entries) {
        ModConfig config = modConfig;
        if (config == null || config.getLoadedConfig() == null) {
            return false;
        }
        value.set(entries);
        config.getLoadedConfig().save();
        return true;
    }

    @SubscribeEvent
    public static void onModConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        modConfig = event.getConfig();
        if (event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading) {
            SeasonMapping.requestReload();
        }
    }
}
