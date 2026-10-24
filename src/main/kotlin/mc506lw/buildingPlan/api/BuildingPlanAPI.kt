package mc506lw.buildingPlan.api

import org.bukkit.Material
import java.util.UUID

/**
 * BuildingPlan 对外 API。
 *
 * 其他插件通过 [mc506lw.buildingPlan.BuildingPlan.getAPI] 或
 * `Bukkit.getServicesManager().load(BuildingPlanAPI::class.java)` 获取实例。
 *
 * ### 线程约定
 * 所有方法都直接读写内存中的玩家额度对象，**必须在玩家所属线程调用**
 * （普通 Paper 服务端即主线程；Folia 下是该玩家所在 region 的线程）。
 * 从异步线程调用不会崩服，但可能读到扣减过程中的中间值，且事件不会被正确派发。
 *
 * ### 离线玩家
 * 额度数据是懒加载的：只有在线玩家才会常驻内存。离线玩家调用这些方法会得到
 * [ConsumeResult.FailReason.NOT_LOADED] 或 `null`，调用前可用 [isLoaded] 判断。
 *
 * ### 典型用法（扣额度）
 * ```kotlin
 * val api = BuildingPlan.getAPI() ?: return
 * val result = api.tryConsume(player.uniqueId, 50, "myplugin:teleport")
 * if (!result.success) {
 *     player.sendMessage("额度不足，当前可用 ${result.availableBefore}")
 *     return
 * }
 * // 业务失败时记得退回
 * if (!doSomething()) api.refund(player.uniqueId, 50, "myplugin:teleport-refund")
 * ```
 */
interface BuildingPlanAPI {

    /** 该玩家的额度数据是否已加载到内存（在线且加载完成）。 */
    fun isLoaded(playerId: UUID): Boolean

    /**
     * 当前生效的套餐名（配置文件里的 key，如 `lite`）。
     * 未订阅或已过期返回 null。
     */
    fun getTier(playerId: UUID): String?

    /** 是否有未过期的订阅。 */
    fun hasActiveSubscription(playerId: UUID): Boolean

    /** 额度快照；未订阅或已过期返回 null。 */
    fun getQuota(playerId: UUID): QuotaSnapshot?

    /**
     * 当前可用点数，语义与商店一致：
     * `min(5h余额, 日余额 + 积攒池, 周余额) + 补偿额度`。
     */
    fun getAvailable(playerId: UUID): Int

    /** 当前可用点数是否够支付 [points]。 */
    fun canAfford(playerId: UUID, points: Int): Boolean

    /** 订阅剩余天数（向上取整），无有效订阅返回 0。 */
    fun getRemainingDays(playerId: UUID): Long

    /** 订阅到期时间（Unix 秒），无有效订阅返回 0。 */
    fun getExpireTime(playerId: UUID): Long

    /**
     * 尝试扣除 [points] 点额度。原子操作：要么全扣，要么一点不扣。
     *
     * @param reason 扣减原因，会透传给 [mc506lw.buildingPlan.api.event.BalanceChangeEvent]，
     *               建议用 `插件名:用途` 的格式便于排查；不想区分时传 `"api"` 即可。
     */
    fun tryConsume(playerId: UUID, points: Int, reason: String): ConsumeResult

    /**
     * 退回额度。退回到"补偿额度"（bonusPoints），不占用三档配额，
     * 因此不会因为退回而突破 5h/日/周上限。
     *
     * @return 玩家数据未加载时返回 false。
     */
    fun refund(playerId: UUID, points: Int, reason: String): Boolean

    /**
     * 直接发放补偿额度（不经过订阅体系，不受三档上限约束）。
     * 适合活动奖励、管理员补偿等场景。
     */
    fun grantBonus(playerId: UUID, points: Int): Boolean

    /** 该材料的每整组（64 个）价格；不在任何分组内返回 0。 */
    fun getPointsPerStack(material: Material): Int

    /** 该材料所属分组的配置名（如 `bulk`）；不在任何分组内返回 null。 */
    fun getMaterialGroupName(material: Material): String?
}
