# 更好的四季 · seedseasons 开发文档

## 1. 工程概览

| 项 | 值 |
|---|---|
| 仓库 | `D:\Games\MOD\更好的四季`（本文件所在目录） |
| 自研模组 | modid `seedseasons`，包名 `com.xulai.seedseasons`，显示名 `Xulai Seed Seasons` |
| 构建产物 | `build/libs/seedseasons-<mod_version>-<mc_version>-NeoForge.jar` |
| 目标平台 | Minecraft 1.21.1 / NeoForge（`neo_version=21.1.249`，NeoGradle 7.1.38） |
| 硬依赖 | SereneSeasons（静谧四季）10.1.0.3、GlitchCore 2.1.0.2、BetterDays |
| 可选依赖 | Jade 15.10.6（仅客户端；未安装时功能不受影响）、Patchouli 1.21.1-92（种植指南手册）、车万女仆 Touhou Little Maid 1.5.3-neoforge+mc1.21.1（女仆兼容，`compileOnly`；未安装时兼容层完全不加载） |
| 仓库内其它目录 | `BetterDays-1.21.1/`、`SereneSeasons-1.21.1/`、`Jade-1.21-neoforge/` 为**参考源码**（不参与构建）；`TouhouLittleMaid-26.1-1.21/` 同样是参考源码，但因含 80MB 第三方素材已写进 `.gitignore`，**只留本地不入库**；`local-repo/` 是构建用的本地 Maven 仓库 |

模组的作用：在静谧四季（SereneSeasons）基础上叠加玩法——
① 作物适种季节可由配置文件补充与数据包并集；
② 作物成长改为以 MC 天为单位、每天固定时刻统一结算的生命周期；
③ 种植需要消耗水源；
④ 主世界一天总长固定（默认 1 MC 天 = 现实 1 小时），白天/夜晚占比随季节变化（需要 BetterDays 提供时间流速机制）；
⑤ 车万女仆的农场模式会顺手照看这套农田：补水、按额度施肥、反季不播种；
⑥ 露天的炼药锅在雨天会积水，速度按游戏小时配置（默认 6~12 小时积满一锅）。

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
- **西瓜/南瓜藤（`StemBlock`）**：成熟后**不过熟枯死**，改为每天 6 点结算时在藤旁（水平 4 邻、目标为空气且下方是草/泥土）随机挑一个空位放置一个 `MELON` / `PUMPKIN` 果实方块；只有**缺水**（走通用缺水分支）或**换季**（`!fertile` 且随机枯死）才会消失。
- **可可豆（`CocoaBlock`）**：长在原木侧面，`pos.below()` 不是耕地，天然不进入水分判定，**不需要水**，也不会因缺水枯死。
- **骨粉**：每株每个 MC 天最多 `boneMealPerDay` 次（默认 1，0 = 不接受骨粉）；施肥**不立即生效**——只记入待结算层数，在下一次每日结算（次日 6:00）时一次性叠加：每层按 `boneMealProgress` 加进度、且每层至少推进一个可见生长阶段；超限再施直接枯死并在动作栏提示；已成熟作物交回原版处理（不消耗）。叠加发生在结算流程最前段（水源判定之后、当季/过熟判定之前），因此缺水枯死的作物当次吃不到叠加；叠加不受当季与 `minGrowHours` 限制。
- 管辖范围：`CropBlock`、`StemBlock`、`NetherWartBlock`、`CocoaBlock`、`SweetBerryBushBlock` 及其子类。竹子、洞穴藤蔓等 `GrowingPlantHeadBlock` 的 `age` 是「离根距离」而非成熟度，**刻意不接管**。

### 2.3 水源消耗（`enableCropWater`）

- `waterPerSource`（默认 10）：水塘的「服务配额系数」——水塘内每格水能「服务」的耕地数。水塘总服务配额 = `水塘内水格数 × waterPerSource`，按距水塘锚点的距离取前这么多块耕地（最近优先，平局按 x/z）。它既是服务配额，也是耗水折算系数。
- **水塘共享服务**：相邻的水源（4 邻连通）自动归为**一个水塘**，塘内所有水**共同服务**同一批耕地——作物只需属于水塘的服务集合，即视为有水（`servingSource` 返回离它最近的水源）。抽掉部分水后，剩余水在次日结算时**自动重新接管**全部田（配额 = 剩余格数 × `waterPerSource`），只要配额仍 ≥ 作物数，就不会有田断水。
- **按需耗水（每天结算时统一折算）**：只有**被非豁免水塘服务且种着作物**的耕地计入耗水。结算时：
  1. 收集所有「被服务的有作物耕地 → 其服务水源」的登记；
  2. 把非豁免水源按 **4 邻连通**分组（空间水塘），作物跨水源去重；
  3. 每个水塘统计**去重后的服务田数** `cropCount`，需耗水格数 = `ceil(cropCount / waterPerSource)`；
  4. 按「距水塘锚点最近」优先抽这么多格水；水塘里水不够就全抽。
  例：1 格水 + 5 田 → 抽 1；4 格水 + 18 田 → 抽 2 留 2，剩余 2 格水配额 20 ≥ 18，**所有田继续有水**；被豁免水（河海水）服务的田不计入耗水、不抽水（豁免判定见下）。
- 缺水（水源耗尽，或该田没被任何水源覆盖）：当次结算不推进成长，并开始计时——计时起点记为「最后一次被供水的结算」（从未被供水过的作物则以首次发现缺水的结算为起点）；此后某次结算距起点已满 `dryWitherDays` 天仍缺水，作物枯死、耕地变回泥土。默认 `dryWitherDays = 1` 即「水被抽走后，到第二天 6 点结算还没补回来就枯死」。被供水的结算会把计时起点刷新到当次，等价于「补水清缺水计时」，但它救不回过熟枯死。
- **水方块被替换成空气**时用 flag 2 写入，不触发邻接更新（避免旁边的水立刻流回来）；重新倒上的水视为全新水源：满格、重新计算服务哪几块耕地。
- **降雨免旱**：本次降雨连续满 `minRainHours`（默认 12 游戏小时，0 = 关闭）后，没被任何水源覆盖但正在淋雨的作物视为有水——照常涨进度、解除缺水计时（见 2.5）。
- **原版无限水已被禁用**：通过 mixin 让 `FlowingFluid.canConvertToSource` 恒返回 `false`，被抽干的水源不会被原版自动补回，保证耗水结果稳定可预测（否则无限水补回会让服务关系反复横跳，出现"水够却枯死"）。
- **所有水统一消耗**：原版无限水已被禁用（`FlowingFluid.canConvertToSource` 恒 false），玩家倒的水/天然水都是消耗品，**没有任何豁免判定**（无群系配置、无密度判定）。所有水体统一走水塘共享 + 按需耗水。
- 只有**静止的水源方块**算水源，流动水不算。原版的「雨水自动湿润耕地」不再参与判定（耕地湿度已由耗水系统接管，见 3.2），但**下雨本身就是合法的水**：露天作物在雨下满 `minRainHours` 后没有水方块也能生长——这是设计意图，不是绕过机制（详见 2.5）。
- 水源状态存在独立区块附件 `WaterStates`（仅内存不落盘——水方块在不在，就是水量的「存档」），「服务哪些耕地」是 100 真实 tick 的内存缓存。每日耗水的登记（`WaterNetwork.SERVED_CROPS`）也是内存态，结算后即清空。

### 2.4 显示

- 作物物品 tooltip：显示「成熟需要 X 天」。
- 骨粉 tooltip：显示「每株每天 N 次，次日 6 点结算生效，每次至少长一级」，并提示超量会枯死；`boneMealPerDay = 0` 时改为「不接受骨粉」。
- Jade：作物进度显示为**真实进度百分比**，下方另起一行「施肥：已施肥 / 未施肥」（已施肥 = 有待结算层数，绿色；未施肥灰色；`boneMealPerDay = 0` 时不显示该行）（可选依赖）。
- Jade 另两行（耗水/骨粉开启时）：断水且不在雨里的作物显示红色「缺水：待补水」（`CropLifecycle.thirstyAt`，判定与结算同源）；当天已施过肥的作物显示金色「今日已施肥 N/M」，让玩家知道额度已被女仆或自己用掉。

### 2.5 降雨免旱（`enableRainGrowth`）

雨天系统已删除，只保留「降雨免旱」这一项：**本次降雨连续满 `minRainHours` 后，露天作物无需水方块也能生长、不会干旱**。由极简类 `RainWater` 驱动（纯事件 + tick，无 mixin）。

- **触发来源**：用**权威天气标志** `level.getLevelData().isRaining()` 判定开始/结束，所以自然天气循环、`/weather` 指令、其它模组改天气都会生效。不要用 `Level.isRaining()`：它在 1.21.1 读的是渲染渐变值 `rainLevel > 0.2`，服务端每 tick 只 ±0.01（雨后 21 tick 才为真、雨停还拖 80 tick），而且整段天气推进被 `dimensionType().hasSkyLight()` 守卫——没有天空光的维度它恒为 false。本模组同样保留 `hasSkyLight()` 作为门槛（下界/末地不参与）。
- **时间口径**：按**游戏时钟** `getDayTime()` 计量（1 小时 = 1000 tick），与成长系统的 `minGrowHours`/`dryWitherDays`/`overMatureDays` 同一口径，会随 BetterDays 等模组的时间倍速一起变快变慢。`RainWater` 每次 tick 只做 Map 查询与写入，无额外处理节奏。
- **判定**：`RainWater.sufficient(level, pos)` —— 该维度正在下雨 && 已连续下满 `minRainHours`（默认 12 游戏小时，即一场雨的后半段）&& 该位置真的在下雨（`isRainingAt(pos)`，见下）。满足时，`CropLifecycle` 在无水源分支里把它视为有水：照常涨进度、解除缺水计时；雨停后若仍没有水，缺水计时从「最后一次被供水（含雨）的结算」重新起算，与水被抽干的口径一致。
- **是否真在下雨**统一用 `Level.isRainingAt(pos)`：静谧四季把这个方法整个 HEAD 替换成季节化判定（`SeasonHooks.isRainingAtHook`），因此**下雪不算降雨**、树冠与屋檐遮挡会被正确排除，温度/季节降水也跟随四季的配置。
- **无 BetterDays 联动**：不再改写 BetterDays 的 `weatherEffect` 配置，雨长完全跟随原版天气计时器。

### 2.6 季节昼夜时长（`enableSeasonalDayNight`）

让主世界一天的总长固定，但白天/夜晚的占比随季节变化。**需要 BetterDays 提供时间流速机制**（NeoForge 下 BetterDays 通过 `ServerLevel.setDayTimePerTick(float)` 控制每 tick 推进量）。

- **总开关**：`enableSeasonalDayNight`（默认 true）。关闭后完全恢复 BetterDays 默认行为。
- **一天总长**：`mcDayLengthMinutes`（默认 60），即 1 MC 天（24000 tick）= 现实 60 分钟。全天速度由该值和季节白天占比共同决定。
- **季节白天占比**：`springDayPercent` 50 / `summerDayPercent` 65 / `autumnDayPercent` 50 / `winterDayPercent` 35（%）。夜晚自动为剩下的百分比。白天段定义为 `dayTime % 24000 < 12000`，夜晚段为其余。直观效果（默认 1 天 = 60 现实分钟）：冬季白天约 21 分钟、夜晚约 39 分钟；夏季白天约 39 分钟、夜晚约 21 分钟。
- **流速计算**：白天速度 = `10 / (N × p)`，夜晚速度 = `10 / (N × (1-p))`，其中 N = 一天现实分钟数，p = 白天占比（小数）。这样一天总游戏 tick 仍为 24000，现实总长恰为 N 分钟，且白天/夜晚的游戏 tick 数按占比分配。
- **实现**：`mixin/SeasonalDayNightMixin` 用 `@ModifyArg` 注入 `ServerLevel.setDayTimePerTick` 内部对 `ServerLevelData.setDayTimePerTick(F)` 的调用（`index=0`），按当前季节 + 当前时刻替换流速。只主世界生效，其它维度原样放行。
- **睡觉加速**：不干预 BetterDays 的睡觉逻辑（只在 BetterDays 调用 `setDayTimePerTick` 时替换数值；玩家睡觉时 BetterDays 走其它分支，本 mixin 不生效）。

### 2.7 车万女仆兼容（`enableMaidCompat`）

只在本模组一侧实现，女仆 mod 的代码一行不改；耦合面全部走它公开的扩展 API：`@LittleMaidExtension` 标注的 `ILittleMaid` 实现（`maid/SeedSeasonsMaidExtension`），用 `addExtraMaidBrain` 注入 WORK 行为、用 `registerSpecialCropHandler` 注册作物处理器。没装车万女仆时这些类永远不会被加载，配置项也不起作用。

- **只在农场模式生效**：`MaidBrain.registerWorkGoals` 会无条件追加 extra brain 的 WORK 行为（对攻击、挤奶等所有模式都追加），所以每条路径都以 `maid.getTask().getUid() == touhou_little_maid:farm` 为门槛。不用同 uid 覆盖原版 `TaskNormalFarm`：`TaskManager.add` 往 `TASK_INDEX`（List）追加，模式按钮会出现两个「农场」。
- **断水没有方块信号**：耗水开启时 `FarmBlock.isNearWater` 被覆盖成恒 true（`WaterNetwork.hydration`），耕地 MOISTURE 永远钉在 7，原版干涸信号不可用。唯一入口是 `CropLifecycle.thirstyAt(level, cropPos)`（耗水开启 && 作物受本模组管辖 && 基底是耕地 && `WaterNetwork.needsWater` && 不被 `RainWater.sufficient` 免旱）——女仆的自主寻路、女仆的到达执行、Jade 的「缺水：待补水」三处都调它，不允许各自拼条件（早前正是三处各写一遍、条件还不完全等价，已合一）。
- **补水**：只补 `thirstyAt` 为真的作物所在耕地，空田不补。放水位置 = `WaterNetwork.refillSpot`：在耕地 ±4、同层的格子里找**能存住水的空位**（本格空气或可替换、下方实心、四邻不是空气——否则水会流走；`FlowingFluidMixin` 已禁掉原版补源，抽干的坑不会自己回填）。
- **取水**：`WaterNetwork.canDrawFrom` 按**连通池余量**判定——池内源格数（BFS 到 64 格即视为余量充足）减 1 后仍 ≥ `ceil(被服务的有作物田数 / waterPerSource)` 才允许舀。于是河、湖、远离农田的水随便取，单格井和刚够用的水池不动。触发只看「背包里有空桶」（用户定案原话），不再要求她手里没有满水桶，所以她会主动把空桶灌满再回田边。
- **活动范围**：补水、取水、施肥全部限制在 `maid.getRestrictRadius()` 内（沿用 `MaidMoveToBlockTask.getHorizontalSearchRange`），女仆不会为了一桶水跑出工作区；附近没有可取的水源就原地待着，等玩家往她脚下扔水桶（她本来就会捡东西）。
- **顺路优先 + 空闲自主**：`MaidPlotWorkTask`（优先级 6）读女仆当前的 `InitEntities.TARGET_POS`——农场任务带她走到哪格，她就在同一格按 补水 → 施肥 的顺序做事，不碰农场任务的记忆；`MaidIrrigationMoveTask`（优先级 7）继承 `MaidMoveToBlockTask` 的「`WALK_TARGET` 与 `TARGET_POS` 都必须为空」前提，因此只有农场没事可做时她才自己找目标，两者不会抢目标位打转。她自己设的目标做完（或做不到）后**必须自己清空** `TARGET_POS`/`WALK_TARGET`，否则农场移动任务永远不再搜索，直接卡死。**中途关开关也会卡死**（实测前审查发现的坑）：她的脚下还挂着我们的目标时，工作行为被 `enableMaidCompat` 挡住就永远不会去清空——所以现在 `checkExtraStartConditions` 在「兼容已关但我们仍持有认领」时照样放行，`start()` 里不做事、只立即 `release()`（不等她走到那格）。
- **施肥**：动作走 `BoneMealItem.applyBonemeal(stack, level, pos, null)`，正好被本模组的 `BoneMealItemMixin` 截住，与玩家共用同一份 `boneMealUses` 日额度；施前先问 `CropLifecycle.canFertilize`（管辖作物 && 未成熟 && 当季 && 额度未满），所以女仆永远不会触发「超限枯死」。`canFertilize` 开头先 `WaterNetwork.loaded(...)` 早退——它是被 AI 逐格扫描调用的，绝不能在这里同步强载区块（对应第 4 节约束 12）。但额度是共享的：她把当天次数用满后，玩家再右键骨粉照样枯死——惩罚不为谁破例，Jade 的「今日已施肥 N/M」就是让玩家看得见额度没了。
- **反季不种**：`MaidSeasonalPlantHandler` 通过 `SpecialCropManager.addCrop(Blocks.FARMLAND, ...)` 注册，而全站只有 `TaskNormalFarm.canPlant` 用**基底方块**查这张表（收获、种植执行、瓜类/可可/甘蔗查的是作物方块或种子物品），所以接管面恰好只有「能不能种」这一条；命中「本模组管辖的作物且 `ModFertility.isCropFertile` 为假」返回 false，其余原样调用接口默认的 `canPlant`（那份默认实现与原版逐字相同）。`MaidFarmMoveTask.shouldMoveTo` 走同一条判定，于是她连寻路都不会过去。
- **与耗水系统的时序配套**：6:00 结算抽干 → 缺水的作物记 `drySince` → 距它满 `dryWitherDays`（默认 1 天）的那次结算还没水就枯死、耕地变泥。女仆的工作就是在这一整天里把水补回去；`RainWater.sufficient` 为真的露天作物当次结算按有水处理，她也不去补（屋檐、树冠下淋不到雨的田照补）。

### 2.8 炼药锅接雨（`enableCauldronRainFill` / `cauldronFillHours`）

原版机制存在但几乎用不上：`ServerLevel.tickChunk` 每 tick 每区块只有 `randomTickSpeed(3) × 1/48` 的机会做一次降水采样，`getBlockRandomPos(chunkX, 0, chunkZ, 15)` 只给区块内**一个随机列**，`tickPrecipitation` 再把它吸附到 `MOTION_BLOCKING` 高度、处理它**下面那格**；命中后 `CauldronBlock.shouldHandlePrecipitation` 还只有 `nextFloat() < 0.05`。单个锅每推进一层 ≈ `1/(0.0625 × 1/256 × 0.05) ≈ 8.2 万 tick`，三口锅装满要 3 个多小时连续降雨——所以本模组把它改成确定的定时驱动。

- **驱动方式是定时刻（scheduled tick）而不是随机刻**：`mixin/AbstractCauldronBlockMixin` 挂在 `AbstractCauldronBlock` 的 `onPlace`（新放的锅接上链条）与 `tick`（每次定时到达时结算）上，真正的判定与推进都在 `CauldronRain`。随机刻（`randomTick`）不适合做「6~12 小时」这种可保证的时长，因为它本身是概率抽格。
- **时长一律按游戏时钟，并且要轮询校正**：每层期望 `cauldronFillHours / 3` 个游戏小时（1 游戏小时 = 1000 dayTime tick），每锅在 `0.67~1.33` 之间随机，所以默认 9 小时对应 **6~12 游戏小时装满**，一整排锅也不会同刻齐满。排定时时要换算成真实 tick（`真实tick = 游戏tick / level.getDayTimePerTick()`，这个值就是 BetterDays 与本模组 `SeasonalDayNightMixin` 实际写入的流速）。**坑在于流速会在等待期间变化**：昼夜占比本身就让白天/夜晚流速不同（冬天 3.17 vs 1.71，差 1.85 倍），睡觉加速与 `/time` 跳时更是直接把 dayTime 掀过去——只按排定那一刻折算一次会显著偏离游戏时钟。因此唤醒间隔一律夹到 `min(还需的滴数, WAKE_GAME_TICKS=300)`，也就是**最多 18 个游戏分钟醒一次**再看账，偏差被夹在 18 游戏分钟以内且每次醒来都用最新流速重算，自动纠偏。
- **记账是「累计淋雨量」而不是「绝对时点」**：`POTS`（按维度 → `pos.asLong()` → `Pot{readyTicks, lastSeen}`）是唯一真相。每次醒来看两次唤醒之间的 dayTime 增量：在下雨（`level.isRainingAt(pos.above())`）就把它从 `readyTicks` 里扣掉；`readyTicks <= 0` 就推进一层，并把 `readyTicks` 重置成一个新的 `step`——**一次唤醒最多推进一层**，所以 `/time` 大跳或睡觉跳过整天也只是补一层，多余的额度不攒（偏保守，不会白送）。锅满 3 层时 `fillOneStep` 不动作，但 `readyTicks` 同样重置为一个 `step`，等于不攒银行（否则玩家舀走一层会立刻白得一层）。`LevelChunkTicks.schedule` 本身按 **(type, pos) 去重**（重复排程被静默丢弃），所以「钟乳石滴水已经在给这口锅排定时」也不会让接水变快——提前唤醒时扣的仍只是真实经历过的 dayTime。
- **老存档的锅**：`onPlace` 不会在区块加载时触发，所以 `ChunkEvent.Load → CauldronRain.onChunkLoad` 用 `LevelChunkSection.maybeHas` 过调色板，只展开含炼药锅的 section 逐个补上链条（沿用作物补档那套做法，绝不在 tick 里强载区块）。
- **与其它系统的边界**：锅里的水不是水源方块，`WaterNetwork` 一律不认（不供水、不进水塘配额、不算可舀水源）；女仆既不从锅里舀也不往锅里倒；`isRainingAt` 被静谧四季接管，因此**下雪不算降水**，雪原/冬天的锅走原版细雪逻辑，本模组不插手；屋檐与树冠下不积水。
- 关掉 `enableCauldronRainFill` 会清掉该维度的记账并停止重排，链条自然断掉；再打开需要重新放锅或重进区块（区块加载会重新接上）。

---

## 3. 实现架构

### 3.1 类清单

| 类 | 职责 |
|---|---|
| `SeedSeasons` | 模组入口：注册配置、两个附件类型、客户端配置界面 |
| `SeedSeasonsConfig` | 全部配置项（20 个）与读写；`ModConfigEvent` 触发重载 |
| `SeedSeasonsEvents` | 游戏事件：指令注册、服务端 tick、区块加载/卸载、方块放置/破坏（即时建档销档）、右键（施肥提示） |
| `SeasonMapping` | 季节列表解析、并集合并进静谧四季、配置版本号、季节掩码快照与实际季节时长读取、`filter` 公共入口 |
| `GrowthDays` | 成熟天数列表解析、未列出作物按季节掩码与 SS 实际季节时长动态推算、写入与移除 |
| `CropLifecycle` | 生命周期核心：每日结算、登记、水耗、枯死、骨粉、客户端进度查询，以及女仆/Jade 用的 `thirstyAt`、`canFertilize`、`boneMealUsesToday` 查询 |
| `CropState` / `CropStates` | 单株状态（进度、时长、干涸计时、待结算施肥层数等）/ 区块级容器 + 附件注册与同步 |
| `WaterSource` / `WaterStates` / `WaterNetwork` | 供水配额缓存（含锚点）/ 容器 / 水塘共享服务判定、按需耗水折算（水塘分组 + 服务田数统计 + 抽水）；对外查询 `needsWater`、`refillSpot`、`canHoldWater`、`canDrawFrom`——读取路径的参数收 `Level` 而不是 `ServerLevel`，好让客户端的 Jade 提示复用同一套判定 |
| `RainWater` | 降雨免旱：按维度记录降雨开始时刻（`getDayTime()`），判定是否已连续下满 `minRainHours` 且该位置正在淋雨 |
| `CauldronRain` | 炼药锅接雨：`POTS` 按维度记每口锅「还差多少游戏 tick 的淋雨量」，每次唤醒把真实经历的 dayTime 增量在雨里扣掉、扣干就推进一层（空锅→1→2→3）；唤醒间隔夹在 ≤`WAKE_GAME_TICKS`(300 游戏 tick) 并用 `getDayTimePerTick()` 折算成真实 tick，区块加载时扫调色板给老锅接上链条 |
| `maid/SeedSeasonsMaidExtension` | `@LittleMaidExtension` 的 `ILittleMaid`：注册 extra brain 与 FARMLAND 作物处理器（没装女仆时这个类永远不会被加载） |
| `maid/MaidIrrigationMoveTask` | 空闲自主寻路：在女仆活动半径内找「能存水的空位 / 可舀的水源 / 待施肥作物」，直接复用女仆的 `MaidMoveToBlockTask` 扫描、可达性与范围判定 |
| `maid/MaidPlotWorkTask` | 到达目标格后执行：耕地 → 补水 + 施肥，空位 → 放水，水源 → 舀水；自己设的目标做完即清空 `TARGET_POS`/`WALK_TARGET` |
| `maid/MaidSeasonalPlantHandler` | `ISpecialCropHandler`：反季的管辖作物不许种，其余原样交给接口默认实现 |
| `maid/MaidBags`、`maid/MaidCompat` | 背包内水桶/铁桶/骨粉的取出与回写；「农场模式 + 兼容开关」门槛 |
| `client/CropTooltips` | 作物与骨粉的物品 tooltip |
| `client/SeedSeasonsClient` | 配置界面入口 |

### 3.2 mixin 清单

| Mixin | 目标 | 注入 | 说明 |
|---|---|---|---|
| `ModFertilityMixin` | 静谧四季 `ModFertility` | `populate` TAIL 追加合并；`isCrop` RETURN 处 `数据包结果 \|\| 配置结果` | 不能清空 SS 的集合（会抹掉数据包），不能在 HEAD 直接接管 |
| `FarmBlockMixin` | 原版 `FarmBlock` | `isNearWater` HEAD 覆盖；`randomTick` 内 `isRainingAt` 重定向（`require = 0`） | 两态：null = 不干预（耗水关闭时走原版）；true = 耗水开启时恒湿润——耕地不再按原版规则干涸变泥，缺水枯死与耕地变泥改由每日结算负责（见 2.3、第 6 节）。注：耗水开启时 `isNearWater` 恒为 true，原版 `!isNearWater && !isRainingAt` 会短路，所以那条 `isRainingAt` 重定向在耗水开启时不会被调用，它只在耗水关闭时保留「不靠雨湿润」的语义；本模组自己的降雨免旱判定走 `RainWater`，不受这里影响 |
| `BoneMealItemMixin` | 原版 `BoneMealItem` | `applyBonemeal` HEAD 可取消 | 骨粉汇聚点，且该方法自己负责扣物品，取消后需手动扣 |
| `ServerLevelMixin` | 原版 `ServerLevel` | `tickChunk` 内 `BlockState.randomTick` 重定向 | 抢在 SS 的季节拦截之前接管作物；返回 true 表示已处理、跳过原版成长 |
| `FlowingFluidMixin` | 原版 `FlowingFluid` | `canConvertToSource(FluidState, Level, BlockPos)` HEAD 恒返回 false | 禁用原版无限水自动补源，保证耗水系统抽干的水不会被补回 |
| `AbstractCauldronBlockMixin` | 原版 `AbstractCauldronBlock` | `onPlace` 与 `tick` 的 TAIL 追加 | 炼药锅接雨：`onPlace` 给新放的锅接上定时链条，`tick`（定时到达）交给 `CauldronRain.advance` 判定并推进。目标类与两个方法名都已在 patched 源码里核对存在（`onPlace` 第 106 行、`tick` 第 88 行）；`require` 用默认 1，改名会直接报错而不是静默失效 |
| `SeasonalDayNightMixin` | 原版 `ServerLevel` | `setDayTimePerTick` 内 `ServerLevelData.setDayTimePerTick(F)` 调用点 `@ModifyArg index=0` | 按当前季节+时刻替换时间流速，实现季节化昼夜时长；只主世界生效 |
| `JadeCropProgressMixin` | Jade `CropProgressProvider` | `appendTooltip` HEAD 取消并自行输出 | 用真实进度替换原版「阶段/最大阶段」，文案与配色复刻 Jade 自身 |

### 3.3 数据流

```
服务端：放置/破坏方块 → 即时建档（记种植时刻与作物 id）/ 销档
       骨粉 → onBoneMeal 记待结算层数（当日限次，不立即生效）
       每天 6:00 → dailyTick → 遍历已加载区块 → 扫区块补档 → step() 每株一次（骨粉叠加/成长/水耗/枯死/催熟）
                    水耗 → registerServedCrop 登记（水源 → 被服务作物）→ drainForDay 按水塘折算并抽水
       随机刻 → CropLifecycle.onRandomTick 提前建档 + 跳过原版成长
       雨天 → RainWater 记录每维度降雨开始时刻；无水源作物在 `RainWater.sufficient` 满足时按有水处理
       状态存区块附件 CropStates（NBT 落盘）
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
12. `LevelReader.getHeight`（含 `Level.getHeight`）对未加载区块会同步拉取区块（至少到 HEIGHTMAP 状态）：需要查四邻柱高度时，务必先用 `getChunkNow` 确认邻区块已加载、再走 `chunk.getHeight`，邻区块没加载就放弃该柱，宁可漏一根也不要在 tick 里触发区块加载。（此约束源自已删除的雨天积水找坑，如未来引入类似的地形扫描逻辑仍需遵守。）
13. **`Level.getFluidState` / `Level.getBlockState` 对未加载区块同样会触发同步区块加载**（`getChunkAt → ServerChunkCache.getChunk → managedBlock`，在服务端 tick 线程上会**自锁假死**——实测河边种植导致整服卡死，动物不动、区块不生成）。任何在 tick 中按坐标查流体/方块状态的代码，服务端必须先用 `getChunkNow(x>>4, z>>4)` 确认区块已加载，未加载就跳过；客户端则先判 `hasChunkAt`。`WaterNetwork` 的 `isWaterSource` / `sourceAt` / `drain` / 耕地扫描均已加此保护。
14. **要纠正别的模组的配置时，只做文本级改写并讲清代价**（历史经验，当前代码已无此场景）：BetterDays 用的是自己的 WhiteNoise 配置（`technology.roughness.whitenoise.config.WhiteNoiseConfigSpec`），**没有注册进 FML 的 `ModConfig`**，所以 `ModList.getModContainerById("betterdays")` 拿不到可 refresh 的 ModConfig，反射写它的私有 `EnumValue` 又太脆。可行做法是在 `ServerAboutToStartEvent` 里用正则只替换目标那一行（`(?m)^(\s*key\s*=\s*).*$`，保留缩进与 CRLF、值相同就不写盘、异常静默忽略），并明确它**要重启才对该模组当次会话生效**。玩家配置文件是 CRLF：多行 old_string 的编辑会失败，只能逐行替换，改完用脚本核对 CRLF 数量（`grep | cat -A` 会吞 CR，不能拿来判行尾）。

### 4.3 构建约束

- 第三方 jar 放 `local-repo/`（自建 Maven 布局 + 最小 pom），build.gradle 里加 `maven { url = uri("${projectDir}/local-repo") }`。
- 车万女仆按同一布局放 `local-repo/touhou_little_maid/touhou_little_maid-1.21.1/1.5.3/`，只作 `compileOnly`；`neoforge.mods.toml` 里**不给它写 `[[dependencies]]`**——版本对不上最多是女仆行为不生效，不该让启动失败。
- `ProcessResources` 必须设 `filteringCharset = 'UTF-8'`，否则 mods.toml 的中文会被平台默认 GBK 吃掉末尾字符。
- `neo_version` 用本地已有缓存（21.1.249）以免下载。
- 不跑 gradle 也能做一次零构建的类型自检：`javac -proc:none -encoding UTF-8 -d <临时目录> -cp "<classpath>" -sourcepath "<桩类目录>;src/main/java" $(find src/main/java -name "*.java")`。classpath = `build/neoForm/neoFormJoined*/steps/recompile/outputs.jar` + 缓存里的 `neoforge-<ver>-universal.jar`、`loader-<fmlver>.jar`（含 `net.neoforged.fml.common.*`）、`mixin-0.8.5.jar`、`bus-*.jar`、`brigadier-*.jar`、`slf4j-api`、`logging`、`datafixerupper`、`netty-buffer/common` + `local-repo/` 的静谧四季/GlitchCore/Jade（三个 jar 的 Maven 目录是 `local-repo/<mod>/<mod>-<mcver>/<version>/<file>.jar`，直接写 `local-repo/<mod>/<mod>/<ver>/...` 会静默找不到包）。`client/` 包用到的 `net.neoforged.api.distmarker.Dist`/`OnlyIn` 不在这些 jar 里，放两个桩类（`enum Dist { CLIENT, DEDICATED_SERVER; isClient(); }`）到 `-sourcepath` 里就能连 `client/` 一起编过。注意 loader 必须取 **4.0.x**（21.1.249 配套），缓存里的 10.x/11.x/12.x 是新 MC 的，`FMLEnvironment.dist` 字段签名对不上会误报找不到符号。
- 若构建报 `Timeout waiting to lock Configuration Cache`，说明有被中断的守护进程占锁：`gradlew --stop` 或加 `--no-configuration-cache`。

---

## 5. 构建与验证

```bash
cd D:\Games\MOD\更好的四季
./gradlew build          # 产物在 build/libs/
```

验证状态（截至本次提交）：

- **已实测**：指令写入配置、配置热重载生效、游戏能正常启动、Tab 补全与「并集累加」修复可用。
- **未实测**：以天为单位的每日 6:00 结算（含按需耗水折算与抽水、`dry` 枯死变泥、`young` 门槛、收获后补种不继承进度、骨粉次日叠加）、非当季枯死/换季催熟/过熟枯死、降雨免旱（`minRainHours` 计时与解除缺水）、Jade 真实进度与施肥提示、growthDays 动态推算、数据包并集对第三方适配作物生效、季节昼夜时长（`enableSeasonalDayNight` 对 BetterDays 流速的替换）。
- **未实测**：炼药锅接雨（`enableCauldronRainFill`/`cauldronFillHours`）——本轮只做静态类型自检与源码核对，需要确认：①空锅淋雨约 3 个游戏小时变 1 层水、约 6~12 小时到 3 层；②屋檐/树冠下、雪原（降水是雪）不积；③满 3 层后不再变；④用桶或玻璃瓶取走一层后仍按剩余时长继续接；⑤钟乳石滴水正下方的锅**不会**因为滴水定时而加速积水；⑥区块卸载重装后链条仍在（`onChunkLoad` 补链），重启服务器后按新的一层时长重新计时；⑦关掉开关后锅只按原版极慢速度积水；⑧跨昼夜切换、季节变化、睡觉跳天与 `/time add` 之后，积满总时长仍应落在 6~12 游戏小时附近（唤醒间隔已夹到 ≤18 游戏分钟，偏差应小于此），且跳一次天最多补一层。
- **女仆兼容实测结果（2026-11-05 21:30 那轮，用户跑客户端 + 一次性分层探针回传日志）**：
  - **已核实**：扩展类被女仆反射加载（10 个女仆各注册一组行为）；模式门槛正确——农场模式的女仆 `compat=true`，钓鱼/空闲模式的女仆 `compat=false` 完全不动田；自主找到「能存水的空位」并判定缺水 → `pour placed=true`，目标格变成 `water[level=0]`；水桶倒完后自动切成找水源 → `draw water=true`，形成「舀→倒→再舀」闭环；施肥确实扣物品（骨粉 100000→99999、99995→99994）；空田（`above=air`）不补；反季拦截在同坐标上出现过 `blocked=true` 与 `blocked=false` 的翻转（随季节变）。
  - **仍未确认**：③ 次日 6:00 结算重新吸到水并恢复进度（那轮日志里没有 `settlement` 行，跨天没跑到）；④ 雨天停工；⑤ 「额度已满不再施」「玩家随后手施仍超限枯死」「Jade 显示 今日已施肥 1/1」；⑥ 单格井不许舀 vs 紧贴农田的大湖可以舀的对照；⑦ `enableMaidCompat=false` 后不卡死（`TARGET_POS` 清空的兜底路径）。**注意**：审查后 ② 与 ⑦ 的实现都改了（取水条件放宽为「有空桶就去装」；关开关时工作行为立即交还认领并清空记忆），所以那两条的旧日志证据不再适用，需要重跑；断水判定也已合一到 `CropLifecycle.thirstyAt`。
- **部署踩过的坑**：`mods/` 里同时放同 modid + 同版本的两份 jar 时，FML 只认其中一份，另一份静默失效——上一轮「女仆什么都不做」的真因就是 10 月 2 日的旧 jar 和新 jar 并存。**部署前先 `ls mods/ | grep -i <modid>` 查重复**，改名成 `.disabled-<日期>` 停用而不是删除。
- 运行时排查入口：启动日志里的 mixin 报错；`javap -p -classpath local-repo/sereneseasons/.../sereneseasons-1.21.1-10.1.0.3.jar sereneseasons.init.ModFertility` 可复核被 shadow 的字段名。

---

## 6. 已知边界

- 只对上面列出的 5 类作物方块生效，其余（竹子、藤蔓、甘蔗、仙人掌等）保持原版。
- 未加载区块不会推进成长；区块重新加载后从存档里的状态继续，不做补偿。
- 非玩家途径出现的作物（发射器、指令、其它模组）没有种植时刻，只能在下次结算被扫描到时以「当时」记为种植时间，因此它们实际要多等一个 `minGrowHours` 才可能涨进度。
- `minGrowHours` 只挡涨进度：种下当天的结算里，这些「没满时长」的作物照样会按「田里有作物」计一次耗水，也照样参与当季/枯死判定。
- 耗水系统开启时，耕地不会被原版规则干涸成泥土（否则缺水地块的作物会在几分钟内被静默顶掉，看起来像"耕地反复湿润变泥"）；耕地变泥只发生在「缺水满 `dryWitherDays` 天、作物枯死」那一步。因此"哪几块田在供水"靠作物是否生长与水源格数体现，而不是靠土壤颜色。
- 按需耗水的「水塘」是每日结算时对**登记过的水源**做 4 邻连通分组，锚点与成员缓存 100 真实 tick（`WaterSource.poolAnchor`）；每组独立折算，互不影响。同一块田若被多个水塘同时覆盖，只登记到最近的水源，不会重复计数。抽水后剩余水在次日缓存过期重算时自动接管全部田（配额 = 剩余格数 × `waterPerSource`），直到配额不足才按距离淘汰。
- Jade 集成按 15.10.6 的类名/签名编写，升级 Jade 后若方法变动，那份 mixin 会静默失效（`require = 0`），不会崩游戏。
- **原版无限水已被禁用**：`FlowingFluidMixin` 让 `canConvertToSource` 恒返回 false，任何被抽干/破坏的水源都不会被原版自动补回。因此不再有"无限水"概念，`consumeInfiniteWater` 与 `consumeNaturalWater` 两个豁免开关也一并删除——所有水（含河流、海洋）统一走水塘共享配额与按需耗水。
- **季节昼夜时长**：只主世界生效，其它维度（下界/末地）原样放行；只影响时间推进速度，不改变天空亮度曲线本身（白天/夜晚的视觉由 dayTime 驱动，速度改变使各时段现实时长按占比分配）；依赖 BetterDays 的 `setDayTimePerTick` 调用，未安装 BetterDays 时该 mixin 无调用者、自然不生效；睡觉加速等 BetterDays 其它逻辑不受影响。
- 降雨免旱只在有天空光的维度参与（下界/末地被 `dimensionType().hasSkyLight()` 挡在外面，与原版天气推进的守卫一致）；会下雪的地方不算下雨，因此不触发免旱。睡觉跳过雨天会让降雨计时清零。
- 用 `/weather` 实测时的两个原版前提：**1.21.1 的 `/weather` 写死只作用于主世界**（`source.getServer().overworld()`，在别的维度敲它没效果）；`duration` 参数是天气计时器的 tick 数，按真实 tick 递减——`/weather rain 12000` 就是「下 12000 真实 tick」，换算成游戏小时会随时间倍速变化。
- 降雨免旱的计时是内存态：雨停清零、服务器重启清零。降雨开始时刻按维度记录在 `RainWater` 的 Map 里，不落盘。
- 性能上的既有取舍：随机刻回调先做 `instanceof` 判定再查方块状态属性；水源「服务哪几块耕地」的结果缓存 100 真实 tick；每日结算遍历已加载区块（加载/卸载事件维护），每区块先用 `LevelChunkSection.maybeHas` 过一遍调色板、只有含作物的区块才逐格扫描，不用 `ChunkMap.getChunks()`。
- **女仆兼容的既有边界**：
  - 客户端的「缺水：待补水」在正在淋雨时直接抑制（`RainWater` 的 `minRainHours` 计时只存在服务端内存，客户端读不到），所以刚开始下雨的那半小时里可能仍显示缺水。
  - 女仆自己不会准备物资：水桶、铁桶、骨粉都要玩家放进她背包，或扔在地上让她捡（`MaidRequestItemEvent` 只有额外容器类模组才会响应），她也不会去箱子里拿。
  - 补水是「一天一趟」的节奏：6:00 结算抽干之后她才会去找水，所以刚过 6:00 的那段时间田确实是干的，只要当天补上就不会枯死。
  - 放水/舀水直接 `setBlock`（女仆没有 `UseOnContext` 通路，仿 `MaidMilkTask` 手改背包），声音用 `BUCKET_EMPTY` / `BUCKET_FILL`，没有玩家倒水的额外粒子；她可能正站在那个坑里，于是被一格水淹一下——一格深不会淹死（女仆自带换气行为）。
  - `enableCropWater` 关 → 补水/取水整体失效（没水可耗也就无需补）；`enableCropLifecycle` 或 `boneMealPerDay = 0` → 施肥失效；反季不种只跟 `enableMaidCompat`。
  - 接管 `FARMLAND` 的 `canPlant` 意味着**别的模组种在原版耕地上的作物也过我们这一层**，判定只针对 `CropLifecycle.isManaged` 的作物、其余原样委托接口默认实现。若别的模组也注册 `FARMLAND` 处理器，`SpecialCropManager` 是 Map、后注册者覆盖，模组间顺序不保证——真遇到就得改成显式分工。
- **炼药锅接雨的既有边界**：
  - 推进与否只看 `POTS` 这本内存账（`readyTicks` = 还差多少游戏 tick 的淋雨量），**重启服务器账就清零**：那时锅还在，但会重新等一整层时长（约 `cauldronFillHours/3` 游戏小时），不会白送一层。锅被拆走/搬走后旧条目不会主动回收（没有 `onRemove` 钩子），残留量是每口历史锅一个小对象，可接受。
  - 同一格同一方块的定时被 `LevelChunkTicks` 按 (type, pos) 去重，所以我们的排程可能被**别人已排好的更早定时**顶掉（例如钟乳石滴水）——不会加速也不会停摆：唤醒时扣的是两次唤醒之间真实经历的 dayTime。反过来，空锅变成水锅后方块类型换了，旧的 `CAULDRON` 定时不再影响新链条。
  - 满 3 层的锅仍会每隔 ≤18 游戏分钟醒一次，推不动也把 `readyTicks` 重置成一个新的层时长（不攒银行，避免玩家舀走一层后白得一层）；代价是一次方块查询，换来的是"取走水之后不需要任何额外事件就能继续接"。
  - 只处理空锅与水锅：细雪锅（雪原降水）与熔岩锅完全交回原版，`isRainingAt` 被静谧四季接管后下雪本来就不算降雨。
  - 锅水是"锅里的水"：`WaterNetwork.isWaterSource` 只认静止水源方块，所以接满的锅不供水、不进配额、也不能被女仆舀；要进灌溉系统仍得倒成方块水（锅 + 玻璃瓶是独立的取水路线，与耗水机制互不影响）。
