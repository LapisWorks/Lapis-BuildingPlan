package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 建材商店（原生 Bukkit Inventory API 实现）
 *
 * 布局（6 行 x 9 列）：
 *   行 1：分类标签（最多 9 个）
 *   行 2-5：商品内容（分页，每页 36 格）
 *   行 6：< 上一页 # # 5h 日 周 # 下一页 >
 *
 * 刷新机制：购买/翻页/切换分类后原地重绘（不重开窗口）。
 */
class ShopGui(plugin: BuildingPlan) : BpGui(plugin, rows = 6, titleKey = "gui.shop-title") {

    private val groups = plugin.configManager.materialGroups.take(MAX_TABS)
    private var selectedTab = 0
    private val pages = mutableMapOf<Int, Int>()

    override fun onBeforeRender(player: Player) {
        selectedTab = tabByPlayer[player.uniqueId]?.takeIf { it in groups.indices } ?: 0
    }

    override fun onClosed(player: Player) {
        tabByPlayer.remove(player.uniqueId)
    }

    override fun render(player: Player) {
        val isEn = plugin.langManager.getLocale(player) == "en_us"
        val bg = GuiUtils.background()

        backingInventory.clear()
        for (i in 0 until backingInventory.size) {
            setItem(i, bg)
        }

        // 行 1：分类标签
        for ((index, group) in groups.withIndex()) {
            val selected = index == selectedTab
            val displayName = if (isEn) group.displayNameEn else group.displayNameZh
            val color = if (selected) "<#FFD166>" else "<#48CAE4>"
            val lore = if (isEn) {
                listOf("<#8D99AE>${group.materials.size} materials | <#FFD166>${group.pointsPerStack} pts/stack")
            } else {
                listOf("<#8D99AE>${group.materials.size} 种建材 | <#FFD166>${group.pointsPerStack} 点/组")
            }
            setItem(index, GuiUtils.item(group.icon, name = "$color$displayName", lore = lore))
        }

        // 行 2-5：当前分类商品（分页）
        val group = groups[selectedTab]
        val materials = group.materials
        val pageCount = max(1, ceil(materials.size / 36.0).toInt())
        val page = min((pages[selectedTab] ?: 0), pageCount - 1)
        pages[selectedTab] = page

        for (i in 0 until 36) {
            val materialIndex = page * 36 + i
            if (materialIndex < materials.size) {
                setItem(9 + i, buildMaterialItem(player, group.materials[materialIndex], group.pointsPerStack, isEn))
            }
        }

        // 行 6：翻页 + 耐久条
        if (page > 0) {
            val name = if (isEn) "Previous Page ($page/$pageCount)" else "上一页 ($page/$pageCount)"
            setItem(45, GuiUtils.item(Material.ARROW, "<#8D99AE>$name"))
        }
        if (page < pageCount - 1) {
            val name = if (isEn) "Next Page (${page + 2}/$pageCount)" else "下一页 (${page + 2}/$pageCount)"
            setItem(53, GuiUtils.item(Material.ARROW, "<#8D99AE>$name"))
        }
        setItem(48, buildDurabilityItem(player, DurabilityType.QUOTA_5H, isEn))
        setItem(49, buildDurabilityItem(player, DurabilityType.QUOTA_DAILY, isEn))
        setItem(50, buildDurabilityItem(player, DurabilityType.QUOTA_WEEKLY, isEn))
    }

    private fun buildMaterialItem(player: Player, material: Material, cost: Int, isEn: Boolean) = GuiUtils.item(
        material,
        name = "<#48CAE4>${GuiUtils.materialDisplayName(material.name)}",
        lore = run {
            val d = plugin.planManager.getPlayerData(player.uniqueId)
            val available = d?.let { plugin.planManager.getAvailableBalance(it) } ?: 0
            val canBuy1 = available >= cost
            val canBuy8 = available >= cost * 8
            val canBuy64 = available >= cost * 64

            if (isEn) {
                listOf(
                    "<#8D99AE>Cost per stack: <#FFD166>$cost pts",
                    "<#8D99AE>Your available: <#FFD166>$available pts",
                    "",
                    if (canBuy1) "<#06D6A0>Left click: Buy 1 stack" else "<#EF476F>Left click: Buy 1 stack (insufficient)",
                    if (canBuy8) "<#06D6A0>Right click: Buy 8 stacks" else "<#EF476F>Right click: Buy 8 stacks (insufficient)",
                    if (canBuy64) "<#06D6A0>Shift+Left: Buy 64 stacks" else "<#EF476F>Shift+Left: Buy 64 stacks (insufficient)"
                )
            } else {
                listOf(
                    "<#8D99AE>每组消耗：<#FFD166>$cost 点",
                    "<#8D99AE>当前可用：<#FFD166>$available 点",
                    "",
                    if (canBuy1) "<#06D6A0>左键购买1组" else "<#EF476F>左键购买1组 (额度不足)",
                    if (canBuy8) "<#06D6A0>右键购买8组" else "<#EF476F>右键购买8组 (额度不足)",
                    if (canBuy64) "<#06D6A0>Shift+左键购买64组" else "<#EF476F>Shift+左键购买64组 (额度不足)"
                )
            }
        }
    )

    override fun onClick(event: InventoryClickEvent, player: Player) {
        val slot = event.rawSlot

        when {
            // 分类标签
            slot in 0 until groups.size -> {
                selectedTab = slot
                pages[slot] = 0
                tabByPlayer[player.uniqueId] = slot
                refresh()
            }
            // 上一页
            slot == 45 -> {
                val page = pages[selectedTab] ?: 0
                if (page > 0) {
                    pages[selectedTab] = page - 1
                    refresh()
                }
            }
            // 下一页
            slot == 53 -> {
                val page = pages[selectedTab] ?: 0
                val pageCount = max(1, ceil(groups[selectedTab].materials.size / 36.0).toInt())
                if (page < pageCount - 1) {
                    pages[selectedTab] = page + 1
                    refresh()
                }
            }
            // 商品槽位
            slot in 9 until 45 -> {
                val group = groups[selectedTab]
                val page = pages[selectedTab] ?: 0
                val materialIndex = page * 36 + (slot - 9)
                if (materialIndex >= group.materials.size) return

                val stacks = when {
                    event.isShiftClick && !event.isRightClick -> 64
                    event.isRightClick -> 8
                    else -> 1
                }
                if (plugin.planManager.purchaseMaterial(player, group.materials[materialIndex], stacks)) {
                    refresh()
                }
            }
        }
    }

    private enum class DurabilityType(val material: Material) {
        QUOTA_5H(Material.IRON_PICKAXE),
        QUOTA_DAILY(Material.GOLDEN_PICKAXE),
        QUOTA_WEEKLY(Material.DIAMOND_PICKAXE)
    }

    /**
     * 用工具耐久条展示额度使用比例：满耐久 = 额度全满，耐久越低 = 使用越多。
     */
    private fun buildDurabilityItem(player: Player, type: DurabilityType, isEn: Boolean) = GuiUtils.item(
        GuiUtils.damaged(type.material, damageOf(player, type)),
        name = nameOf(player, type, isEn),
        lore = loreOf(player, type, isEn)
    )

    private fun damageOf(player: Player, type: DurabilityType): Int {
        val d = plugin.planManager.getPlayerData(player.uniqueId)
        val tier = d?.let { plugin.configManager.getPlanForTier(it.tier) }
        val (used, max) = quotaUsage(d, tier, type)

        val maxDurability = type.material.maxDurability.toInt()
        val damage = if (max <= 0) maxDurability else ((maxDurability.toLong() * used) / max).toInt()
        return min(maxDurability, max(0, damage))
    }

    private fun quotaUsage(
        d: mc506lw.buildingPlan.model.PlayerData?,
        tier: mc506lw.buildingPlan.model.PlanTier?,
        type: DurabilityType
    ): Pair<Long, Long> {
        return when (type) {
            DurabilityType.QUOTA_5H -> {
                val m = tier?.quota5h?.toLong() ?: 0
                val u = if (d != null && d.tier != "NONE") (m - d.balance5h) else 0
                u to m
            }
            DurabilityType.QUOTA_DAILY -> {
                val m = tier?.quotaDaily?.toLong() ?: 0
                val u = if (d != null && d.tier != "NONE") (m - d.balanceDaily) else 0
                u to m
            }
            DurabilityType.QUOTA_WEEKLY -> {
                val m = tier?.quotaWeekly?.toLong() ?: 0
                val u = if (d != null && d.tier != "NONE") (m - d.balanceWeekly) else 0
                u to m
            }
        }
    }

    private fun nameOf(player: Player, type: DurabilityType, isEn: Boolean): String {
        val d = plugin.planManager.getPlayerData(player.uniqueId)
        val tier = d?.let { plugin.configManager.getPlanForTier(it.tier) }
        val (used, max) = quotaUsage(d, tier, type)
        val remaining = max - used

        val (label, color) = when (type) {
            DurabilityType.QUOTA_5H -> (if (isEn) "5h" else "5h额度") to "<#FFD166>"
            DurabilityType.QUOTA_DAILY -> (if (isEn) "Daily" else "日额度") to "<#06D6A0>"
            DurabilityType.QUOTA_WEEKLY -> (if (isEn) "Weekly" else "周额度") to "<#48CAE4>"
        }
        return "$color$label<#8D99AE>: <#FFD166>$remaining<#8D99AE>/<#FFD166>$max"
    }

    private fun loreOf(player: Player, type: DurabilityType, isEn: Boolean): List<String> {
        val d = plugin.planManager.getPlayerData(player.uniqueId)
        val tier = d?.let { plugin.configManager.getPlanForTier(it.tier) }
        val (used, max) = quotaUsage(d, tier, type)
        val remaining = max - used

        val usage = if (isEn) {
            "<#8D99AE>Used: <#FFD166>$used <#8D99AE>| Remaining: <#06D6A0>$remaining"
        } else {
            "<#8D99AE>已用：<#FFD166>$used <#8D99AE>| 剩余：<#06D6A0>$remaining"
        }
        return if (d != null && d.bonusPoints > 0) {
            listOf(usage) + if (isEn) {
                listOf("<#06D6A0>Bonus: +${d.bonusPoints}")
            } else {
                listOf("<#06D6A0>补偿额度：+${d.bonusPoints}")
            }
        } else {
            listOf(usage)
        }
    }

    companion object {
        private const val MAX_TABS = 9

        /** 记住每个玩家上次选中的分类（跨多次打开） */
        private val tabByPlayer = ConcurrentHashMap<UUID, Int>()
    }
}
