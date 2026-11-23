# 商业时代2：新纪元 (TycoonCraft²: The New Era)

**简体中文** · [English](README.en.md)

商业时代2：新纪元（`tyc2`）是一个面向 Minecraft 的商业经营与宏观经济模组。模组把金属铸币与银行、村庄大宗贸易、宏观物价波动以及村庄自动扩建串成一条完整的经营线，在纯原版环境中就能独立游玩，不需要任何前置模组。

如果存档里同时安装了 [工业时代2：新纪元（`ic2`）](https://github.com/spectrum527/IndustrialCraft2-The-New-Era) 或 [物流时代2：新纪元（`lc2`）](https://github.com/spectrum527/LogisticsCraft2-The-New-Era)，联动功能会自动启用：工业铸币机接入 EU 电网并支持 IC2 升级，`1x` 货币自动统一为 `ic2:coin`，村庄贸易池加入合金、铱碎片和 IC2 农林特产，合作枢纽也兼容 LC2 的区块加载升级与自动货运。

## 下载与安装

按当前 Minecraft 版本和加载器挑好对应的 JAR 文件，放进游戏实例的 `mods/` 目录即可。多人联机需要客户端和服务端同时安装。

[v1.0.0-pre.1 预发布说明](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/tag/v1.0.0-pre.1) · [SHA-256 校验文件](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/SHA256SUMS.txt) · [四端实机联测报告](docs/FIELD_TEST_REPORT.md) · [设计规格文档](docs/DESIGN.md)

| Minecraft | 加载器 | Java | 下载 |
| --- | --- | ---: | --- |
| 1.19.2 | Forge 43.5.0 | 17 | [Forge 1.19.2 JAR](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/tyc2-forge-1.19.2-1.0.0-pre.1.jar) |
| 1.21.1 | NeoForge 21.1.265 | 21 | [NeoForge 1.21.1 JAR](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/tyc2-neoforge-1.21.1-1.0.0-pre.1.jar) |
| 26.1.2 | NeoForge 26.1.2.128 | 25 | [NeoForge 26.1.2 JAR](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/tyc2-neoforge-26.1.2-1.0.0-pre.1.jar) |
| 26.2 | NeoForge 26.2.0.95 | 25 | [NeoForge 26.2 JAR](https://github.com/Alexme9125/TycoonCraft2-The-New-Era/releases/download/v1.0.0-pre.1/tyc2-neoforge-26.2-1.0.0-pre.1.jar) |

所有联动模组都按需安装，未安装时自动跳过，不会影响启动。

- **工业与物流**：[工业时代2：新纪元（`ic2` v1.0.5+）](https://github.com/spectrum527/IndustrialCraft2-The-New-Era)、[物流时代2：新纪元（`lc2` v1.1.0+）](https://github.com/spectrum527/LogisticsCraft2-The-New-Era)
- **季节与农业**：静谧四季（Serene Seasons）、节气（Ecliptic Seasons）、农夫乐事（Farmer's Delight）、森罗物语：厨房（Kaleidoscope Cookery）
- **界面与配方查询**：The One Probe、Jade、JEI

## 新手上手教程

### 铸造第一批启动资金

模组提供 `1x` 到 `1000x` 共 9 档面额（`1x / 5x / 10x / 20x / 50x / 100x / 200x / 500x / 1000x`）。起步阶段先用纯原版材料合一台 **原始铸币机（`tyc2:primitive_mint_machine`）**，只需要铁锭、铜锭、石砖、活塞和熔炉，不涉及任何工业零件。

把煤炭、木炭或木板这类熔炉燃料放进下方燃料格，上方两格放进铜锭、铁锭、金锭或绿宝石，机器就会开始铸币。原始铸币机无需接电，但不支持升级槽，耗时是工业版的 `1.5` 倍，只能铸造 `1x` 到 `100x` 的纯原版货币。

有了电力基础设施后，可以把原始铸币机升级成 **工业铸币机（`tyc2:mint_machine`）**，合成配方会按是否安装 IC2 自动切换。

- 存档装了 IC2 时，升级配方自动使用 IC2 零件（`ic2:machine`、`ic2:circuit`、`ic2:electric_motor` 和 `ic2:iron_plate`）。
- 存档没装 IC2 时，升级配方自动换成高阶原版材料，方便搭配其他 FE 科技模组。

工业铸币机完全靠电运行，不烧煤炭，支持 IC2 EU 电网（基础缓存 `16,000 EU`，LV `32 EU/t`）、通用 FE（`1 EU = 4 FE`），槽位 3 也能放 IC2 电池或 FE 电池放电。它以 `1.0` 倍速度铸造全部 `1x` 到 `1000x` 货币与各类模组金属（自动识别 `#c:ingots/*` 和 `#forge:ingots/*` 标签），右侧 4 个升级槽兼容 IC2 常用机器升级（超频、高压、储能以及弹出和抽入），槽位 4 还能顺带给通讯终端充电。

如果安装了 IC2，`1x` 货币会自动统一输出为 `ic2:coin`，`ic2:coin` 与 `tyc2:coin_1` 全服 1:1 互认。默认配置下，IC2 金属成型机切割铁壳制币的旧配方会被关闭（可在 `config/tyc2/general.json` 中改回），让所有新发货币都经过铸币机并计入宏观货币总量。

### 存钱、建商会与给终端充电

用铁锭、金锭、玻璃板、箱子和红石即可合成 **工业 ATM 机（`tyc2:atm_machine`）**。打开界面可以把硬币逐堆或一键全存入银行账户，也可以按指定面额取款，或者输入任意金额让机器按最优面额自动找零，存取和换钞都没有手续费。

每位玩家默认拥有一个个人账户。多人联机想合资经营，可以在 ATM 里创建商会共享账户并生成邀请码，把邀请码发给队友就能共同存取，平时在界面里一键切换个人或商会结算。ATM 还支持向任意玩家或商会直接转账，并保留最近 `32` 条流水。

下一步合成手持 **商业通讯终端（`tyc2:comm_terminal`）**，最大电量 `20,000 EU`（`80,000 FE`）。把它放进 ATM 右侧的充电格就能补电。ATM 的充电位同时兼容 IC2 EU、FE 和煤炭红石燃料兜底，纯原版玩家也能随时给终端充电。装了 IC2 后，终端还能直接放进各级储电箱（BatBox 到 MFSU）或充电座里充电。

### 找村长签约，建立合作枢纽

带着刚铸好的货币去任意自然村庄。只要村里有原版村庄钟（`minecraft:bell`），且半径 `48` 格内至少有 `2` 名村民，系统会自动选出一名成年村民，头顶挂上金色 `[村长]` 称号。村里只有 0 到 1 名村民时不会产生村长；村长意外阵亡，剩下村民足够 2 人就会立刻重选。

准备好默认签约物资（`100 IC` 现金或银行余额、`32` 个任意原木、`16` 个铁锭和 `32` 份基础食物），右键村长打开谈判界面并点下签约按钮即可完成合作。

1. 村庄中的钟变成 **村庄合作枢纽（`tyc2:cooperation_hub`）**。枢纽爆炸抗性为 `3,600,000` 且无法普通挖掘，只有拥有者、商会管理员或 OP 能在设置页无损打包迁移或解除合作。
2. 村里普通村民不再打开原版绿宝石交易界面，全村改用工业货币在合作枢纽，只接受大宗订单。
3. 枢纽立即刷新第一批 1 级收购单和出售单。就算以后发生袭击导致村民全部阵亡，合作数据也不会丢失，订单会暂时冻结，补回至少 2 名村民后自动重选村长恢复运转。

### 做买卖、接管道与远程自动化

打开合作枢纽可以看到村庄发布的出售单（你花钱向村里进货）和收购单（你把货交给村里赚钱）。在枢纽前既可以用现金格交易，也能直接扣银行账户。

合作枢纽内置 `1,000` 格分页仓库（支持按名称搜索），刻意拆成两个互不串货的 `500` 格区域，接漏斗、管道或 LC2 站点时不会把刚送进去的货又吸走。

- **交付收货区（0–499 格）**：外部管道、漏斗、IC2 抽入升级以及 LC2 智能港口和货运基站**只能输入**。村庄收购单和扩建建材都从这里交货。
- **采购发货区（500–999 格）**：从村庄买到的物资自动落进这里，外部管道、漏斗、IC2 弹出升级和 LC2 站点**只能抽出**。

在任意收购单或扩建单上打开 **自动履约（Auto-Fulfill）**，枢纽每 `40 tick` 会自动检查交付收货区，货齐就扣货打款进你的银行账户。

拿充好电的 **商业通讯终端** 潜行右键合作枢纽，即可绑定这座村庄（单台终端最多绑定 `16` 座村庄）。以后在世界任何角落右键终端，都能切换村庄并查看仓库余量和物价走势；远程银行买卖每笔消耗 `50 EU`，发起村庄升级或扩建每次消耗 `100 EU`，也能顺手开关自动履约。远程结算和订单刷新都支持跨未加载区块虚拟运行；如果你希望管道、LC2 运输船或无人机在你离开后继续装卸货，把 `lc2:chunk_loading_upgrade` 插进枢纽的 2 个升级槽，就能维持周围 `3×3` 区块常驻加载。

### 村庄升级、物价波动、季节套利与扩建

#### 村庄等级（1–10 级）

在枢纽的升级页同时达到人口、累计交易额和村庄余额三项要求就能升级，升级会花掉一笔村庄资金。低等级村庄（1–3 级）主要卖农产品、纺织品、皮革和怪物掉落，收购煤炭、矿石与基础金属锭；高等级村庄（7–10 级）反过来收购熟食与精致菜肴，出售高级附魔书和合金，升到 10 级后还会放出铱碎片（未装 IC2 时为远古残骸等稀有物资）。村庄开放的商品梯队默认低于玩家或商会当前的工业等级，防止直接跳过核心科技阶段。

#### 通胀与通缩

全服物价随宏观 CPI 实时浮动，CPI 由全服货币总量 `M` 与近期商品流通量 `Q` 共同决定，再叠加村庄现金储备、单品供需、季节和群系折扣（最终倍率限定在 `0.25` 到 `4.00` 倍）。只顾着大量铸币却不交易会把 CPI 推高，引发通货膨胀；频繁贸易配合村庄升级费、扩建开支和每日市政维护回笼货币，则会把物价拉回通缩区间。枢纽与终端内嵌了 `48` 个采样点的 CPI 走势折线图，随时能看清行情。

#### 季节套利与农业联动

安装静谧四季或节气后，农业村庄在丰收季会以 `0.75` 倍低价和双倍数量大量出售当季作物；进入反季或冬季后，村里停售这些作物，转而挂出 `1.45` 到 `1.75` 倍的高价收购单，你可以趁丰收囤仓、冬季出货赚差价。安装农夫乐事、森罗物语：厨房或 IC2 后，对应农作物、树脂与料理会自动进入目录，未安装的模组自动忽略。

#### 村庄扩建与防误拆

合作村庄会定期发布建材收购单。交齐木材、石头、沙子等建材后，村庄按自身生物群系风格（平原、沙漠、热带草原、针叶林、雪原）以每 `3 tick` 放 `2` 格的速度自底向上施工，逐步修建路面与路灯、农田、养殖场、民居和多层小楼；水边或高阶村庄还会留出适配 LC2 港口和无人机基站的广场。施工会避开所有玩家放过的方块、任意方块实体（箱子、机器、管道和 IC2/LC2 设备）以及已有建筑和道路，新房完工后会自动吸引新村民入住。如果想暂时停工或限制范围，在枢纽设置里可以随时暂停扩建，也能把扩建半径调在 `10` 到 `72` 格之间。

## 常用指令与自定义扩展

游戏内提供 7 条管理与查询指令，后四条需要 OP 权限。

- `/tyc2 status`：查看全局 CPI、货币总量、季节与最近村庄状态
- `/tyc2 cpi`：查看当前物价指数与通胀状态
- `/tyc2 season`：查看当前识别到的季节
- `/tyc2 reload`：重载外部配置文件与数据表
- `/tyc2 mint <数量>`：调试注入或扣减货币统计
- `/tyc2 expand [项目类型]`：手动触发附近合作村庄扩建
- `/tyc2 levelup`：手动测试附近合作村庄升级

整合包作者和模组开发者可以通过数据包、外部配置和 Java API 三层接口扩展内容。

1. **数据包 JSON（支持 `/reload` 热重载）**：在 `data/<命名空间>/tyc2/` 下放置 `goods/*.json`、`mint_recipes/*.json`、`seasons/*.json` 或 `levels/*.json`，分别扩展贸易目录、铸币配方（支持 `#ingots/*` 标签）、作物季节与升级门槛。
2. **外部配置文件夹（首次启动自动生成）**：编辑 `config/tyc2/general.json`（开关 IC2 压币拦截、谈判门槛、CPI 护栏、刷新与扩建间隔等），或直接修改 `custom_goods.json`、`custom_mint_recipes.json` 后执行 `/tyc2 reload`。
3. **公共 Java API**：调用 `com.tycooncraft.tyc2.api.TyC2RegistryApi`，在代码中注册自定义商品、铸币配方、季节提供器与工业等级判定器。

## 从源码构建教程

本仓库同时维护 **Forge 1.19.2** 与 **NeoForge 1.21.1 / 26.1.2 / 26.2** 四个版本。按下面四步就能在本地全部打出来。

### 准备编译环境

| 工具 | 所需版本 | 用途 |
| --- | --- | --- |
| JDK 17 | OpenJDK / Liberica 17 | 编译并重混淆 Forge 1.19.2 |
| JDK 21 | OpenJDK 21 | 编译纯 Java 公共层、运行 JUnit 5 单测、构建 NeoForge 1.21.1 |
| JDK 25 | OpenJDK 25 | 构建 NeoForge 26.1.2 与 NeoForge 26.2 |
| Python 3 | 3.10+ | 生成多版本资源文件、运行静态打包审计（重绘纹理需额外安装 Pillow） |

### 放入编译期 IC2 参考 JAR

虽然游戏运行时完全不需要 IC2，但编译可选联动桥接代码时需要 IC2 v1.0.5 的四个 JAR 作为 `compileOnly` 参考。请从 [IC2: The New Era v1.0.5 发布页](https://github.com/spectrum527/IndustrialCraft2-The-New-Era/releases/tag/v1.0.5) 下载对应版本的 4 个文件，放到仓库根目录的 `.ic2-deps/` 文件夹下并保持如下文件名。

```text
.ic2-deps/
├── ic2-forge-1.19.2-1.0.5.jar
├── ic2-neoforge-1.21.1-1.0.5.jar
├── ic2-neoforge-26.1.2-1.0.5.jar
└── ic2-neoforge-26.2-1.0.5.jar
```

### 一键编译全部四个版本

在仓库根目录执行下面两条命令（Windows 把 `./gradlew` 换成 `gradlew.bat`，把 `./forge-1.19.2/gradlew` 换成 `forge-1.19.2\gradlew.bat`）。

```sh
# 1. 编译纯 Java 公共核心、运行 31 项单元测试，并打包 NeoForge 1.21.1 / 26.1.2 / 26.2
./gradlew :checkCommonImports :test:test :neoforge:1.21.1:jar :neoforge:26.1.2:jar :neoforge:26.2:jar

# 2. 使用 JDK 17 编译并重混淆打包 Forge 1.19.2（按本机 JDK 17 路径设置 JAVA_HOME）
JAVA_HOME=/path/to/jdk-17 ./forge-1.19.2/gradlew -p forge-1.19.2 build reobfJar
```

编译完成后，四个发行 JAR 分别落在各自模块的 `build/libs` 目录。

- Forge 1.19.2：`forge-1.19.2/build/libs/tyc2-forge-1.19.2-1.0.0-pre.1.jar`
- NeoForge 1.21.1：`src/neoforge/1.21.1/build/libs/tyc2-neoforge-1.21.1-1.0.0-pre.1.jar`
- NeoForge 26.1.2：`src/neoforge/26.1.2/build/libs/tyc2-neoforge-26.1.2-1.0.0-pre.1.jar`
- NeoForge 26.2：`src/neoforge/26.2/build/libs/tyc2-neoforge-26.2-1.0.0-pre.1.jar`

### 运行打包审计与实机测试（开发者可选）

打包完成后可以跑一遍静态审计，确认四个 JAR 资源完整、没有调试文件泄漏，也没有对 IC2 的非隔离硬引用。

```sh
python3 src/qa/audit_jars.py
```

如需运行完整的纯原版与 IC2/LC2 实机服务端 QA，先将对应版本的 LC2 v1.1.0 JAR 放进 `.ic2-deps/`（或用 `-Plc2Jar=<路径>` 指定），再执行下面命令。

```sh
# NeoForge（将 1.21.1 替换为 26.1.2 或 26.2 可测对应版本）
./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:1.21.1:tyc2QaAll

# Forge 1.19.2
JAVA_HOME=/path/to/jdk-17 ./forge-1.19.2/gradlew -p forge-1.19.2 runTyc2QaAll
```

## 许可证

- 源代码采用 [GPL-3.0-only](LICENSE) 开源许可证。
- 原创像素美术与 GUI 图集采用 CC BY-SA 4.0 许可证。
