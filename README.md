# Lapis-BuildingPlan

一个 Minecraft 订阅制建材商店插件，采用类似 AI Token 的额度体系来管理玩家获取建材的权限和数量。

## 为什么做 Lapis-BuildingPlan？

AI 领域有 Token Plan（如 Codex、Claude、Opencode GO ），**付费 → 获得额度 → 按量消耗**。但 Minecraft 建材商店一直停留在"直接花钱买"的模式：

- 要么一次买断无限量，服务器经济迅速崩溃
- 要么每颗方块定价，OP 天天调价格表
- 要么按组定价，大玩家刷爆库存小玩家买不起

Lapis-BuildingPlan 把 Token Plan 的模式搬进 Minecraft：**订阅套餐 → 获得多维度额度 → 按组消耗**。

## 与 AI Token Plan 的对比

| 维度 | AI Token Plan（如 Opencode GO ） | Lapis-BuildingPlan |
|------|---------------------------|-------------|
| **付费模式** | 月订阅固定费用 | 月订阅（一次性物品抵扣） |
| **额度维度** | 三重额度：5h 爆发 + 日额度 + 周额度 | 三重额度：5h 爆发 + 日额度 + 周额度 |
| **额度耗尽** | 等待下次重置并用完即停 | 日额未用完 → 转入积攒池（Pool），周重置 |
| **套餐分档** | 不同挡位 | Lite / Standard / Pro / Ultra |

## 核心优势

### 1. 积攒池（Pool）— 告别"不用就亏"

AI Token Plan 最大痛点之一：**5H/每日额度用不完就清零**。Lapis-BuildingPlan 的 Pool 机制让每日未用完的额度自动转入积攒池（有上限），玩家可以囤积额度应对大型建筑项目。

```
日额度 3000 → 用掉 2000 → 剩余 1000 → 自动转入积攒池（假设上限 3000）
```

### 2. 建材分类定价，匹配建筑成本（可自由配置）

按建材稀有度分 5 档，每整组（64个）消耗不同点数：

| 分类 | 点数/组 | 举例 |
|------|---------|------|
| 散装建材 | 2 点 | 树叶、泥土、沙子 |
| 基础石材 | 6 点 | 石头、深板岩、黑石 |
| 基础木材 | 6 点 | 原木、木板 |
| 加工建材 | 14 点 | 混凝土、玻璃、陶瓦 |
| 稀有建材 | 28 点 | 紫珀块、黑曜石、铜块 |

### 3. 本地优先，零依赖

- 内置 H2 数据库，无需 MySQL/Redis
- GUI 使用原生 Bukkit Inventory API 实现，无第三方 GUI 库（不影响其他插件 GUI）
- 单文件部署，即装即用
- Folia 兼容，支持现代 Minecraft 服务端架构
- PlaceholderAPI 变量支持（可选）

### 4. 可视化额度监控

商店 GUI 用工具耐久条直观显示三种额度的剩余比例：

> 铁镐 → 5h额度 | 金镐 → 日额度 | 钻石镐 → 周额度

满耐久 = 额度充足，耐久越低 = 消耗越多。

## 套餐方案（可自由配置）

| 套餐 | 典型价格 | 5h 额度 | 日额度 | 周额度 | 积攒上限 |
|------|---------|---------|--------|--------|---------|
| Lite | 8 钻石块 | 200 | 1,000 | 7,000 | 1,000 |
| Standard | 20 钻石块 + 6 下界合金锭 | 600 | 3,000 | 21,000 | 3,000 |
| Pro | 40 钻石块 + 12 下界合金锭 + 2 下界合金块 | 2,000 | 10,000 | 70,000 | 10,000 |
| Ultra | 80 钻石块 + 24 下界合金锭 + 4 下界合金块 | 5,000 | 25,000 | 175,000 | 25,000 |

## 技术栈

- **语言**：Kotlin 2.4 (JVM 25)
- **平台**：Paper 26.2 (Folia 兼容)
- **数据库**：H2 (嵌入式，MySQL 兼容模式)
- **GUI**：原生 Bukkit Inventory API（`InventoryHolder` 严格作用域，不干扰其他插件）
- **构建**：Gradle + ShadowJar
- **可选依赖**：PlaceholderAPI

## 快速开始

1. 将构建好的 jar 放入 `plugins/` 目录
2. 重启服务器
3. 编辑 `plugins/Lapis-BuildingPlan/config.yml` 自定义套餐和价格
4. 玩家使用 `/bp subscribe` 打开订阅 GUI，`/bp shop` 打开建材商店

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/bp shop` | 打开建材商店 | `buildingplan.shop` |
| `/bp subscribe` | 打开订阅 GUI | `buildingplan.subscribe` |
| `/bp admin give <玩家> <点数>` | 给予补偿额度 | `buildingplan.admin` |
| `/bp admin reload` | 重载配置 | `buildingplan.admin` |

## 对外 API

其他插件可以直接读写玩家的 BuildingPlan 额度，把"建材额度"当成服务器里的一种通用货币。

### 接入方式

```kotlin
// 方式一：静态入口，插件未启用时返回 null
val api = mc506lw.buildingPlan.BuildingPlan.getAPI() ?: return

// 方式二：Bukkit 服务管理器
val api = Bukkit.getServicesManager().load(BuildingPlanAPI::class.java) ?: return
```

编译期依赖（jar 未发布到远程仓库，用本地文件即可）：

```kotlin
dependencies {
    compileOnly(files("libs/BuildingPlan-1.5.jar"))
}
```

并在自己的 `plugin.yml` 里声明 `softdepend: [BuildingPlan]`，保证加载顺序。

### 查询

| 方法 | 说明 |
|------|------|
| `isLoaded(uuid)` | 额度数据是否已加载到内存 |
| `getTier(uuid)` | 当前套餐名（如 `lite`），无有效订阅返回 null |
| `hasActiveSubscription(uuid)` | 是否有未过期的订阅 |
| `getQuota(uuid)` | 完整快照 `QuotaSnapshot`，无有效订阅返回 null |
| `getAvailable(uuid)` | 当前可用点数 |
| `canAfford(uuid, points)` | 是否买得起 |
| `getRemainingDays(uuid)` / `getExpireTime(uuid)` | 剩余天数 / 到期时间 |
| `getPointsPerStack(material)` | 该材料每整组价格，不在分组内返回 0 |
| `getMaterialGroupName(material)` | 该材料所属分组名 |

### 扣减与退回

```kotlin
val result = api.tryConsume(player.uniqueId, 50, "myplugin:teleport")
if (!result.success) {
    // result.failReason: NOT_LOADED / NO_SUBSCRIPTION / EXPIRED / INSUFFICIENT / LOCKED / INVALID_AMOUNT
    player.sendMessage("额度不足，当前可用 ${result.availableBefore} 点")
    return
}
if (!teleport(player)) {
    // 业务失败记得退回，否则玩家白扣
    api.refund(player.uniqueId, 50, "myplugin:teleport-refund")
}
```

`tryConsume` 是原子的：要么全扣、要么一点不扣。`refund` 退回到"补偿额度"，不占用三档配额，所以不会因为退回而突破 5h/日/周上限。

另外 `grantBonus(uuid, points)` 可以直接发放补偿额度，适合活动奖励。

### 事件

```kotlin
@EventHandler
fun onBalanceChange(event: BalanceChangeEvent) {
    // event.delta 负数=扣减，正数=增加
    // event.reason 是调用方传进来的原因
    logger.info("${event.playerId} 额度变化 ${event.delta}，剩余 ${event.availableAfter}，原因 ${event.reason}")
}

@EventHandler
fun onSubscriptionChange(event: SubscriptionChangeEvent) {
    // event.reason: SUBSCRIBE / UPGRADE / EXPIRE / ADMIN_RESET / ADMIN_EXTEND
}
```

事件只携带 UUID，需要实体时自行 `Bukkit.getPlayer(uuid)`。

### 线程约定

所有方法直接读写内存中的额度对象，**必须在玩家所属线程调用**（普通 Paper 服务端即主线程；Folia 下是该玩家所在 region 的线程）。从异步线程调用不会崩服，但可能读到扣减过程中的中间值。

离线玩家的额度不会常驻内存，调用会得到 `ConsumeResult.FailReason.NOT_LOADED` 或 null，可先用 `isLoaded` 判断。

## 许可证

开源项目，按需使用。
