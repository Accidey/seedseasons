# 🌱Seed Seasons

**Farming that follows the calendar, and the water table.**

Seed Seasons is a farming overhaul built on Serene Seasons. It replaces random-tick growth with a day-based lifecycle, gives farmland a real water supply that can genuinely run dry, and puts both under the rule of the seasons.

## ✨ At a Glance

- 🌾 Crops mature over a set number of days, settled once per day
- 🗓️ Out-of-season crops stop growing and may wither
- 🌱 Bone meal is queued, one dose per crop per day
- 💧 Farmland draws from still water sources that are actually consumed
- 🌧️ Rain and cauldrons offer relief when the pond is empty
- 🌗 Daylight hours follow the season
- 🚿 A powered Irrigator waters a field without a pond
- 🧹 Maids tend the field, and Jade reports crop status

## 🌾 Day-Based Growth

Random ticks no longer drive growth. Every crop advances once per Minecraft day, at a single settlement shortly after 6:00 AM. A crop set to mature in three days is ripe on the third settlement after planting.

Each plant rolls a small random offset around its maturation time, so a field does not ripen in one instant. Unloaded chunks pause and resume from their saved state: nothing is lost, nothing is skipped.

Maturation time appears on the seed's tooltip. Unless set explicitly, it is derived from the season length configured in Serene Seasons.

A newly planted crop waits twelve in-game hours before it joins its first settlement, so sowing just before dawn buys no free day.

## 🗓️ Seasons Decide Survival

A crop's allowed seasons are merged from two sources: Serene Seasons datapack tags, and a configurable list that only adds to them. Nothing is taken from datapacks or from other mods' adapters.

Outside its season, a crop stops growing and, by default, withers at its next growth check. A crop already three-quarters grown when the season turns has an 80% chance to be forced ripe instead, so late harvests usually survive the change.

Mature crops left standing wither after a day. Melon and pumpkin stems are exempt and keep bearing fruit on adjacent soil, while cocoa needs no water at all.

## 🌱 Bone Meal, Queued

Bone meal still works, but never instantly. Each use is queued and resolves at the next daily settlement, where every queued layer advances at least one visible growth stage.

A crop accepts one application per day by default, and that allowance is shared with anyone else tending the field. Going over it withers the crop on the spot. Set the allowance to zero and crops refuse bone meal entirely.

## 💧 Water as a Resource

Farmland draws from nearby still water sources. One source supplies ten plots, nearest first; touching sources merge into a single pond with a shared quota. Empty plots cost nothing, while mature unharvested crops still count.

Each settlement drains the pond according to the number of planted plots. Vanilla infinite water is disabled: drained cells become air and never return, and rivers, lakes and oceans are consumed just the same. Refill before the next settlement and nothing is harmed.

A crop without water stops growing and starts a thirst timer. After a full day it withers and the farmland reverts to dirt; watering resets the timer. Farmland no longer darkens when dry, so soil colour says nothing, read the crop's status instead.

## 🌧️ Rain and Cauldrons

Once a storm has lasted twelve in-game hours by default, sky-exposed crops count as watered: they grow normally and their thirst is cleared. Roofs, canopies and snow do not qualify.

A cauldron left in the open fills itself while it rains, one layer at a time. It joins no irrigation network and waters no farmland, but the water is free: bucket it out and not one source block leaves your fields.

## 🚿 The Irrigator

A machine that keeps farmland supplied without a pond. It serves the nearest twenty plots within eight blocks, holds one hundred buckets and runs on redstone; pipes connect to its top face for automated filling and draining. Each machine serves its own plots, so a large field wants several, spread out.

A status light reports its state at a glance: green while watering, red when powered but empty, dark when idle. One bucket lasts two Minecraft days, and only while crops are actually served. A water bucket fills it one bucket at a time, an empty bucket draws one out, and any other item opens a gauge with the exact contents.

Crafting takes four cauldrons, a water bucket, two glass blocks and two iron ingots.

## 🌗 Seasonal Daylight

With BetterDays installed, the length of a day stays fixed while the daylight share follows the season: a full day is 60 real minutes by default, 65% of it lit in summer and 35% in winter. Only the Overworld is affected, and only time speed, not sky light or tick counts. Everything measured in in-game hours moves with it.

## 🧹 Companions

A maid in farm mode, with Touhou Little Maid installed, waters plots that lost their supply, fertilizes within the daily allowance and refuses to sow out of season. She changes no rule that applies to you; she simply does the chores.

Jade adds crop progress, fertilizing state and thirst to the tooltip, using the same checks the game itself runs.

## 📖 Guide and Commands

A Patchouli handbook arrives on first join, and a copy can always be taken from the Tools & Utilities creative tab. It covers every mechanic, the config reference and troubleshooting.

Seasons and maturation time can be edited without touching a file: hold a seed or crop and use the mod's commands, which require operator permission. Changes go straight into the config and apply immediately, with no restart. Crops already in the ground keep their current progress and continue at the new pace.

## ⚙️ Configuration

Options live in config/seedseasons-common.toml and can also be edited in game, from the Mods menu. Most settings take effect the moment the file is saved or the config screen is applied, with no restart.

Every system has its own switch and numbers: growth days, wither chances, water quota, thirst and over-maturity timers, rain threshold, cauldron pace, day length and daylight shares, irrigator range and capacity, maid behaviour. Disable the crop lifecycle and crops return to vanilla random ticks while Serene Seasons keeps its restrictions.

## 📦 Requirements

- **Serene Seasons** for the season system
- **BetterDays** for the time speed behind seasonal daylight
- **Jade**, **Patchouli** and **Touhou Little Maid** are optional integrations

---

# 🌱 四季物语

**让农事跟着节气走，也守着水。**

四季物语是一款建立在静谧四季之上的农业改造模组。它以按天推进的生长取代随机刻，让耕地拥有会真正枯竭的水源，并把两者都交给季节支配。

## ✨ 概览

- 🌾 作物按设定的天数成熟，每天只结算一次
- 🗓️ 非当季的作物停止生长，并可能枯死
- 🌱 骨粉改为排队结算，每株每天一剂
- 💧 耕地从静态水源取水，水源会被真正抽干
- 🌧️ 池塘见底时，降雨与炼药锅提供补给
- 🌗 白昼时长随季节变化
- 🚿 通电的灌溉机让农田无需池塘
- 🧹 女仆打理农活，Jade 显示作物状态

## 🌾 按天生长

随机刻不再决定生长。每株作物每天推进一次，在早上 6 点后的一次统一结算中完成。设定三天成熟的作物，会在播种后的第三次结算时成熟。

每株作物在成熟时间附近有小幅随机浮动，一片田不会在同一瞬间全部成熟。未加载的区块暂停推进，重新加载后从存档状态继续，不丢失，也不跳步。

成熟天数显示在种子提示中；未单独设定时，按静谧四季所配置的季节长度推算。

新播种的作物需等待十二个游戏小时才会参与首次结算，卡在清晨播种占不到便宜。

## 🗓️ 季节决定存亡

作物的可种植季节由两处合并而来：静谧四季的数据包标签，以及一份只做补充的配置列表。数据包与其它模组的适配不会被覆盖或削减。

不在当季时，作物停止生长，并默认在下一次生长判定时枯死。若换季时作物已长到四分之三以上，则有 80% 的概率被直接催熟，临近收获的作物通常能熬过换季。

成熟后久不采收的作物会在一天后枯死。西瓜与南瓜的茎例外，不会因过熟而死，会持续在旁边的土壤上结果；可可豆完全不需要水。

## 🌱 骨粉排队结算

骨粉仍然有效，但不再瞬间生效。每次使用都会排队，在次日结算时统一兑现，每层至少推进一个可见的生长阶段。

每株作物每天默认只接受一次，这份配额与同在田间劳作的其它人共用。超量使用会让作物当场枯死。把配额设为 0，作物就完全不接受骨粉。

## 💧 水是资源

耕地从附近的静态水源取水。一个水源可供十块耕地，由近及远；相连的水源合并为同一片水域，共享配额。空置的耕地不耗水，已成熟但未采收的作物仍然计入。

每次结算按种有作物的耕地数量抽水。原版无限水被关闭：被抽走的水源变为空气，不会自行回补；河流、湖泊与海洋同样会被消耗。在下次结算前补上水，就不会有任何损失。

缺水的作物停止生长并开始计时，满一天后枯死，耕地退回泥土；重新供水会重置计时。缺水时耕地不再变干变浅，土色说明不了任何问题，请直接查看作物状态。

## 🌧️ 降雨与炼药锅

一场雨默认持续满十二个游戏小时后，暴露在天空下的作物即视为已浇灌：正常生长，缺水计时清零。屋檐、树冠与雪都不算数。

放在露天的炼药锅会在雨中自行积水，一层一层上涨。它不接入灌溉系统，也不浇灌耕地，但这份水是白得的：舀出来用，田里不会少一个水源。

## 🚿 灌溉机

一台无需池塘即可维持农田供水的机械。它服务自身八格范围内最近的二十块耕地，可储水一百桶，由红石驱动，顶面可接管道自动注水与抽水。每台只服务自己那批耕地，大田需要错开摆放多台。

状态灯一眼可辨：供水时为绿色，通电但无水为红色，待机为暗色。一桶水可用两个游戏日，且只在确实供水时消耗。手持水桶右键注入一桶，空桶右键取出一桶，其它物品右键则打开读数界面，精确到桶。

合成需要四个炼药锅、一个水桶、两块玻璃和两个铁锭。

## 🌗 季节昼夜

安装 BetterDays 后，一天的总长度保持不变，白昼所占比例随季节变化：默认一天为 60 分钟现实时间，夏季 65% 为白昼，冬季 35%。仅影响主世界，且只改变时间流速，不改变天空光照曲线或一天的刻数。所有以游戏内小时计的机制都会随之加快或减慢。

## 🧹 帮手

安装东方小女仆后，农场模式的女仆会为失去水源的耕地补水、在每日配额内施肥，并拒绝在非当季播种。她不改变任何对你生效的规则，只是把杂活做完。

安装 Jade 后，作物进度、施肥状态与缺水状态会显示在提示中，与游戏内部的判定完全一致。

## 📖 手册与指令

首次进入游戏会获得一本帕秋莉手册，创造模式的「工具与实用物品」页签中也可随时取用。手册涵盖全部机制、配置速查与常见问题。

作物季节与成熟天数无需打开文件即可修改：手持种子或作物，用模组指令添加或移除季节，或设定天数，该指令需要管理员权限。改动直接写入配置并立即生效，不必重启游戏；已经种下的作物会保留当前进度，按新的天数继续生长。

## ⚙️ 配置

选项位于 config/seedseasons-common.toml，也可以在游戏内的模组菜单里直接修改。大多数设置会在保存文件或应用配置界面时立即生效，无需重启。

每个系统都有独立开关与数值：成熟天数、枯死概率、单水源配额、缺水与过熟时限、降雨阈值、炼药锅积水速度、一天长度与各季白昼比例、灌溉机范围与容量、女仆行为。关闭作物生命周期后，作物恢复原版随机刻生长，静谧四季的季节限制照常生效。

## 📦 前置

- **静谧四季**：季节系统本身
- **BetterDays**：季节昼夜所需的时间流速
- **Jade**、**帕秋莉手册**、**东方小女仆**：可选联动
