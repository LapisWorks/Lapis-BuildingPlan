package mc506lw.buildingPlan.api.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * 玩家订阅状态发生变化时触发（订阅、升级、过期、管理员重置/延期）。
 *
 * 同步事件，不可取消。
 */
class SubscriptionChangeEvent(
    val playerId: UUID,
    /** 变化前的套餐名；之前没有订阅时为 null。 */
    val oldTier: String?,
    /** 变化后的套餐名；因过期而清空时为 null。 */
    val newTier: String?,
    val reason: Reason,
    /** 变化后的到期时间（Unix 秒）；无订阅时为 0。 */
    val expireTime: Long
) : Event() {

    enum class Reason {
        /** 新订阅。 */
        SUBSCRIBE,
        /** 升级到更高档位。 */
        UPGRADE,
        /** 订阅到期被清空。 */
        EXPIRE,
        /** 管理员重置额度（套餐不变）。 */
        ADMIN_RESET,
        /** 管理员延长有效期。 */
        ADMIN_EXTEND
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
