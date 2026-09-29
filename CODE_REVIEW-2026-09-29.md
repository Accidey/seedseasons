# seedseasons 代码审查报告（2026-09-29）

对照 `DEVELOPMENT.md` 逐条审查 `src/main/java/com/xulai/seedseasons` 全部 18 个类、2 份 mixin 配置、语言文件、构建脚本，并用仓库内 SereneSeasons 10.1.0.3 / Jade 15.10.6 参考源码核对了 mixin 目标签名。

## 总体结论

代码与文档**高度一致**：三层玩法（季节并集、按天生命周期、水源消耗）的核心机制、全部 16 个配置项、默认成熟天数的季节推算、mixin 注入点与容错约定、日志格式、命名与构建约束均逐条落实。发现 **2 个行为与文档不符的实质问题**（耗水计量、缺水计时）和 4 处文档/资源残留。

---

## 一、核对一致的部分

| 文档章节 | 核对结果 |
|---|---|
| 3.1 类清单 | 18 个类全部存在，职责相符 |
| 3.2 mixin 清单 | 5 个 mixin 的目标类、注入点、require 约定全部一致；`ModFertilityMixin` 的 6 个 @Shadow 字段名与 SS 源码逐字吻合；`JadeCropProgressMixin` 方法签名与 Jade 源码吻合，文案复刻 `addMaturityTooltip` |
| 4.2.2 注入冲突 | SS 在 `BlockStateBase.randomTick` HEAD cancel；本模组改注入 `ServerLevel.tickChunk` 的 INVOKE 点，先于 SS，规避正确 |
| 2.1 作物季节 | 并集语义（populate TAIL 只 addAll、isCrop RETURN 取或）、`;` 分隔、物品↔方块等价、逐条容错 WARN、指令 OP2/主手/add 并集累加/remove、Tab 补全带已输入前缀 |
| 2.2 生命周期 | 跨 dayTime 0 结算、进度 +1/天数、1e-9 吸附为 1.0、年龄 = floor(进度×最大年龄)、±1 天浮动、configVersion 重算保留进度、放置/破坏即时建档销档、换作物重置、minGrowHours 只挡涨进度（耗水/当季/枯死判定照常）、75% 催熟写死 0.75、过熟 `>=` 判定（N 天成熟 N+1 天枯）、骨粉每天限次/超限枯死+动作栏提示/至少一级/0 不接受/成熟交回原版、管辖 5 类作物方块、结算日志格式逐字一致 |
| 2.3 水源（部分） | waterPerSource 最近优先、平局按 x/z；抽干用 flag 2 写空气；只认静止水源；雨天不湿润（isRainingAt 重定向）；consumeNaturalWater / consumeInfiniteWater 两开关互相独立，判定逻辑与文档一致；状态存附件 + 内存缓存 |
| 2.4 显示 | 作物/骨粉 tooltip、Jade 真实进度百分比，与文档一致 |
| 默认 growthDays | 11 条默认值与 SS 内置标签推算**全部吻合**（wheat 24=夏+秋、carrot 24=春+秋、potato 12=仅春、beetroot 12=仅秋、nether_wart 24=全年、sweet_berries 24=春+冬、melon/pumpkin/torchflower/pitcher/cocoa 12=单季） |
| 4.1 风格 | 包名/modid/产物命名正确；**代码中确实无注释**；配置中英双语、`defineListAllowEmpty`、TOML 注释块格式（首项无前导分隔）符合约定 |
| 4.3 构建 | `filteringCharset = 'UTF-8'`、local-repo 仓库、neo_version=21.1.249、依赖版本号全部一致 |
| 配置项数量 | 16 个，与文档一致 |

## 二、发现的问题

### P1-1 耗水没有「计量」：文档描述的机制在代码中不存在

- **文档 2.3**：「每块种了作物的耕地每天扣 1 点水……10 块田一天抽干一格水，**单块十天**」——即水源有 waterPerSource 点水量，按田数逐日扣减。
- **代码实际**：`WaterSource` 没有任何水量字段；`CropLifecycle.step` 只要某块田找到供水水源，就把该水源加入 `DRAINED`，结算末尾 `WaterNetwork.drain` 直接把水方块替换成空气。**只要供水范围内种着任意一株作物，这格水当天就耗尽**，与有几块田无关（1 块田和 10 块田都是 1 天耗干）。
- `waterPerSource` 在代码里只承担「同时养活几块耕地」的湿润配额，不承担水量容量。
- `waterPerSource` 的**配置注释**描述的恰是代码行为（「只要这些耕地里种着任意一株作物……这格水就会被替换成空气」）。
- 旁证：语言文件里的死键 `seedseasons.message.water_refilled`（「水源已补满（%s 格）」，代码中无任何引用）与文档「拿水桶右键水源即回满（非创造换成空桶）」的未实现交互，共同指向**早期版本有「水量计数 + 补水交互」，后简化为「有作物即整格耗尽」，文档 2.3 未同步**。

**影响**：核心玩法节奏与文档差 10 倍——按文档玩家应「每 10 天补一次水」，实际必须「每 10 个 MC 天内每天结算前保证有水，否则次日全枯」。

### P1-2 缺水计时提前一天，且对「湿润过」与「从未湿润」的作物行为不一致

- **文档 2.3 / dryWitherDays 配置注释**：「缺水当次结算不推进成长并**开始计时**，dryWitherDays 天后枯死」。
- **代码**：有水结算时无条件 `record.setDrySince(now)`（把计时起点记成「最后一次有水的结算」），缺水时判定 `now - drySince >= dryWitherDays × 24000`。
- 后果（默认 dryWitherDays=1）：
  - 之前一直有水的作物：水源被抽干后**第一次缺水结算就枯死**（`now - drySince` 已 = 1 天），玩家没有「缺水当天停止生长、次日 6 点前补水救命」的窗口——与 `dryWitherDays` 自己的配置注释矛盾（但与 `waterPerSource` 注释「到第二天 6 点前没补回来就会枯死」碰巧一致）。
  - 从未有水的作物（drySince 初始 ≤ 0）：缺水当次才开始计时，行为**符合**文档。
- 若按文档语义修复：有水时应 `record.setDrySince(-1L)`（清计时），缺水时才开始计时。三处描述（文档 2.3、dryWitherDays 注释、waterPerSource 注释）两种口径，需要先定夺哪个是设计意图。

### P2-1 死翻译键 / 未实现的补水交互

`seedseasons.message.water_refilled`（中英两份 lang 均有）在代码中无引用；「水桶右键补水」逻辑不存在（当前机制下补水 = 玩家重新倒一桶水，属原版行为）。建议删除死键并修正文档 2.3 对应句子。

### P3 文档与代码的小出入（建议改文档）

1. **3.2 FarmBlockMixin「三态」描述过时**：文档说 null/true/false 三态（false = 未覆盖交给原版干涸变泥）；实际 `WaterNetwork.hydration` 只返回 null 或 TRUE（耗水开启即全程湿润，耕地永不按原版规则干涸）——这与文档第 6 节「耕地不会被原版规则干涸成泥土」一致，3.2 是旧设计残留。
2. **缓存时长自相矛盾**：2.3 说「20 真实 tick」，第 6 节说「100 真实 tick」；代码 `CACHE_TICKS = 100`，2.3 是旧值。
3. **漏了 growth 指令**：2.1 只列 add/remove；代码与配置注释中还有 `/seedseasons growth <天数>` 与 `/seedseasons growth remove`（设置成熟天数）。

### 低风险观察

- 静态 `LAST_DAYS` / `LOADED_CHUNKS` / `DRAINED` 在单机退出重进后不清理：经推演无实际危害（残留区块经 `getChunkNow` 返回 null 被跳过），仅不优雅。
- `seedseasons.mixins.json` 的 `compatibilityLevel` 为 JAVA_17（项目是 Java 21）：Mixin 常规做法，无碍。

## 三、处理建议

1. 先定夺 P1-1 的设计意图：要「按田数计量、单块田 10 天」就给 `WaterSource` 加水量并在 `drain` 前扣减；要「有作物即耗尽」就更新文档 2.3 与 `dryWitherDays` 注释。
2. 按定夺结果统一缺水计时口径（P1-2），推荐改为有水时 `setDrySince(-1)`。
3. 清理 `water_refilled` 死键；同步文档 2.3、3.2，补充 growth 指令说明。

## 四、处理记录（同日）

- **P1-1 / P1-2 定案**：经确认，当前代码行为即设计意图——耗水是整格的（供水范围内有种作物的耕地，结算后整格水变空气），缺水计时以「最后一次被供水的结算」为起点、有水结算刷新起点。**不改代码，改文档**。
- DEVELOPMENT.md 已同步：
  - 2.1 补充 `/seedseasons growth <天数>` / `growth remove` 指令；
  - 2.3 重写为「整格耗水 + 重新倒水即补水 + 最后一次供水结算起算缺水计时」，删除「每田每天扣 1 点、单块田 10 天」与「水桶右键补水」的旧描述，缓存 20 tick 更正为 100 tick；
  - 3.1 `WaterNetwork` 职责描述改为「供水配额缓存 / 供水判定、抽干水源」；
  - 3.2 `FarmBlockMixin` 由「三态覆盖」改为实际的两态描述（null = 不干预、true = 恒湿润）。
- 已删除 `seedseasons.message.water_refilled` 死翻译键（zh_cn / en_us）。
