package mc506lw.buildingPlan.api

import java.util.UUID

/**
 * 玩家额度快照。所有字段都是调用瞬间的只读副本，持有它不会随额度变化而更新。
 */
data class QuotaSnapshot(
    val playerId: UUID,
    /** 套餐配置名，如 `lite`。 */
    val tier: String,
    /** 5 小时爆发额度余额。 */
    val balance5h: Int,
    val quota5h: Int,
    /** 日额度余额。 */
    val balanceDaily: Int,
    val quotaDaily: Int,
    /** 周额度余额。 */
    val balanceWeekly: Int,
    val quotaWeekly: Int,
    /** 积攒池（日额度未用完时结转，有上限）。 */
    val pool: Int,
    val poolCap: Int,
    /** 补偿额度，不受三档上限约束，扣减时优先消耗。 */
    val bonusPoints: Int,
    /** 实际可用点数 = min(5h, 日+池, 周) + 补偿额度。 */
    val available: Int,
    /** 到期时间（Unix 秒）。 */
    val expireTime: Long,
    /** 剩余天数（向上取整）。 */
    val remainingDays: Long
)
