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
- 条目逐条容错：坏 id / 空条目 / 无有效季节整条忽略并记 WARN；季节词拼错只丢那个词；注册表里查不到的 id 不进集合。
- 指令（OP 2 级、取主手物品）：`/seedseasons add <季节...>`（同一作物重复执行取并集累加季节）、`/seedseasons remove`（删该 id 的条目）。

### 2.2 作物生命周期（`enableCropLifecycle`）

- **结算时机**：每天 `dayTime` 跨过 0（= 早上 6:00）时，对**已加载区块**里的所有作物统一结算一次，每株进度 `+1/成熟天数`。原版随机刻不再推进成长（随机刻只负责登记新作物、识别收获/换株，并跳过原版成长）。
- **成熟天数**：`growthDays` 按作物配置（默认值按季节推算：一个季节 = 3 子季 × 8 天 = 24 天 → 单季作物 12 天两熟、跨 2 季及以上 24 天一熟）；未列出的用 `defaultGrowthDays`（默认 24）。每株种下时按 `growthDayVariance`（默认 ±1 天）roll 出各自总时长，所以同批作物不会同日成熟。改配置后已种下的作物会在下次登记时按新配置重算时长（保留进度）。
- **进度是 0~1 的连续值**，方块年龄 = `floor(进度 × 最大年龄)`。
- **非当季**：每天结算时若不合时令，按 `witherChance` 枯死（直接消失，同四季的「摧毁」）。
- **快成熟换季**：进度 ≥ 75%（写死）且换季后不合时令时，按 `nearMatureChance` 直接催熟；判定与枯死一样只发生在每天结算那一刻，不是换季瞬间。
- **过熟**：成熟后 `overMatureDays` 天未收获则枯死（0 = 关闭）。
- **骨粉**：每株每个 MC 天最多 `boneMealPerDay` 次（默认 1，0 = 不接受骨粉），每次至少推进一个可见生长阶段（配置 `boneMealProgress` 为进度下限）；超限再施直接枯死并在动作栏提示；已成熟作物交回原版处理（不消耗）。
- 管辖范围：`CropBlock`、`StemBlock`、`NetherWartBlock`、`CocoaBlock`、`SweetBerryBushBlock` 及其子类。竹子、洞穴藤蔓等 `GrowingPlantHeadBlock` 的 `age` 是「离根距离」而非成熟度，**刻意不接管**。

### 2.3 水源消耗（`enableCropWater`）

- `waterPerSource`（默认 10）：一格水的容量 = 它同时能养活的耕地数，只湿润离它最近的这么多块（最近优先，平局按 x/z）。排不进前 N 的耕地不供水。
- 每块**种了作物**的耕地每天扣 1 点水；空着的耕地不消耗。10 块田一天抽干一格水，单块十天。
- 缺水（水源耗尽，或该田没被任何水源覆盖）：当次结算不推进成长并开始计时；`dryWitherDays` 天后枯死、耕地变回泥土。
- 补水：拿水桶右键水源即回满（非创造换成空桶）。
- 只有**静止的水源方块**算水源，流动水不算。雨天不再自动湿润耕地（否则雨天会绕过整套机制）。
- 水源状态存在独立区块附件 `WaterStates`，「服务哪些耕地」是 20 真实 tick 的内存缓存。

### 2.4 显示

- 作物物品 tooltip：显示「成熟需要 X 天」。
- 骨粉 tooltip：显示「每株每天 N 次，每次至少长一级」，并提示超量会枯死；`boneMealPerDay = 0` 时改为「不接受骨粉」。
- Jade：作物进度显示为**真实进度百分比**（可选依赖）。

---

## 3. 实现架构

### 3.1 类清单

| 类 | 职责 |
|---|---|
| `SeedSeasons` | 模组入口：注册配置、两个附件类型、客户端配置界面 |
| `SeedSeasonsConfig` | 全部配置项（13 个）与读写；`ModConfigEvent` 触发重载 |
| `SeedSeasonsEvents` | 游戏事件：指令注册、服务端 tick、区块加载/卸载、右键（补水/施肥提示） |
| `SeasonMapping` | 季节列表解析、并集合并进静谧四季、配置版本号、`filter` 公共入口 |
| `GrowthDays` | 成熟天数列表解析、按作物/按方块查询、写入与移除 |
| `CropLifecycle` | 生命周期核心：每日结算、登记、水耗、枯死、骨粉、客户端进度查询 |
| `CropState` / `CropStates` | 单株状态（进度、时长、干涸计时等）/ 区块级容器 + 附件注册与同步 |
| `WaterSource` / `WaterStates` / `WaterNetwork` | 水源状态 / 容器 / 供水判定、扣除、补水 |
| `client/CropTooltips` | 作物与骨粉的物品 tooltip |
| `client/SeedSeasonsClient` | 配置界面入口 |

### 3.2 mixin 清单

| Mixin | 目标 | 注入 | 说明 |
|---|---|---|---|
| `ModFertilityMixin` | 静谧四季 `ModFertility` | `populate` TAIL 追加合并；`isCrop` RETURN 处 `数据包结果 \|\| 配置结果` | 不能清空 SS 的集合（会抹掉数据包），不能在 HEAD 直接接管 |
| `FarmBlockMixin` | 原版 `FarmBlock` | `isNearWater` HEAD 三态覆盖；`randomTick` 内 `isRainingAt` 重定向（`require = 0`） | 三态：null = 不干预、true = 被水源覆盖（含空水源的宽限）、false = 未覆盖交给原版干涸变泥 |
| `BoneMealItemMixin` | 原版 `BoneMealItem` | `applyBonemeal` HEAD 可取消 | 骨粉汇聚点，且该方法自己负责扣物品，取消后需手动扣 |
| `ServerLevelMixin` | 原版 `ServerLevel` | `tickChunk` 内 `BlockState.randomTick` 重定向 | 抢在 SS 的季节拦截之前接管作物；返回 true 表示已处理、跳过原版成长 |
| `JadeCropProgressMixin` | Jade `CropProgressProvider` | `appendTooltip` HEAD 取消并自行输出 | 用真实进度替换原版「阶段/最大阶段」，文案与配色复刻 Jade 自身 |

### 3.3 数据流

```
服务端：随机刻 → CropLifecycle.onRandomTick 只登记
       每天 6:00 → dailyTick → 遍历被跟踪的区块 → step() 每株一次（成长/水耗/枯死/催熟）
       状态存区块附件 CropStates（NBT 落盘）
                    ↓ AttachmentType.serializable(...).sync(CODEC)
客户端：同一附件（进区块自动补发） → Jade mixin 读取 → 显示真实百分比
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
9. 区块迭代不要用 `ChunkMap.getChunks()`（非 public）；只跟踪有数据的区块，用 `getChunkNow(x,z)` 取已加载区块。

### 4.3 构建约束

- 第三方 jar 放 `local-repo/`（自建 Maven 布局 + 最小 pom），build.gradle 里加 `maven { url = uri("${projectDir}/local-repo") }`。
- `ProcessResources` 必须设 `filteringCharset = 'UTF-8'`，否则 mods.toml 的中文会被平台默认 GBK 吃掉末尾字符。
- `neo_version` 用本地已有缓存（21.1.249）以免下载。
- 若构建报 `Timeout waiting to lock Configuration Cache`，说明有被中断的守护进程占锁：`gradlew --stop` 或加 `--no-configuration-cache`。

---

## 5. 构建与验证

```bash
cd D:\Games\MOD\更好的四季
./gradlew build          # 产物在 build/libs/
```

验证状态（截至本次提交）：

- **已实测**：指令写入配置、配置热重载生效、游戏能正常启动、Tab 补全与「并集累加」修复可用。
- **未实测**：以天为单位的每日 6:00 结算、非当季枯死/换季催熟/过熟枯死、水源整套逻辑（10 块田上限、每日 1 点水耗、缺水枯死变泥、水桶补水）、Jade 真实进度显示、数据包并集对第三方适配作物生效。
- 运行时排查入口：启动日志里的 mixin 报错；`javap -p -classpath local-repo/sereneseasons/.../sereneseasons-1.21.1-10.1.0.3.jar sereneseasons.init.ModFertility` 可复核被 shadow 的字段名。

---

## 6. 已知边界

- 只对上面列出的 5 类作物方块生效，其余（竹子、藤蔓、甘蔗、仙人掌等）保持原版。
- 未加载区块不会推进成长；区块重新加载后从存档里的状态继续，不做补偿。
- 土壤湿润度由原版规则显示（附近有水就深色），「哪 10 块田真正在供水」通过作物是否生长体现，缺水一天后耕地会被变为泥土。
- Jade 集成按 15.10.6 的类名/签名编写，升级 Jade 后若方法变动，那份 mixin 会静默失效（`require = 0`），不会崩游戏。
