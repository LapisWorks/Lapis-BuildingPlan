package mc506lw.buildingPlan.api

import mc506lw.buildingPlan.BuildingPlan
import org.bukkit.Material
import java.util.UUID

/**
 * [BuildingPlanAPI] 的默认实现，全部委托给 [mc506lw.buildingPlan.manager.PlanManager]
 * 和 [mc506lw.buildingPlan.config.ConfigManager]，不额外持有状态。
 */
class BuildingPlanApiImpl(private val plugin: BuildingPlan) : BuildingPlanAPI {

    private val planManager get() = plugin.planManager
    private val configManager get() = plugin.configManager

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    override fun isLoaded(playerId: UUID): Boolean = planManager.getPlayerData(playerId) != null

    override fun getTier(playerId: UUID): String? {
        val data = planManager.getPlayerData(playerId) ?: return null
        if (data.tier == "NONE") return null
        if (nowSeconds() >= data.expireTime) return null
        return data.tier.lowercase()
    }

    override fun hasActiveSubscription(playerId: UUID): Boolean = getTier(playerId) != null

    override fun getQuota(playerId: UUID): QuotaSnapshot? = planManager.snapshot(playerId)

    override fun getAvailable(playerId: UUID): Int {
        val data = planManager.getPlayerData(playerId) ?: return 0
        if (data.tier == "NONE" || nowSeconds() >= data.expireTime) return 0
        return planManager.getAvailableBalance(data)
    }

    override fun canAfford(playerId: UUID, points: Int): Boolean =
        points > 0 && getAvailable(playerId) >= points

    override fun getRemainingDays(playerId: UUID): Long {
        val data = planManager.getPlayerData(playerId) ?: return 0
        if (data.tier == "NONE") return 0
        return planManager.getRemainingDays(data)
    }

    override fun getExpireTime(playerId: UUID): Long {
        val data = planManager.getPlayerData(playerId) ?: return 0
        if (data.tier == "NONE") return 0
        return data.expireTime
    }

    override fun tryConsume(playerId: UUID, points: Int, reason: String): ConsumeResult =
        planManager.tryConsume(playerId, points, reason)

    override fun refund(playerId: UUID, points: Int, reason: String): Boolean =
        planManager.refund(playerId, points, reason)

    override fun grantBonus(playerId: UUID, points: Int): Boolean =
        planManager.giveBonusPoints(playerId, points)

    override fun getPointsPerStack(material: Material): Int =
        configManager.getPointsPerStack(material)

    override fun getMaterialGroupName(material: Material): String? =
        configManager.findMaterialGroup(material)?.name
}
