package mc506lw.buildingPlan.api

/**
 * [BuildingPlanAPI.tryConsume] 的返回值。
 *
 * Java 侧判断成功与否请用 [success]，失败原因用 [failReason]。
 */
data class ConsumeResult(
    val success: Boolean,
    /** 实际扣减点数；失败时为 0。 */
    val consumed: Int,
    /** 扣减前可用点数。 */
    val availableBefore: Int,
    /** 扣减后可用点数；失败时与 [availableBefore] 相同。 */
    val availableAfter: Int,
    /** 失败原因；成功时为 null。 */
    val failReason: FailReason?
) {

    enum class FailReason {
        /** 玩家额度数据尚未加载（离线或刚上线）。 */
        NOT_LOADED,
        /** 没有订阅。 */
        NO_SUBSCRIPTION,
        /** 订阅已过期。 */
        EXPIRED,
        /** 可用点数不足。 */
        INSUFFICIENT,
        /** 该玩家的额度正在被其他操作占用，稍后重试。 */
        LOCKED,
        /** 请求的点数不是正数。 */
        INVALID_AMOUNT
    }

    companion object {

        @JvmStatic
        fun ok(consumed: Int, availableBefore: Int, availableAfter: Int): ConsumeResult =
            ConsumeResult(true, consumed, availableBefore, availableAfter, null)

        @JvmStatic
        fun fail(reason: FailReason, availableBefore: Int): ConsumeResult =
            ConsumeResult(false, 0, availableBefore, availableBefore, reason)
    }
}
