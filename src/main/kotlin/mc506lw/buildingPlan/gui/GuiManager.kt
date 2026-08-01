package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.PlayerInventory

/**
 * GUI 事件入口。
 *
 * 关键设计：所有事件先检查 `view.topInventory.holder` 是否为本插件的 [BpGui] 实例，
 * 不是则直接忽略（return），因此本插件绝不会拦截、取消或影响其他插件 GUI 的任何操作。
 * 这是对旧版 InvUI 全局监听导致"影响其他插件 GUI"问题的根治。
 */
class GuiManager(private val plugin: BuildingPlan) : Listener {

    fun openShop(player: Player) {
        plugin.runOnEntity(player, Runnable { ShopGui(plugin).open(player) })
    }

    fun openSubscribe(player: Player) {
        plugin.runOnEntity(player, Runnable { SubscribeGui(plugin).open(player) })
    }

    fun openUpgrade(player: Player) {
        plugin.runOnEntity(player, Runnable { UpgradeGui(plugin).open(player) })
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInventoryClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder
        if (holder !is BpGui) return

        val player = event.whoClicked
        if (player !is Player) return

        val clicked = event.clickedInventory ?: return
        if (clicked === top) {
            // 点击本插件 GUI 的槽位：一律取消，再交给对应 GUI 处理
            event.isCancelled = true
            holder.onClick(event, player)
        } else if (clicked is PlayerInventory && event.isShiftClick) {
            // 防止 Shift 点击将背包物品移入本插件 GUI（避免物品被重绘覆盖丢失）
            event.isCancelled = true
        }
        // 玩家在自身背包内的普通点击放行，不影响手感
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val top = event.view.topInventory
        if (top.holder !is BpGui) return
        // 拖拽涉及本插件 GUI 的槽位时取消，纯背包内的拖拽放行
        if (event.rawSlots.any { it < top.size }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder
        if (holder !is BpGui) return
        val player = event.player
        if (player is Player) holder.onClosed(player)
    }
}
