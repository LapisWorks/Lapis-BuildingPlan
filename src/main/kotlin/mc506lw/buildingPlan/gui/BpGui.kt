package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * 基于原生 Bukkit Inventory API 的 GUI 基类。
 *
 * 与旧版 InvUI 方案不同：本插件不再依赖任何第三方 GUI 库，
 * 所有事件均通过 [mc506lw.buildingPlan.gui.GuiManager] 中严格的
 * `InventoryHolder` 类型检查来限定作用域，因此绝不会干扰其他插件的 GUI。
 *
 * 每个实例对应一个玩家的一次打开，关闭后即被回收。
 */
abstract class BpGui(
    protected val plugin: BuildingPlan,
    protected open var rows: Int,
    protected val titleKey: String
) : InventoryHolder {

    protected lateinit var backingInventory: Inventory
    protected lateinit var viewer: Player

    /** 打开 GUI；子类可覆写以在打开前做校验/准备（如升级 GUI 需要玩家数据） */
    open fun open(player: Player) {
        viewer = player
        val title = MiniMessage.miniMessage().deserialize(plugin.langManager.raw(player, titleKey))
        backingInventory = Bukkit.createInventory(this, rows * 9, title)
        onBeforeRender(player)
        render(player)
        player.openInventory(backingInventory)
        onOpened(player)
    }

    /** 原地重绘（购买/翻页/切换 tab 后调用，不重开窗口） */
    fun refresh() {
        if (::backingInventory.isInitialized && ::viewer.isInitialized) {
            render(viewer)
        }
    }

    /** 打开前调用，用于恢复上次的界面状态（如选中的 tab） */
    protected open fun onBeforeRender(player: Player) {}

    /** 打开后调用 */
    protected open fun onOpened(player: Player) {}

    /** 关闭时调用，用于清理状态 */
    open fun onClosed(player: Player) {}

    /** 重绘整个界面内容 */
    protected abstract fun render(player: Player)

    /** 处理对顶部（本插件）库存槽位的点击 */
    open fun onClick(event: InventoryClickEvent, player: Player) {}

    protected fun setItem(slot: Int, item: ItemStack?) {
        backingInventory.setItem(slot, item)
    }

    override fun getInventory(): Inventory = backingInventory
}
