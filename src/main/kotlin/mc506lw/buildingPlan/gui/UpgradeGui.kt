package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import kotlin.math.min

class UpgradeGui(plugin: BuildingPlan) : BpGui(plugin, rows = 0, titleKey = "gui.upgrade-title") {

    private lateinit var upgradeKeys: List<String>
    private lateinit var planSlots: List<Int>
    private lateinit var currentTier: mc506lw.buildingPlan.model.PlanTier
    private lateinit var playerData: mc506lw.buildingPlan.model.PlayerData

    override fun open(player: Player) {
        val data = plugin.planManager.getPlayerData(player.uniqueId)
        if (data == null || data.tier == "NONE") {
            plugin.langManager.msg(player, "no-subscription")
            return
        }
        val now = System.currentTimeMillis() / 1000
        if (now >= data.expireTime) {
            plugin.langManager.msg(player, "upgrade-no-active")
            return
        }

        val current = plugin.configManager.getPlanForTier(data.tier) ?: return
        val planKeys = plugin.configManager.plans.keys.toList()
        val currentIdx = planKeys.indexOfFirst { it.equals(data.tier, true) }

        // 只显示比当前级别高的套餐
        val upgrades = planKeys.filterIndexed { index, _ -> index > currentIdx }.take(MAX_PLANS)

        if (upgrades.isEmpty()) {
            plugin.langManager.msg(player, "upgrade-max-tier")
            return
        }

        playerData = data
        currentTier = current
        upgradeKeys = upgrades
        planSlots = computePlanSlots(upgrades.size)
        rows = if (upgrades.size <= 4) 3 else 5

        super.open(player)
    }

    override fun render(player: Player) {
        val isEn = plugin.langManager.getLocale(player) == "en_us"
        val bg = GuiUtils.background()

        backingInventory.clear()
        for (i in 0 until backingInventory.size) {
            setItem(i, bg)
        }

        for ((index, tierName) in upgradeKeys.withIndex()) {
            val newTier = plugin.configManager.getPlan(tierName) ?: continue
            val tierDisplay = if (isEn) newTier.displayNameEn else newTier.displayNameZh

            // 计算升级成本
            val cost = plugin.planManager.calculateUpgradeCost(playerData, currentTier, newTier)

            val priceLines = newTier.price.entries.map { (mat, amt) ->
                val matName = GuiUtils.materialDisplayName(mat)
                "<#8D99AE>- <#FFD166>$amt x $matName"
            }

            val costLines = if (cost != null) {
                val payLines = if (cost.pay.isEmpty()) {
                    listOf(if (isEn) "<#06D6A0>No additional cost!" else "<#06D6A0>无需额外付费！")
                } else {
                    cost.pay.entries.map { (mat, amt) ->
                        val matName = GuiUtils.materialDisplayName(mat)
                        "<#FFD166>需付 $amt x $matName"
                    }
                }
                val refundLines = if (cost.refund.isEmpty()) emptyList()
                else cost.refund.entries.map { (mat, amt) ->
                    val matName = GuiUtils.materialDisplayName(mat)
                    "<#8D99AE>返还 $amt x $matName"
                }
                payLines + refundLines
            } else {
                listOf("<#EF476F>${if (isEn) "Cannot calculate" else "无法计算"}")
            }

            val loreLines = if (isEn) {
                listOf(
                    "<#8D99AE>5h Quota: <#FFD166>${newTier.quota5h}",
                    "<#8D99AE>Daily Quota: <#FFD166>${newTier.quotaDaily}",
                    "<#8D99AE>Weekly Quota: <#FFD166>${newTier.quotaWeekly}",
                    "<#8D99AE>Pool Cap: <#FFD166>${newTier.poolCap}",
                    "<#8D99AE>Price:"
                ) + priceLines + listOf("", "<#48CAE4>--- Upgrade Cost ---") + costLines + listOf(
                    "",
                    "<#06D6A0>Click to upgrade"
                )
            } else {
                listOf(
                    "<#8D99AE>5h额度：<#FFD166>${newTier.quota5h}",
                    "<#8D99AE>日额度：<#FFD166>${newTier.quotaDaily}",
                    "<#8D99AE>周额度：<#FFD166>${newTier.quotaWeekly}",
                    "<#8D99AE>积攒上限：<#FFD166>${newTier.poolCap}",
                    "<#8D99AE>价格："
                ) + priceLines + listOf("", "<#48CAE4>--- 升级费用 ---") + costLines + listOf(
                    "",
                    "<#06D6A0>点击升级"
                )
            }

            setItem(planSlots[index], GuiUtils.item(
                newTier.icon,
                name = "<#48CAE4>$tierDisplay ${if (isEn) "Upgrade" else "升级"}",
                lore = loreLines
            ))
        }
    }

    override fun onClick(event: InventoryClickEvent, player: Player) {
        val index = planSlots.indexOf(event.rawSlot)
        if (index < 0) return
        val tierName = upgradeKeys[index]

        if (plugin.planManager.upgradeSubscription(player, tierName)) {
            // 升级成功后重绘（升级列表随当前套餐变化）
            refresh()
        }
    }

    companion object {
        private const val MAX_PLANS = 8

        /**
         * 升级套餐槽位：每排最多 4 个，样式 `# a # b # c # d #`
         * 3 行布局：槽位 10, 12, 14, 16（第 2 排）
         * 5 行布局：槽位 10, 12, 14, 16（第 2 排），28, 30, 32, 34（第 4 排）
         */
        private fun computePlanSlots(planCount: Int): List<Int> {
            val row1 = listOf(10, 12, 14, 16)
            val row2 = listOf(28, 30, 32, 34)
            return (row1 + row2).take(min(planCount, MAX_PLANS))
        }
    }
}
