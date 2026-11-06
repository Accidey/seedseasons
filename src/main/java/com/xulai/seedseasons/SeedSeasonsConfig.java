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
    private static final ModConfigSpec.DoubleValue DRY_WITHER_DAYS;
    private static final ModConfigSpec.BooleanValue ENABLE_RAIN_GROWTH;
    private static final ModConfigSpec.DoubleValue MIN_RAIN_HOURS;
    private static final ModConfigSpec.BooleanValue ENABLE_CAULDRON_RAIN_FILL;
    private static final ModConfigSpec.DoubleValue CAULDRON_FILL_HOURS;
    private static final ModConfigSpec.BooleanValue ENABLE_SEASONAL_DAY_NIGHT;
    private static final ModConfigSpec.DoubleValue MC_DAY_LENGTH_MINUTES;
    private static final ModConfigSpec.DoubleValue SPRING_DAY_PERCENT;
    private static final ModConfigSpec.DoubleValue SUMMER_DAY_PERCENT;
    private static final ModConfigSpec.DoubleValue AUTUMN_DAY_PERCENT;
    private static final ModConfigSpec.DoubleValue WINTER_DAY_PERCENT;
    private static final ModConfigSpec.BooleanValue ENABLE_MAID_COMPAT;

    private static volatile ModConfig modConfig;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SEED_SEASONS = builder
                .comment(
                        "作物适种季节",
                        "Crop seasons",
                        "每行一条：<物品或方块 id> <季节...>，季节之间用空格分隔，可选 spring、summer、autumn、winter。",
                        "例：\"minecraft:potato spring summer autumn\"、\"minecraft:wheat_seeds spring summer autumn winter\"。",
                        "这里的季节会和数据包标签合并取并集：静谧四季内置的作物、以及其他模组为它适配的数据包都能照常生效，这里只做补充。",
                        "所以数据包里已经标好的作物，删掉这里的条目也不会失去季节限制；想让它们不受限制得改数据包。",
                        "默认留空：数据包照常生效，这里只写你想额外补充或延长季节的作物；列表为空时即不额外施加任何限制。",
                        "物品按它种下去的方块生效，所以写种子物品或作物方块都可以。",
                        "同一个 id 写多条时季节会叠加，想减少季节就把该 id 的季节写在一行里。",
                        "写错的条目会被单独忽略，不影响其它条目和作物。",
                        "手持物品执行 /seedseasons add <季节...> 写入（对同一作物重复执行会累加季节）、/seedseasons remove 移除整条。",
                        "保存文件或应用配置界面即生效，不需要重启游戏。",
                        "One entry per line: <item or block id> <seasons...>, seasons separated by spaces.",
                        "Valid seasons: spring, summer, autumn, winter.",
                        "These seasons are merged as a union with the datapack tags: crops shipped by Serene Seasons and by other mods that adapt to it keep working, and this file only adds to them.",
                        "A crop already tagged by a datapack keeps its seasons even when its entry is deleted here, so unrestricting it takes a datapack change.",
                        "Empty by default: the datapack stays in charge, so only list the crops whose seasons you want to add to or extend.",
                        "Items work through the block they plant, so the seed item and the crop block behave the same.",
                        "Entries for the same id stack, so list every wanted season of an id in one entry.",
                        "Entries that cannot be parsed are ignored individually, without affecting the other entries or crops.",
                        "Holding an item, run /seedseasons add <seasons...> to write it here (repeating it for one crop adds up its seasons), or /seedseasons remove to delete the whole entry.",
                        "Saving the file or applying the config screen takes effect live, no restart needed.",
                        "Default: [] / 默认：[]")
                .defineListAllowEmpty("seedSeasons", List.of(), () -> "", value -> value instanceof String);

        builder.comment(" ");

        ENABLE_CROP_LIFECYCLE = builder
                .comment(
                        "是否启用作物生命周期",
                        "Enable the crop lifecycle",
                        "以 MC 天为单位的成长、非当季枯死、成熟过久枯死；每天 6 点统一结算。",
                        "关闭后所有作物恢复原版随机刻生长，四季的季节限制仍然生效。",
                        "Whether the crop lifecycle is enabled: day-based growth, out-of-season withering and over-maturity withering, all settled at 6 AM.",
                        "When disabled, crops grow by vanilla random ticks again while the season restrictions of Serene Seasons still apply.",
                        "Default: true / 默认：true")
                .define("enableCropLifecycle", true);

        builder.comment(" ");

        GROWTH_DAYS = builder
                .comment(
                        "作物成熟天数",
                        "Crop maturation days",
                        "每行一条：<物品或方块 id> <天数>，天数可以是小数。",
                        "例：\"minecraft:wheat 2\"、\"minecraft:carrot 0.5\"；写种子物品或作物方块都一样。",
                        "列表默认留空：没列出的作物按静谧四季实际配置的季节时长自动推算——一个季节 = 3 个子季 × 子季时长，",
                        "跨 2 个季节及以上（含全年）的作物按一个季节一熟，只在 1 个季节生长的作物按当季两熟（减半）。",
                        "在这里列出的条目会覆盖推算值；改了四季的子季时长后，没列出的作物会自动跟随，列出的要手动调整。",
                        "同一个 id 写多条时以最后一条为准；写错的条目会被单独忽略。",
                        "手持物品执行 /seedseasons growth <天数> 写入、/seedseasons growth remove 移除。",
                        "只对作物类方块生效：作物、茎、下界疣、可可豆、甜浆果丛及其子类；竹子、藤蔓等保持原版。",
                        "One entry per line: <item or block id> <days>, the day count may be fractional.",
                        "Example: \"minecraft:wheat 2\", \"minecraft:carrot 0.5\"; the seed item and the crop block behave the same.",
                        "The list is empty by default: crops that are not listed derive their days from the season length actually configured in Serene Seasons - a season is 3 sub-seasons times the sub-season length.",
                        "Crops spanning two or more seasons (year-round included) get one harvest per season, crops limited to a single season get two (halved).",
                        "Entries listed here override the derivation; if you change the Serene Seasons sub-season length, unlisted crops follow automatically while listed ones need manual rescaling.",
                        "The last entry of an id wins; broken entries are ignored individually.",
                        "Holding an item, run /seedseasons growth <days> to write it here, or /seedseasons growth remove to delete it.",
                        "Only crop blocks are affected: crops, stems, nether wart, cocoa, sweet berry bush and their subclasses; bamboo and vines stay vanilla.",
                        "Default: [] / 默认：[]")
                .defineListAllowEmpty("growthDays", List.of(), () -> "", value -> value instanceof String);

        builder.comment(" ");

        WITHER_CHANCE = builder
                .comment(
                        "非当季枯死概率",
                        "Out-of-season wither chance",
                        "非当季作物到达生长节点时枯死（直接消失）的概率，每次生长机会判定一次。",
                        "0 表示不会枯死、只是停止生长；1 表示必定枯死。",
                        "Chance for an out-of-season crop to wither and vanish when its growth is due, rolled once per growth attempt.",
                        "0 never withers (the crop just stops growing), 1 always withers.",
                        "Default: 1.0 / 默认：1.0",
                        "Range: 0.0 ~ 1.0")
                .defineInRange("witherChance", 1.0D, 0.0D, 1.0D);

        builder.comment(" ");

        NEAR_MATURE_CHANCE = builder
                .comment(
                        "快成熟换季催熟概率",
                        "Near-maturity ripening chance",
                        "季节变化后，已经长到 3/4 以上（快成熟）却已不合时令的作物，被直接催熟的概率，避免换季把快收成的作物清掉。",
                        "和枯死一样，这个判定只在作物下一次「触发生长」时进行，不是换季瞬间立即结算。",
                        "Chance for a crop that is at least three quarters grown but now out of season to be ripened when the season changes, instead of being lost.",
                        "Like withering, this is only rolled on the crop's next growth attempt, not instantly when the season turns.",
                        "Default: 0.8 / 默认：0.8",
                        "Range: 0.0 ~ 1.0")
                .defineInRange("nearMatureChance", 0.8D, 0.0D, 1.0D);

        builder.comment(" ");

        OVER_MATURE_DAYS = builder
                .comment(
                        "成熟后允许滞留的天数",
                        "Over-maturity days",
                        "成熟后满这么多天仍未收获就枯死（直接消失），0 表示不因过熟枯死。",
                        "判定只在每天 6 点结算时做一次，所以「第 N 天 6 点成熟」的作物正好在第 N+1 天 6 点（正好 24 小时后）枯死；补水只清缺水计时，救不回它。",
                        "Days a crop may stay mature without being harvested before it withers and vanishes, 0 disables it.",
                        "The check runs once at each 6 AM settlement, so a crop maturing at day N 6 AM withers at day N+1 6 AM, exactly 24 hours later; watering clears the thirst timer only and does not save it from over-maturity.",
                        "Default: 1.0 / 默认：1.0",
                        "Range: 0.0 ~ 1000.0")
                .defineInRange("overMatureDays", 1.0D, 0.0D, 1000.0D);

        builder.comment(" ");

        GROWTH_DAY_VARIANCE = builder
                .comment(
                        "成熟天数随机浮动",
                        "Maturation day variance",
                        "每株作物种下时会在「配置天数 ± 该值」之间取一个随机天数，所以同一批作物不会同一天一起成熟。",
                        "例：默认 1 时，配置 2 天的作物实际会在 1、2、3 天成熟；设为 0 关闭浮动，所有作物按配置天数整批成熟。",
                        "Random spread of the maturation time: every crop rolls its own total from the configured day count plus or minus this value, so a batch of crops does not ripen on the same day.",
                        "Example: with the default 1, a crop configured as 2 days actually ripens in 1, 2 or 3 days; 0 disables the spread.",
                        "Default: 1 / 默认：1",
                        "Range: 0 ~ 30")
                .defineInRange("growthDayVariance", 1, 0, 30);

        builder.comment(" ");

        MIN_GROW_HOURS = builder
                .comment(
                        "种下到能生长的最短小时数",
                        "Minimum growth hours",
                        "作物种下后要满这么多小时，才会在每天 6 点结算时推进成长，免得 5:59 种下、6:00 就白拿一天进度。",
                        "时间按游戏内时钟算：默认 12 时，下午 6 点之后种下的作物要等到再下一次 6 点结算才涨。",
                        "骨粉不受这条限制，种下就能用。",
                        "设为 0 表示种下当天就能参与下一次结算。",
                        "Hours a crop must have been planted before a 6 AM settlement may advance its growth, so planting at 5:59 AM does not earn a free day.",
                        "The clock is the in-game one: with the default 12, anything planted after 6 PM waits for the settlement after the next one.",
                        "Bone meal ignores this rule and works right after planting.",
                        "0 lets a crop grow at the very next settlement.",
                        "Default: 12.0 / 默认：12.0",
                        "Range: 0.0 ~ 24.0")
                .defineInRange("minGrowHours", 12.0D, 0.0D, 24.0D);

        builder.comment(" ");

        BONE_MEAL_PER_DAY = builder
                .comment(
                        "每株每天可用骨粉次数",
                        "Bone meal uses per day",
                        "每株作物每个 MC 天最多能用几次骨粉，玩家与农场模式的女仆共用同一份额度。超过次数再用，作物会直接枯死消失。",
                        "设为 0 表示作物完全不接受骨粉。",
                        "Bone meal uses allowed per crop per MC day, shared between the player and farm-mode maids. Using it beyond that limit withers the crop.",
                        "0 means crops do not accept bone meal at all.",
                        "Default: 1 / 默认：1",
                        "Range: 0 ~ 64")
                .defineInRange("boneMealPerDay", 1, 0, 64);

        builder.comment(" ");

        BONE_MEAL_PROGRESS = builder
                .comment(
                        "每次施肥的推进比例",
                        "Progress per bone meal",
                        "每次施肥增加的成熟进度比例，0.1 表示一次长 10%。",
                        "施肥不在当下生效：先记下待结算的层数，在下一次每天 6 点的结算里一次性叠加。",
                        "实际效果取「该进度」与「一个完整生长阶段」中更大的那个：阶段数少的作物（可可豆只有 2 级）按 10% 算不足一级，仍会直接长一级，所以每层有效施肥都看得见变化。",
                        "Maturity progress added by each accepted bone meal, 0.1 means 10% per use.",
                        "Bone meal does not apply instantly: uses are queued and all stack at the next 6 AM settlement.",
                        "The actual gain is the larger of that progress and one full growth stage, so crops with few stages (cocoa has only two) still advance a visible stage per use.",
                        "Default: 0.1 / 默认：0.1",
                        "Range: 0.01 ~ 1.0")
                .defineInRange("boneMealProgress", 0.1D, 0.01D, 1.0D);

        builder.comment(" ");

        ENABLE_CROP_WATER = builder
                .comment(
                        "是否启用作物耗水",
                        "Enable crop water consumption",
                        "耕地需要水源供水，种了作物的田会持续把水喝掉。",
                        "只要田里有作物就耗水，包括已经成熟还没收的；缺水一样会让成熟作物枯死。",
                        "关闭后耕地回到原版行为（水源无限），作物不会缺水。",
                        "Whether planted crops consume water from a nearby water source.",
                        "Any crop in the plot drains water, mature and unharvested ones included, and thirst kills them just the same.",
                        "When disabled, farmland behaves like vanilla with unlimited water and crops never dry out.",
                        "Default: true / 默认：true")
                .define("enableCropWater", true);

        builder.comment(" ");

        WATER_PER_SOURCE = builder
                .comment(
                        "一格水的服务配额",
                        "Farmland served per water cell",
                        "水塘内一格水能同时服务多少块耕地；水塘总配额 = 塘内水格数 × 该值，只服务离水塘最近的这么多块田，排不进的作物拿不到水。",
                        "每天 6 点结算时，被服务且种着作物的田会按「田数 ÷ 该值」向上取整抽走水塘里对应格数的水；一块作物都没种时水不消耗。",
                        "水被抽走后，到第二天 6 点前没补回来，作物就会枯死、耕地变回泥土（补水就是重新倒一格静止水源）。",
                        "只有静止的水源方块算水源，流动的水不算。",
                        "How many farmland plots one water cell of a pond serves; the pond quota is the number of cells times this value, and only the nearest plots get watered.",
                        "At each 6 AM settlement the pond gives up ceil(plotsWithCrops / this value) cells, rounded up; an empty field leaves the water alone.",
                        "A crop that is not watered again before the next 6 AM withers and its farmland reverts to dirt.",
                        "Only still water source blocks count, flowing water does not.",
                        "Default: 10 / 默认：10",
                        "Range: 1 ~ 1000")
                .defineInRange("waterPerSource", 10, 1, 1000);

        builder.comment(" ");

        DRY_WITHER_DAYS = builder
                .comment(
                        "缺水后枯死的天数",
                        "Days before thirst withers",
                        "缺水（水源耗尽，或这块田没被任何水源覆盖）后，作物先停止生长，再过这么多 MC 天就枯死消失、耕地变回泥土。",
                        "设为 0 表示只停止生长，永不因缺水枯死。",
                        "After losing water the crop stops growing, and after this many MC days it withers away and the farmland reverts to dirt.",
                        "0 means the crop only stops growing and never dies of thirst.",
                        "Default: 1.0 / 默认：1.0",
                        "Range: 0.0 ~ 1000.0")
                .defineInRange("dryWitherDays", 1.0D, 0.0D, 1000.0D);

        builder.comment(" ");

        ENABLE_RAIN_GROWTH = builder
                .comment(
                        "是否启用降雨免旱",
                        "Enable rain drought relief",
                        "下满 minRainHours 小时后，露天作物无需水方块也能生长、不会干旱。",
                        "只依赖原版降雨本身，不含雨强分级、档位变化或积水等额外机制。",
                        "Whether crops can grow on rain alone: after minRainHours of rain, exposed crops grow without a water block and never dry out.",
                        "This relies on vanilla rain only, without rain grades, intensity drift or puddle mechanics.",
                        "Default: true / 默认：true")
                .define("enableRainGrowth", true);

        builder.comment(" ");

        MIN_RAIN_HOURS = builder
                .comment(
                        "降雨免旱所需小时数",
                        "Hours of rain required",
                        "作物靠雨水生长（不需要水方块）时，本次降雨要持续满这么多游戏内小时才生效，只对真的淋到雨的作物有效（不在屋檐树冠下、当地此刻下的是雨而不是雪）。",
                        "1 小时 = 1000 tick 的游戏时钟；原版一场雨长 12~24 游戏小时，所以默认 12 表示「下到一场雨的后半段才开始靠雨生长」。",
                        "设为 0 表示下雨即生效。",
                        "How many in-game hours the current rain must have lasted before crops can grow on rain alone, without a water block; only crops that are actually getting wet count (not under a roof or leaves, and where the local precipitation is rain rather than snow).",
                        "An hour here is 1000 ticks of the game clock; a vanilla storm lasts 12-24 of those hours, so the default 12 means crops feed off the second half of a rain.",
                        "0 means it takes effect as soon as it rains.",
                        "Default: 12.0 / 默认：12.0",
                        "Range: 0.0 ~ 24.0")
                .defineInRange("minRainHours", 12.0D, 0.0D, 24.0D);

        builder.comment(" ");

        ENABLE_CAULDRON_RAIN_FILL = builder
                .comment(
                        "是否启用炼药锅接雨",
                        "Enable cauldron rain collection",
                        "露天的炼药锅在下雨（下雪不算）时自动积水，空锅 → 1 层 → 2 层 → 满 3 层。",
                        "关闭后回到原版行为：只有服务端的随机天气采样命中那格时才有 5% 概率加一层，慢到几乎察觉不到。",
                        "锅里的水不是水源方块：不给耕地供水、不参与水塘折算，女仆也不会从锅里舀水或往锅里倒。",
                        "Whether open-air cauldrons collect rainwater: an exposed cauldron fills step by step while rain (not snow) is falling on it - empty, then 1, 2 and 3 layers.",
                        "When disabled, vanilla behaviour remains: a layer is only added when the server's random weather sampling lands on that cell, with a 5% roll, which is far too slow to notice.",
                        "Water inside a cauldron is not a water source block: it waters no farmland, joins no pond quota, and maids neither scoop from nor pour into it.",
                        "Default: true / 默认：true")
                .define("enableCauldronRainFill", true);

        builder.comment(" ");

        CAULDRON_FILL_HOURS = builder
                .comment(
                        "炼药锅积满所需小时数",
                        "Hours to fill a cauldron",
                        "一口空锅在连续淋雨下积满 3 层需要多少游戏小时；1 游戏小时 = 1000 tick 的游戏时钟，会随季节昼夜和时间倍速一起变快变慢。",
                        "默认 9：每层约 3 小时，每口锅的时长在 2~4 小时之间随机浮动，所以一整排锅不会同刻满。",
                        "雨停不会倒扣已经接到的水，下一场雨从当前层数接着接。",
                        "Game hours of steady rainfall an empty cauldron needs to reach 3 layers; one hour here is 1000 ticks of the game clock, so it speeds up and slows down with seasonal day length and time scale.",
                        "Default 9: roughly 3 hours per layer, with each cauldron rolling between two thirds and one third more, so a row of cauldrons never fills in unison.",
                        "Rain stopping never removes collected water; the next storm resumes from the current layer.",
                        "Default: 9.0 / 默认：9.0",
                        "Range: 0.5 ~ 240.0")
                .defineInRange("cauldronFillHours", 9.0D, 0.5D, 240.0D);

        builder.comment(" ");

        ENABLE_SEASONAL_DAY_NIGHT = builder
                .comment(
                        "是否启用季节昼夜时长",
                        "Enable seasonal day/night length",
                        "让主世界一天的总长固定，但白天/夜晚的占比随季节变化。",
                        "需要 BetterDays 提供时间流速机制；本模组会按当前季节和时刻覆盖每 tick 的时间推进速度。",
                        "关闭后恢复 BetterDays 默认行为。",
                        "Whether seasonal day/night length is enabled: the overworld keeps a fixed day length while the day/night ratio changes with the season.",
                        "Requires BetterDays to drive the time speed; this mod overrides the per-tick time progression based on the current season and time of day.",
                        "Disable to restore BetterDays' default behaviour.",
                        "Default: true / 默认：true")
                .define("enableSeasonalDayNight", true);

        builder.comment(" ");

        MC_DAY_LENGTH_MINUTES = builder
                .comment(
                        "一天的现实分钟数",
                        "Real minutes per game day",
                        "主世界一天（从日出到下一次日出）等于多少现实分钟。",
                        "默认 60：游戏里过完一天正好约 1 个现实小时（原版是 20 分钟）。",
                        "改成 30 就是半小时一天，改成 120 就是两小时一天。",
                        "How many real-life minutes one full day in the overworld lasts.",
                        "Default 60: one in-game day takes about one real hour (vanilla is 20 minutes).",
                        "Set it to 30 for a half-hour day, or 120 for a two-hour day.",
                        "Default: 60.0 / 默认：60.0",
                        "Range: 1.0 ~ 10000.0")
                .defineInRange("mcDayLengthMinutes", 60.0D, 1.0D, 10000.0D);

        builder.comment(" ");

        SPRING_DAY_PERCENT = builder
                .comment(
                        "春季白天占比",
                        "Spring daytime percent",
                        "春季一天中「白天」占多少百分比（%），剩下的就是夜晚。",
                        "默认 50：一天里白天和夜晚各占一半。",
                        "例子：设成 30，则一天里白天只占 30%（配合默认的 1 天 = 60 现实分钟，白天约 18 分钟、夜晚约 42 分钟）。",
                        "What percentage of a spring day is daytime (%); the rest is night.",
                        "Default 50: day and night are equally long.",
                        "Example: 30 means daytime only takes 30% of the day (with the default 1 day = 60 real minutes, that is about 18 minutes of day and 42 of night).",
                        "Default: 50.0 / 默认：50.0",
                        "Range: 1.0 ~ 99.0")
                .defineInRange("springDayPercent", 50.0D, 1.0D, 99.0D);

        builder.comment(" ");

        SUMMER_DAY_PERCENT = builder
                .comment(
                        "夏季白天占比",
                        "Summer daytime percent",
                        "夏季一天中「白天」占多少百分比（%），剩下的就是夜晚。",
                        "默认 65：白天比夜晚长，符合夏天白昼长的直觉。",
                        "例子：设成 70，则一天里白天占 70%（配合默认的 1 天 = 60 现实分钟，白天约 42 分钟、夜晚约 18 分钟）。",
                        "What percentage of a summer day is daytime (%); the rest is night.",
                        "Default 65: daytime is longer than night, matching the long summer days.",
                        "Example: 70 means daytime takes 70% of the day (with the default 1 day = 60 real minutes, that is about 42 minutes of day and 18 of night).",
                        "Default: 65.0 / 默认：65.0",
                        "Range: 1.0 ~ 99.0")
                .defineInRange("summerDayPercent", 65.0D, 1.0D, 99.0D);

        builder.comment(" ");

        AUTUMN_DAY_PERCENT = builder
                .comment(
                        "秋季白天占比",
                        "Autumn daytime percent",
                        "秋季一天中「白天」占多少百分比（%），剩下的就是夜晚。",
                        "默认 50：一天里白天和夜晚各占一半。",
                        "例子：设成 40，则一天里白天只占 40%（配合默认的 1 天 = 60 现实分钟，白天约 24 分钟、夜晚约 36 分钟）。",
                        "What percentage of an autumn day is daytime (%); the rest is night.",
                        "Default 50: day and night are equally long.",
                        "Example: 40 means daytime takes 40% of the day (with the default 1 day = 60 real minutes, that is about 24 minutes of day and 36 of night).",
                        "Default: 50.0 / 默认：50.0",
                        "Range: 1.0 ~ 99.0")
                .defineInRange("autumnDayPercent", 50.0D, 1.0D, 99.0D);

        builder.comment(" ");

        WINTER_DAY_PERCENT = builder
                .comment(
                        "冬季白天占比",
                        "Winter daytime percent",
                        "冬季一天中「白天」占多少百分比（%），剩下的就是夜晚。",
                        "默认 35：白天比夜晚短，符合冬天白昼短的直觉。",
                        "例子：设成 30，则一天里白天只占 30%（配合默认的 1 天 = 60 现实分钟，白天约 18 分钟、夜晚约 42 分钟）。",
                        "What percentage of a winter day is daytime (%); the rest is night.",
                        "Default 35: daytime is shorter than night, matching the short winter days.",
                        "Example: 30 means daytime only takes 30% of the day (with the default 1 day = 60 real minutes, that is about 18 minutes of day and 42 of night).",
                        "Default: 35.0 / 默认：35.0",
                        "Range: 1.0 ~ 99.0")
                .defineInRange("winterDayPercent", 35.0D, 1.0D, 99.0D);

        builder.comment(" ");

        ENABLE_MAID_COMPAT = builder
                .comment(
                        "是否启用车万女仆兼容",
                        "Enable Touhou Little Maid companion",
                        "处于农场模式的女仆会顺手给缺水的耕地补水、按每天的骨粉额度施肥，并且不再在反季播种。",
                        "只改变女仆的行为，玩家、其他模组和每日结算的规则都不受影响；没有安装车万女仆时这条不起作用。",
                        "关闭后女仆回到原版农场模式的表现：只种只收，不管水也不施肥，反季照样种。",
                        "Whether the Touhou Little Maid companion is enabled: a maid in farm mode waters thirsty farmland, fertilizes crops within the daily bone meal allowance, and stops planting out of season.",
                        "Only maid behaviour changes - players, other mods and the daily settlement keep working exactly as before, and the option does nothing when Touhou Little Maid is not installed.",
                        "Disable to leave maids on their plain farm mode: they plant and harvest only, ignore water, and plant out of season as well.",
                        "Default: true / 默认：true")
                .define("enableMaidCompat", true);

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

    public static double dryWitherDays() {
        return DRY_WITHER_DAYS.get();
    }

    public static boolean enableRainGrowth() {
        return ENABLE_RAIN_GROWTH.get();
    }

    public static double minRainHours() {
        return MIN_RAIN_HOURS.get();
    }

    public static boolean enableCauldronRainFill() {
        return ENABLE_CAULDRON_RAIN_FILL.get();
    }

    public static double cauldronFillHours() {
        return CAULDRON_FILL_HOURS.get();
    }

    public static boolean enableSeasonalDayNight() {
        return ENABLE_SEASONAL_DAY_NIGHT.get();
    }

    public static double mcDayLengthMinutes() {
        return MC_DAY_LENGTH_MINUTES.get();
    }

    public static double springDayPercent() {
        return SPRING_DAY_PERCENT.get();
    }

    public static double summerDayPercent() {
        return SUMMER_DAY_PERCENT.get();
    }

    public static double autumnDayPercent() {
        return AUTUMN_DAY_PERCENT.get();
    }

    public static double winterDayPercent() {
        return WINTER_DAY_PERCENT.get();
    }

    public static boolean enableMaidCompat() {
        return ENABLE_MAID_COMPAT.get();
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
