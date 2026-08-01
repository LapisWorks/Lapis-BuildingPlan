package mc506lw.buildingPlan.gui

import mc506lw.buildingPlan.BuildingPlan
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import kotlin.math.min

class SubscribeGui(plugin: BuildingPlan) : BpGui(plugin, rows = subscribeRows(plugin), titleKey = "gui.subscribe-title") {

    private val displayKeys: List<String>
    private val planSlots: List<Int>

    init {
        val keys = plugin.configManager.plans.keys.toList()
        displayKeys = keys.take(MAX_PLANS)
        planSlots = computePlanSlots(displayKeys.size)
    }

    override fun render(player: Player) {
        val isEn = plugin.langManager.getLocale(player) == "en_us"
        val bg = GuiUtils.background()

        backingInventory.clear()
        for (i in 0 until backingInventory.size) {
            setItem(i, bg)
        }

        for ((index, tierName) in displayKeys.withIndex()) {
            val tier = plugin.configManager.getPlan(tierName) ?: continue
            val tierDisplay = if (isEn) tier.displayNameEn else tier.displayNameZh

            val priceLines = tier.price.entries.map { (mat, amt) ->
                val matName = GuiUtils.materialDisplayName(mat)
                "<#8D99AE>- <#FFD166>$amt x $matName"
            }

            val loreLines = if (isEn) {
                listOf(
                    "<#8D99AE>5h Quota: <#FFD166>${tier.quota5h}",
                    "<#8D99AE>Daily Quota: <#FFD166>${tier.quotaDaily}",
                    "<#8D99AE>Weekly Quota: <#FFD166>${tier.quotaWeekly}",
                    "<#8D99AE>Pool Cap: <#FFD166>${tier.poolCap}",
                    "<#8D99AE>Price:"
                ) + priceLines + listOf("", "<#06D6A0>Click to subscribe")
            } else {
                listOf(
                    "<#8D99AE>5h额度：<#FFD166>${tier.quota5h}",
                    "<#8D99AE>日额度：<#FFD166>${tier.quotaDaily}",
                    "<#8D99AE>周额度：<#FFD166>${tier.quotaWeekly}",
                    "<#8D99AE>积攒上限：<#FFD166>${tier.poolCap}",
                    "<#8D99AE>价格："
                ) + priceLines + listOf("", "<#06D6A0>点击订阅")
            }

            setItem(planSlots[index], GuiUtils.item(
                tier.icon,
                name = "<#48CAE4>$tierDisplay ${if (isEn) "Plan" else "套餐"}",
                lore = loreLines
            ))
        }
    }

    override fun onClick(event: InventoryClickEvent, player: Player) {
        val index = planSlots.indexOf(event.rawSlot)
        if (index < 0) return
        val tierName = displayKeys[index]

        val data = plugin.planManager.getPlayerData(player.uniqueId)
        if (data != null && data.tier != "NONE") {
            val now = System.currentTimeMillis() / 1000
            if (now < data.expireTime) {
                // 套餐有效，检查是否有更高级套餐可升级
                val planKeys = plugin.configManager.plans.keys.toList()
                val currentIdx = planKeys.indexOfFirst { it.equals(data.tier, true) }
                val hasHigher = currentIdx >= 0 && currentIdx < planKeys.lastIndex
                if (hasHigher) {
                    plugin.guiManager.openUpgrade(player)
                    return
                }
            }
            // 不能升级：已是最高级或已过期
            val currentTier = plugin.configManager.getPlanForTier(data.tier)
            val tierDisplay = if (plugin.langManager.getLocale(player) == "en_us") {
                currentTier?.displayNameEn ?: data.tier
            } else {
                currentTier?.displayNameZh ?: data.tier
            }
            plugin.langManager.msg(player, "already-subscribed", mapOf(
                "tier" to tierDisplay,
                "expire" to GuiUtils.formatExpire(data.expireTime)
            ))
        } else {
            val success = plugin.planManager.purchaseSubscription(player, tierName)
            if (success) {
                refresh()
            } else {
                plugin.langManager.msg(player, "subscribe-failed")
            }
        }
    }

    companion object {
        private const val MAX_PLANS = 8

        /** 套餐 GUI 行数：1-4 个为 3 行，5-8 个为 5 行 */
        private fun subscribeRows(plugin: BuildingPlan): Int {
            val planCount = plugin.configManager.plans.keys.size
            return if (planCount <= 4) 3 else 5
        }

        /**
         * 套餐槽位：每排最多 4 个，样式 `# a # b # c # d #`
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
