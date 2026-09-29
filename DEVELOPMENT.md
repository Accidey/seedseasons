# 更好的四季 · seedseasons 开发文档

## 1. 工程概览

| 项 | 值 |
|---|---|
| 仓库 | `D:\Games\MOD\更好的四季`（本文件所在目录） |
| 自研模组 | modid `seedseasons`，包名 `com.xulai.seedseasons`，显示名 `Xulai Seed Seasons` |
| 构建产物 | `build/libs/seedseasons-<mod_version>-<mc_version>-NeoForge.jar` |
| 目标平台 | Minecraft 1.21.1 / NeoForge（`neo_version=21.1.249`，NeoGradle 7.1.38） |
| 硬依赖 | SereneSeasons（静谧四季）10.1.0.3、GlitchCore 2.1.0.2 |
| 可选依赖 | Jade 15.10.6（仅客户端；未安装时功能不受影响） |
| 仓库内其它目录 | `BetterDays-1.21.1/`、`SereneSeasons-1.21.1/`、`Jade-1.21-neoforge/` 为**参考源码**（不参与构建）；`local-repo/` 是构建用的本地 Maven 仓库 |

模组的作用：在静谧四季（SereneSeasons）基础上叠加三层玩法——
① 作物适种季节可由配置文件补充与数据包并集；
② 作物成长改为以 MC 天为单位、每天固定时刻统一结算的生命周期；
③ 种植需要消耗水源。

---

## 2. 功能规格（与当前代码一致）

### 2.1 作物季节（`seedSeasons`）

- 配置项：`config/seedseasons-common.toml` 的 `seedSeasons`，每行 `<物品或方块 id> <季节...>`，季节可选 `spring/summer/autumn/winter`，行内可用 `;` 分隔多条；写种子物品或作物方块等价（物品经 `BlockItem` 解析成方块）。
- **语义是并集**：配置里的季节与「静谧四季内置标签 + 其它模组为它适配的数据包标签」合并，两边都生效。所以数据包已标好的作物，删掉配置条目也不会失去季节限制。
- 默认留空 = 完全交给数据包。
- 条目逐条容错：坏 id / 空条目 / 无有效季节整条忽略；季节词拼错只丢那个词；注册表里查不到的 id 不进集合。
- 指令（OP 2 级、取主手物品）：`/seedseasons add <季节...>`（同一作物重复执行取并集累加季节）、`/seedseasons remove`（删该 id 的条目）、`/seedseasons growth <天数>` 与 `/seedseasons growth remove`（写/删该 id 在 `growthDays` 里的成熟天数条目）。

### 2.2 作物生命周期（`enableCropLifecycle`）

- **结算时机**：每天 `dayTime` 跨过 0（= 早上 6:00）时，对**已加载区块**里的所有作物统一结算一次，每株进度 `+1/成熟天数`。结算前先扫一遍区块、给还没建档的作物补档（**不依赖随机刻**：随机刻只提前建档并跳过原版成长，抽不到也不影响当天结算）。
- **成熟天数**：`growthDays` 按作物覆盖（默认留空）；**未列出的作物按静谧四季实际配置的季节时长动态推算**——一个季节 = 3 子季 × `subSeasonDuration`（SS 配置，默认 8 天 → 24 天），从作物的季节掩码（SS 标签 + 本模组配置的并集，`SeasonMapping.externalMasks` 快照）判断跨几季：跨 2 季及以上（含全年）一季一熟，单季两熟（减半），完全没有季节信息按一季一熟。在这里列出的条目覆盖推算值；改了 SS 的子季时长后，未列出的作物自动跟随，列出的要手动调整。每株种下时按 `growthDayVariance`（默认 ±1 天）roll 出各自总时长，所以同批作物不会同日成熟。改配置后已种下的作物会在下次登记时按新配置重算时长（保留进度）。
- 进度按 `1/(roll 出的天数)` 逐日累加，**累加到 ≥ 98% 就直接吸附成 1.0（视为成熟）**——既兜住二进制浮点舍入（6/7/23/24 这类天数会累加到 0.9999999999999998），也避免高天数作物或骨粉混合把进度停在 99% 让玩家白等一天；骨粉叠加同样按 98% 吸附。
- **进度是 0~1 的连续值**，方块年龄 = `floor(进度 × 最大年龄)`。
- **种植门槛**：`minGrowHours`（默认 12，按游戏内时钟算，0 = 关闭）——种下不满这么多小时的作物，结算时只跳过涨进度，避免 5:59 种下、6:00 白拿一天。种植/收获会**即时**建档与销档，所以门槛计时从真正种下那一刻开始，不是从结算时刻开始。骨粉不受门槛限制。
- **即时更新**：玩家种下作物 → `BlockEvent.EntityPlaceEvent` 立刻建档（记下种植时刻与作物 id）；玩家破坏作物 → `BlockEvent.BreakEvent` 立刻删档。因此「收获后补种」拿到的是全新纪录，不会继承上一株的进度（踩过的坑：旧只靠 `lastAge` 判断，收获后没随机刻就会被旧进度直接催熟）。记录里还存了作物 id，同格换成另一种作物（起始 age 相同）也会重置。
- **非当季**：每天结算时若不合时令，按 `witherChance` 枯死（直接消失，同四季的「摧毁」）。
- **快成熟换季**：进度 ≥ 75%（写死）且换季后不合时令时，按 `nearMatureChance` 直接催熟；判定与枯死一样只发生在每天结算那一刻，不是换季瞬间。
- **过熟**：成熟后 `overMatureDays` 天未收获则枯死（0 = 关闭）。判定用 `>=` 且只在每天 6 点结算做，所以「第 N 天 6 点成熟」的作物正好在第 N+1 天 6 点（24 小时后）枯死；补水只清缺水计时，救不回它。
- **骨粉**：每株每个 MC 天最多 `boneMealPerDay` 次（默认 1，0 = 不接受骨粉）；施肥**不立即生效**——只记入待结算层数，在下一次每日结算（次日 6:00）时一次性叠加：每层按 `boneMealProgress` 加进度、且每层至少推进一个可见生长阶段；超限再施直接枯死并在动作栏提示；已成熟作物交回原版处理（不消耗）。叠加发生在结算流程最前段（水源判定之后、当季/过熟判定之前），因此缺水枯死的作物当次吃不到叠加；叠加不受当季与 `minGrowHours` 限制。
- 管辖范围：`CropBlock`、`StemBlock`、`NetherWartBlock`、`CocoaBlock`、`SweetBerryBushBlock` 及其子类。竹子、洞穴藤蔓等 `GrowingPlantHeadBlock` 的 `age` 是「离根距离」而非成熟度，**刻意不接管**。

### 2.3 水源消耗（`enableCropWater`）

- `waterPerSource`（默认 10）：一格水同时能「服务」的耕地数，只供水给离它最近的这么多块（最近优先，平局按 x/z）。排不进前 N 的耕地拿不到水。它只是湿润配额，不代表水量。
- **耗水是整格的**：每天结算时，只要某块被供水的耕地上种着作物（成熟未收也照算），结算完成后这格水就被替换成空气——与种了几株、服务几块耕地无关；供水范围内一块作物都没有则水不消耗。也就是一格水「用一天」，想让它常在就得定期补（用户定案：成熟照常参保，湿润结算只解除缺水计时、不阻止过熟枯死）。
- 缺水（水源耗尽，或该田没被任何水源覆盖）：当次结算不推进成长，并开始计时——计时起点记为「最后一次被供水的结算」（从未被供水过的作物则以首次发现缺水的结算为起点）；此后某次结算距起点已满 `dryWitherDays` 天仍缺水，作物枯死、耕地变回泥土。默认 `dryWitherDays = 1` 即「水被抽走后，到第二天 6 点结算还没补回来就枯死」。被供水的结算会把计时起点刷新到当次，等价于「补水清缺水计时」，但它救不回过熟枯死。
- **水方块被替换成空气**时用 flag 2 写入，不触发邻接更新（避免旁边的水立刻流回来）；重新倒上的水视为全新水源：满格、重新计算服务哪几块耕地。
- **下雨即免耗水**：维度正在下雨时，能看见天空的作物不再抽干自己的供水水源；雨停恢复照常（见 2.5）。
- **雨中生长**：本次降雨连续满 `minRainHours`（默认 12 游戏小时，0 = 关闭）后，没被任何水源覆盖但能看见天空的作物视为有水——照常涨进度、解除缺水计时，不必等积水。
- 豁免是两个**互相独立**的开关，默认都关闭豁免（即一律消耗）：
  - `consumeNaturalWater`（默认 true）：河流、海洋等水构成的生物群系里的水照常被消耗；设为 false 后这些水永不减少（按原版 `#minecraft:is_river`、`#minecraft:is_ocean` 标签判定），鼓励在河边、岸边种植。
  - `consumeInfiniteWater`（默认 true）：原版意义上的「无限水」（四周有两格及以上水源、挖掉会被原版补回来的水）照常被消耗，玩家自建的无限水池与天然湖泊都算；设为 false 后这类水永不减少。
- 只有**静止的水源方块**算水源，流动水不算。原版的「雨水自动湿润耕地」不再参与判定（耕地湿度已由耗水系统接管，见 3.2），但**下雨本身就是合法的水**：露天作物雨中被抽水的配额免除、雨下满 `minRainHours` 后没有水方块也能生长，洼地还会积水——这是设计意图，不是绕过机制（详见 2.5）。
- 水源状态存在独立区块附件 `WaterStates`（仅内存不落盘——水方块在不在，就是水量的「存档」），「服务哪些耕地」是 100 真实 tick 的内存缓存。

### 2.4 显示

- 作物物品 tooltip：显示「成熟需要 X 天」。
- 骨粉 tooltip：显示「每株每天 N 次，次日 6 点结算生效，每次至少长一级」，并提示超量会枯死；`boneMealPerDay = 0` 时改为「不接受骨粉」。
- Jade：作物进度显示为**真实进度百分比**，下方另起一行「施肥：已施肥 / 未施肥」（已施肥 = 有待结算层数，绿色；未施肥灰色；`boneMealPerDay = 0` 时不显示该行）（可选依赖）。

### 2.5 雨天系统（`enableRainWater`）

视觉与原版雨天完全一致，差异只体现在雨强提示与水的行为上，由新类 `RainSystem` 驱动（纯事件 + tick，无 mixin）。

- **触发来源**：状态机用**权威天气标志** `level.getLevelData().isRaining()` 判定开始/结束，所以自然天气循环、`/weather` 指令、其它模组改天气都会进入雨天系统。不要用 `Level.isRaining()`：它在 1.21.1 读的是渲染渐变值 `rainLevel > 0.2`，服务端每 tick 只 ±0.01（雨后 21 tick 才为真、雨停还拖 80 tick），而且整段天气推进被 `dimensionType().hasSkyLight()` 守卫——没有天空光的维度它恒为 false。本模组同样保留 `hasSkyLight()` 作为门槛（下界/末地不参与）。
- **时间口径**：雨天系统的所有时长都按**游戏时钟** `getDayTime()` 计量（1 小时 = 1000 tick、1 天 = 24000 tick），与成长系统的 `minGrowHours`/`dryWitherDays`/`overMatureDays` 同一口径，会随 BetterDays 等模组的时间倍速一起变快变慢。唯一的例外是**处理节奏**：积水推进与找坑每 100 **真实** tick 跑一次（`HANDLING_INTERVAL`，只是 CPU 节流），但推进量取的是这段时间里的游戏时钟增量。
  一场原版降雨长 `RAIN_DURATION = 12000~24000` tick，即 12~24 游戏小时（0.5~1 天），所以 `minRainHours` 上限 24、`*FillDays` 上限 1 天，数值才有意义。
- **与 BetterDays 的天气时钟同步**：原版天气计时器按**真实** tick 递减，BetterDays 只在 `weatherEffect` 生效时把它按倍速同步（`WeatherSleepEffect`；`SLEEPING` = 仅在有人睡觉时同步）。我们改用游戏时钟口径后，必须让一场雨的**长短**也跟游戏时钟走，否则倍速越高越对不上（100 倍速实测：等好几个 MC 天都积不出水）。所以 `SeedSeasonsEvents.onServerAboutToStart → RainSystem.syncWeatherClock()`：检测到 `betterdays` 一起加载且 `enableRainWater` 为真时，把它配置文件里的 `weatherEffect` 文本级改写为 `ALWAYS`（只在值不同时写，不动其它内容，异常静默忽略）。BD 用的是自己的 WhiteNoise 配置、不注册进 FML 的 `ModConfig`，所以内存里的值不会即时刷新，**要重启才对 BD 当次会话生效**。
- **五档雨强**：毛毛细雨、小雨、中雨、大雨、暴雨。开始下雨时按当季概率随机定档，之后在相邻档位间随机升降（不跳档）：每次变化在聊天框提示「雨变大了/雨变小了 - 档名」，雨开始提示「下雨了 - 档名」，雨停不提示；消息（灰色）发给下雨维度里的所有玩家。
- **季节概率**（初始档位按此加权随机，概率表写死在 `RainSystem.SEASON_WEIGHTS`；四季都能出全部五档，只是权重不同，每行合计 100）：
  - 春：毛毛细雨 25% / 小雨 30% / 中雨 22% / 大雨 15% / 暴雨 8%
  - 夏：毛毛细雨 10% / 小雨 20% / 中雨 25% / 大雨 25% / 暴雨 20%
  - 秋：毛毛细雨 22% / 小雨 28% / 中雨 25% / 大雨 15% / 暴雨 10%
  - 冬：毛毛细雨 40% / 小雨 28% / 中雨 18% / 大雨 10% / 暴雨 4%
- **变化节奏**：开始下雨、以及每次雨强变化后，当前档位先**随机锁定 1~5 小时**（写死在代码里，不配置）；稳定期过后每隔 `rainCheckHours`（默认 1 小时）判定一次——约 1/2 不变、1/4 升一档、1/4 降一档，越界不动；一旦变化，新的稳定期再次随机 1~5 小时。
- **下雨即免耗水**：该作物所在位置真的在下雨时，它的供水水源不被抽干；雨停恢复。
- **雨中生长**：本次降雨连续满 `minRainHours`（默认 12 小时，即半天——一场雨的后半段）后，没被水源覆盖但正在淋雨的作物视为有水（涨进度、解除缺水计时）。雨停后若仍没有水，缺水计时从「最后一次被供水（含雨）的结算」重新起算，与水被抽干的口径一致。
- **是否真在下雨**统一用 `Level.isRainingAt(pos)`：静谧四季把这个方法整个 HEAD 替换成季节化判定（`SeasonHooks.isRainingAtHook`），因此**下雪不算降雨**、树冠与屋檐遮挡会被正确排除，温度/季节降水也跟随四季的配置。
- **低洼积水**：
  - 每 100 tick 处理一次：先给已登记的坑推进进度，再对每个**已加载**区块随机抽 1 根地表柱找新坑。
  - 「坑」的判定：本柱 WORLD_SURFACE 第一个空气位，且四邻柱的第一个空气位都比它高（被更高地形围住），该位与其下方都不是水，并且该位置当下真的在下雨（`isRainingAt`，下雪的寒冷生物群系与被遮住的洼地都不积水）。
  - 进度按**当前档位速度**推进：`进度 += 间隔 / (当前档天数 × 24000)`——**雨一变档，积水速度立即跟着变**（毛毛细雨积到一半转暴雨，剩余部分按 0.15 天的速度快速灌满）。
  - 各档填满一坑所需的连续降雨（按天气自己的时钟，1 天 = 24000 tick）：`drizzleFillDays` 1.0 / `lightRainFillDays` 0.75 / `moderateRainFillDays` 0.5 / `heavyRainFillDays` 0.3 / `torrentialFillDays` 0.15。上限取 1 天是因为「进度雨停清零」而一场雨最长就是 24000 tick——再大的数值永远积不满，等于装饰。
  - **进度雨停清零**（纯内存不落盘）：坑必须在单场雨里填满才出水；坑满即在该位置放置一个静止水源——普通水，可被耗水系统抽干、也可直接接进农田供水。每维度最多追踪 4096 个坑（写死上限）。

---

## 3. 实现架构

### 3.1 类清单

| 类 | 职责 |
|---|---|
| `SeedSeasons` | 模组入口：注册配置、两个附件类型、客户端配置界面 |
| `SeedSeasonsConfig` | 全部配置项（23 个）与读写；`ModConfigEvent` 触发重载 |
| `SeedSeasonsEvents` | 游戏事件：指令注册、服务端 tick、区块加载/卸载、方块放置/破坏（即时建档销档）、右键（施肥提示）、服务端启动前（同步 BetterDays 的天气时钟） |
| `SeasonMapping` | 季节列表解析、并集合并进静谧四季、配置版本号、季节掩码快照与实际季节时长读取、`filter` 公共入口 |
| `GrowthDays` | 成熟天数列表解析、未列出作物按季节掩码与 SS 实际季节时长动态推算、写入与移除 |
| `CropLifecycle` | 生命周期核心：每日结算、登记、水耗、枯死、骨粉、客户端进度查询 |
| `CropState` / `CropStates` | 单株状态（进度、时长、干涸计时、待结算施肥层数等）/ 区块级容器 + 附件注册与同步 |
| `WaterSource` / `WaterStates` / `WaterNetwork` | 供水配额缓存 / 容器 / 供水判定、抽干水源 |
| `RainSystem` | 雨天系统：五档雨强状态机（季节概率/随机摆动/聊天提示，全部按 `getDayTime()` 计时）、下雨免耗水与雨中生长判定、低洼积水抽查与灌水、`syncWeatherClock` 纠正 BetterDays 的 `weatherEffect` |
| `client/CropTooltips` | 作物与骨粉的物品 tooltip |
| `client/SeedSeasonsClient` | 配置界面入口 |

### 3.2 mixin 清单

| Mixin | 目标 | 注入 | 说明 |
|---|---|---|---|
| `ModFertilityMixin` | 静谧四季 `ModFertility` | `populate` TAIL 追加合并；`isCrop` RETURN 处 `数据包结果 \|\| 配置结果` | 不能清空 SS 的集合（会抹掉数据包），不能在 HEAD 直接接管 |
| `FarmBlockMixin` | 原版 `FarmBlock` | `isNearWater` HEAD 覆盖；`randomTick` 内 `isRainingAt` 重定向（`require = 0`） | 两态：null = 不干预（耗水关闭时走原版）；true = 耗水开启时恒湿润——耕地不再按原版规则干涸变泥，缺水枯死与耕地变泥改由每日结算负责（见 2.3、第 6 节）。注：耗水开启时 `isNearWater` 恒为 true，原版 `!isNearWater && !isRainingAt` 会短路，所以那条 `isRainingAt` 重定向在耗水开启时不会被调用，它只在耗水关闭时保留「不靠雨湿润」的语义；本模组自己的雨天判定走 `RainSystem`，不受这里影响 |
| `BoneMealItemMixin` | 原版 `BoneMealItem` | `applyBonemeal` HEAD 可取消 | 骨粉汇聚点，且该方法自己负责扣物品，取消后需手动扣 |
| `ServerLevelMixin` | 原版 `ServerLevel` | `tickChunk` 内 `BlockState.randomTick` 重定向 | 抢在 SS 的季节拦截之前接管作物；返回 true 表示已处理、跳过原版成长 |
| `JadeCropProgressMixin` | Jade `CropProgressProvider` | `appendTooltip` HEAD 取消并自行输出 | 用真实进度替换原版「阶段/最大阶段」，文案与配色复刻 Jade 自身 |

### 3.3 数据流

```
服务端：放置/破坏方块 → 即时建档（记种植时刻与作物 id）/ 销档
       骨粉 → onBoneMeal 记待结算层数（当日限次，不立即生效）
       每天 6:00 → dailyTick → 遍历已加载区块 → 扫区块补档 → step() 每株一次（骨粉叠加/成长/水耗/枯死/催熟）
       随机刻 → CropLifecycle.onRandomTick 提前建档 + 跳过原版成长
       雨天 → RainSystem 每 100 tick：找坑/按当前档推进积水；雨强随季节与时间随机变化并提示
       状态存区块附件 CropStates（NBT 落盘）+ 积水进度（内存，雨停清零）
                    ↓ AttachmentType.serializable(...).sync(CODEC)
客户端：同一附件（进区块自动补发） → Jade mixin 读取 → 显示真实百分比 + 施肥状态
```

---

## 4. 开发约束

### 4.1 固定风格（所有自研模组通用）

1. 包名 `com.xulai.<modid>`；modid 全小写无分隔。
2. 构建产物命名 `<modid>-<版本>-<mc版本>-NeoForge.jar`。
3. **代码里不写注释**——解释写在回复/文档里。
4. 配置项：中英双语、可热重载、能在模组列表配置界面里改；生成 TOML 时每项说明为「中文描述块 → 单独一行 `#` → 英文描述块」，**非首项前再加一行独立 `#` 作分隔**，首项不加（文件开头不留孤立 `#`）。
5. 模组未发布，**不写任何向后兼容**：改 NBT 结构/配置键直接改，不做迁移、不读旧键。
6. 顺带修掉上游缺陷；但审查清单先给用户挑，不要默认新版更优。

### 4.2 本工程踩出来的技术约束

1. **mixin 优先混入 mod 类**（静谧四季/Jade 的类不混淆，名字直接匹配）。混入原版类时注意：
   - `@At(target = "Lowner;name(desc)V")` 里的 **owner 必须是字节码里接收者的静态类型**，javac 不会回溯到方法声明类。例：`FarmBlock.randomTick` 里 `isRainingAt` 的 owner 是 `ServerLevel` 而不是声明它的 `Level`。用 `javap -c -p -classpath build/neoForm/neoFormJoined*/steps/recompile/outputs.jar <类>` 直接读调用点 owner。
   - 纯视觉/锦上添花的注入加 `require = 0`，匹配不上就静默跳过而不是崩游戏。
   - 混入可选模组（Jade）用**单独的 `required: false` mixin 配置**，并在 mods.toml 里声明 `type = "optional"`、`side = "CLIENT"`。
   - 本工具链**不需要 refmap**（开发名即可匹配；前提是 owner 写对）。
2. 不要混入 `BlockStateBase.randomTick` 与静谧四季抢同一注入点（它在 HEAD cancel，会互相打断）。
3. 配置列表项一律用 `defineListAllowEmpty`；普通 `defineList` 会把空列表判为「需修正」并还原成默认值。
4. 附件同步用 NeoForge 原生 `AttachmentType.serializable(...).sync(StreamCodec)` + `chunk.syncData(type)`，不要自己写包；进区块会自动补发。
5. 遍历/修改同一条集合时先收集再删除（每日结算里踩过 `ConcurrentModificationException`）。
6. `CropState.lastAge` 用于区分「该生长」与「换了一株/收获重置」——少了它，收获后补种同一格会继承旧进度直接成熟。
7. 改成长/成熟相关配置后，用 `SeasonMapping.configVersion()` 让已种下的作物在下次登记时重算时长（保留进度）。
8. greedyString 参数的 Tab 补全，建议文本必须带上已输入的前缀，否则会替换掉前面已选的季节。
9. 区块迭代不要用 `ChunkMap.getChunks()`（非 public）；用 `ChunkEvent.Load/Unload` 维护「已加载区块」集合，再用 `getChunkNow(x,z)` 取区块。
10. **作物的发现不能依赖随机刻**：随机刻是按 3/4096 的概率抽格子的，加速时间后一株可能几十天抽不到——只靠随机刻建档会让「同一片田里有的长有的不长」。结算时按区块扫一遍补档（先用 `LevelChunkSection.maybeHas(predicate)` 过调色板，含作物的区块才逐格查），成本是每天每区块一次。
11. 跟踪「玩家放置/破坏方块」时注意事件触发面：本版本 `BlockEvent.EntityPlaceEvent` 只在 `ItemStack.useOn → CommonHooks.onPlaceItemIntoWorld` 这条链上触发（发射器等自动放置不触发），且事件在方块**已放下之后**才触发——`getPlacedBlock()` 是新状态，`getState()` 是放置前的旧状态；`BlockEvent.BreakEvent` 由 `ServerPlayerGameMode.destroyBlock → CommonHooks.fireBlockBreak` 触发，覆盖玩家收获。所以结算扫描的兜底不能省。
12. `LevelReader.getHeight`（含 `Level.getHeight`）对未加载区块会同步拉取区块（至少到 HEIGHTMAP 状态）：雨天找坑查四邻柱高度时必须先用 `getChunkNow` 确认邻区块已加载、再走 `chunk.getHeight`，邻区块没加载就放弃该柱，宁可漏一根也不要在 tick 里触发区块加载。
13. **要纠正别的模组的配置时，只做文本级改写并讲清代价**：BetterDays 用的是自己的 WhiteNoise 配置（`technology.roughness.whitenoise.config.WhiteNoiseConfigSpec`），**没有注册进 FML 的 `ModConfig`**，所以 `ModList.getModContainerById("betterdays")` 拿不到可 refresh 的 ModConfig，反射写它的私有 `EnumValue` 又太脆。可行做法是在 `ServerAboutToStartEvent` 里用正则只替换目标那一行（`(?m)^(\s*key\s*=\s*).*$`，保留缩进与 CRLF、值相同就不写盘、异常静默忽略），并明确它**要重启才对该模组当次会话生效**。玩家配置文件是 CRLF：多行 old_string 的编辑会失败，只能逐行替换，改完用脚本核对 CRLF 数量（`grep | cat -A` 会吞 CR，不能拿来判行尾）。

### 4.3 构建约束

- 第三方 jar 放 `local-repo/`（自建 Maven 布局 + 最小 pom），build.gradle 里加 `maven { url = uri("${projectDir}/local-repo") }`。
- `ProcessResources` 必须设 `filteringCharset = 'UTF-8'`，否则 mods.toml 的中文会被平台默认 GBK 吃掉末尾字符。
- `neo_version` 用本地已有缓存（21.1.249）以免下载。
- 不跑 gradle 也能做一次零构建的类型自检：`javac -proc:none -d <临时目录> -cp "<classpath>" $(find src/main/java -name "*.java")`。classpath = `build/neoForm/neoFormJoined*/steps/recompile/outputs.jar` + 缓存里的 `neoforge-<ver>-universal.jar`、`loader-<fmlver>.jar`（含 `net.neoforged.fml.common.*`）、`mixin-0.8.5.jar`、`bus-*.jar`、`brigadier-*.jar`、`slf4j-api`、`logging`、`datafixerupper`、`netty-buffer/common` + `local-repo/` 的静谧四季/GlitchCore/Jade。`client/` 包用到的 `net.neoforged.api.distmarker.Dist` 不在这些 jar 里，自检时排掉 `client` 包或临时放一份桩类即可。
- 若构建报 `Timeout waiting to lock Configuration Cache`，说明有被中断的守护进程占锁：`gradlew --stop` 或加 `--no-configuration-cache`。

---

## 5. 构建与验证

```bash
cd D:\Games\MOD\更好的四季
./gradlew build          # 产物在 build/libs/
```

验证状态（截至本次提交）：

- **已实测**：指令写入配置、配置热重载生效、游戏能正常启动、Tab 补全与「并集累加」修复可用。
- **未实测**：以天为单位的每日 6:00 结算（含水被抽干、`dry` 枯死变泥、`young` 门槛、收获后补种不继承进度、骨粉次日叠加）、非当季枯死/换季催熟/过熟枯死、雨天系统（雨强变化与提示、免耗水、雨中生长、低洼积水）、Jade 真实进度与施肥提示、growthDays 动态推算、数据包并集对第三方适配作物生效。
- 运行时排查入口：启动日志里的 mixin 报错；`javap -p -classpath local-repo/sereneseasons/.../sereneseasons-1.21.1-10.1.0.3.jar sereneseasons.init.ModFertility` 可复核被 shadow 的字段名。

---

## 6. 已知边界

- 只对上面列出的 5 类作物方块生效，其余（竹子、藤蔓、甘蔗、仙人掌等）保持原版。
- 未加载区块不会推进成长；区块重新加载后从存档里的状态继续，不做补偿。
- 非玩家途径出现的作物（发射器、指令、其它模组）没有种植时刻，只能在下次结算被扫描到时以「当时」记为种植时间，因此它们实际要多等一个 `minGrowHours` 才可能涨进度。
- `minGrowHours` 只挡涨进度：种下当天的结算里，这些「没满时长」的作物照样会按「田里有作物」计一次耗水，也照样参与当季/枯死判定。
- 耗水系统开启时，耕地不会被原版规则干涸成泥土（否则缺水地块的作物会在几分钟内被静默顶掉，看起来像"耕地反复湿润变泥"）；耕地变泥只发生在「缺水满 `dryWitherDays` 天、作物枯死」那一步。因此"哪 10 块田在供水"靠作物是否生长与水源水量体现，而不是靠土壤颜色。
- Jade 集成按 15.10.6 的类名/签名编写，升级 Jade 后若方法变动，那份 mixin 会静默失效（`require = 0`），不会崩游戏。
- 无限水的两个豁免开关互相独立：`consumeInfiniteWater=false` 会把天然湖泊一并豁免（湖泊与玩家水池在机制上无法区分），只想豁免河流/海洋应使用 `consumeNaturalWater=false`。
- 雨天系统只在有天空光的维度参与（下界/末地被 `dimensionType().hasSkyLight()` 挡在外面，与原版天气推进的守卫一致）；会下雪的地方不算下雨，因此不积水也不免耗水。睡觉跳过雨天会让状态机直接开始或清零。
- 用 `/weather` 实测时的两个原版前提：**1.21.1 的 `/weather` 写死只作用于主世界**（`source.getServer().overworld()`，在别的维度敲它没效果）；`duration` 参数是天气计时器的 tick 数，而 `weatherEffect = ALWAYS` 生效后这个计时器按游戏时钟递减，所以 `/weather rain 12000` 就是「下满 12 游戏小时」（1/3 倍速下约 30 现实分钟，100 倍速下约 6 现实秒）。没开 ALWAYS 时它按真实 tick 递减，倍速越高、换算成游戏小时就越短。
- 积水候选与进度是内存态：雨停清零、服务器重启清零；区块卸载后其中的坑不推进进度，重新加载后继续。积水出的是普通水源，同样受耗水系统约束。
- 性能上的既有取舍：随机刻回调先做 `instanceof` 判定再查方块状态属性；水源「服务哪几块耕地」的结果缓存 100 真实 tick；每日结算遍历已加载区块（加载/卸载事件维护），每区块先用 `LevelChunkSection.maybeHas` 过一遍调色板、只有含作物的区块才逐格扫描，不用 `ChunkMap.getChunks()`；雨天找坑每 100 tick 每区块只随机抽 1 根地表柱（heightmap 查询，O(1)），积水进度与候选全部在内存，总量有 4096/维度的写死上限。
