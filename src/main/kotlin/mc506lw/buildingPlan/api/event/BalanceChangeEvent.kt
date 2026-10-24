package mc506lw.buildingPlan.api.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * 玩家额度发生变化时触发。
 *
 * 同步事件，在引起变化的那次调用所在的线程上派发（通常就是主线程）。
 * 事件不可取消 —— 额度已经扣完才通知，监听方只能做记录、提示或统计。
 */
class BalanceChangeEvent(
    /** 玩家 UUID。事件只携带 UUID，需要实体时自行 `Bukkit.getPlayer(uuid)`。 */
    val playerId: UUID,
    /** 变化量：负数为扣减，正数为增加。 */
    val delta: Int,
    val availableBefore: Int,
    val availableAfter: Int,
    /** 变化原因，例如 `shop:STONE`、`api`、`myplugin:teleport`。 */
    val reason: String
) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
