package mc506lw.buildingPlan.papi

import mc506lw.buildingPlan.BuildingPlan
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import java.text.SimpleDateFormat
import java.util.Date

class PAPIExpansion(private val plugin: BuildingPlan) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "buildingplan"

    override fun getAuthor(): String = plugin.pluginMeta.authors.firstOrNull() ?: "mc506lw"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer, params: String): String? {
        val uuid = player.uniqueId
        val data = plugin.planManager.getPlayerData(uuid) ?: return null
        val tier = plugin.configManager.getPlanForTier(data.tier)

        return when (params.lowercase()) {
            "tier" -> data.tier
            "5h" -> data.balance5h.toString()
            "5h_max" -> tier?.quota5h?.toString() ?: "0"
            "daily" -> data.balanceDaily.toString()
            "daily_max" -> tier?.quotaDaily?.toString() ?: "0"
            "weekly" -> data.balanceWeekly.toString()
            "weekly_max" -> tier?.quotaWeekly?.toString() ?: "0"
            "pool" -> data.pool.toString()
            "pool_max" -> tier?.poolCap?.toString() ?: "0"
            "available" -> plugin.planManager.getAvailableBalance(data).toString()
            "expire" -> {
                if (data.expireTime > 0) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    sdf.format(Date(data.expireTime * 1000))
                } else "N/A"
            }
            "expired" -> {
                if (data.tier == "NONE") "true"
                else {
                    val now = System.currentTimeMillis() / 1000
                    (now >= data.expireTime).toString()
                }
            }
            "bonus" -> data.bonusPoints.toString()
            else -> null
        }
    }
}
