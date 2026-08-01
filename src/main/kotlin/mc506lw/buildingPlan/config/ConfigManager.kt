package mc506lw.buildingPlan.config

import mc506lw.buildingPlan.BuildingPlan
import mc506lw.buildingPlan.model.MaterialGroup
import mc506lw.buildingPlan.model.PlanTier
import org.bukkit.Material

class ConfigManager(private val plugin: BuildingPlan) {

    var debug: Boolean = false
        private set
    var subscriptionDays: Int = 28
        private set
    val plans = mutableMapOf<String, PlanTier>()
    val materialGroups = mutableListOf<MaterialGroup>()

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config = plugin.config

        debug = config.getBoolean("debug", false)
        subscriptionDays = config.getInt("subscription-days", 28)

        plans.clear()
        val plansSection = config.getConfigurationSection("plans")
        if (plansSection != null) {
            for (key in plansSection.getKeys(false)) {
                val section = plansSection.getConfigurationSection(key) ?: continue
                val priceSection = section.getConfigurationSection("price")
                val price = mutableMapOf<String, Int>()
                if (priceSection != null) {
                    for (materialKey in priceSection.getKeys(false)) {
                        price[materialKey] = priceSection.getInt(materialKey)
                    }
                }
                plans[key] = PlanTier(
                    name = key,
                    price = price,
                    quota5h = section.getInt("quota_5h"),
                    quotaDaily = section.getInt("quota_daily"),
                    quotaWeekly = section.getInt("quota_weekly"),
                    poolCap = section.getInt("pool_cap"),
                    icon = parseIcon(section.getString("icon"), "EMERALD_BLOCK", "plan $key"),
                    displayNameZh = section.getString("display-name-zh") ?: key,
                    displayNameEn = section.getString("display-name-en") ?: key
                )
                if (debug) plugin.logger.info("[DEBUG] Loaded plan: $key -> ${plans[key]}")
            }
        }
        if (plans.size > 8) {
            plugin.logger.warning("Found ${plans.size} plans, but the GUI only supports up to 8. Only the first 8 will be displayed.")
        }

        materialGroups.clear()
        val groupsSection = config.getConfigurationSection("material-groups")
        if (groupsSection != null) {
            for (key in groupsSection.getKeys(false)) {
                val section = groupsSection.getConfigurationSection(key) ?: continue
                val iconStr = section.getString("icon")
                val icon = parseIcon(iconStr, "STONE", "group $key")
                val materialsList = section.getStringList("materials")
                val materials = materialsList.mapNotNull { matStr ->
                    try { Material.valueOf(matStr) }
                    catch (e: IllegalArgumentException) {
                        plugin.logger.warning("Invalid material: $matStr in group $key")
                        null
                    }
                }
                materialGroups.add(MaterialGroup(
                    name = key,
                    displayNameZh = section.getString("display-name-zh") ?: key,
                    displayNameEn = section.getString("display-name-en") ?: key,
                    icon = icon,
                    pointsPerStack = section.getInt("points-per-stack", 6),
                    materials = materials
                ))
                if (debug) plugin.logger.info("[DEBUG] Loaded material group: $key with ${materials.size} materials")
            }
        }
    }

    fun reload() {
        load()
    }

    fun getPlan(name: String): PlanTier? = plans[name]

    fun getPlanForTier(tierName: String): PlanTier? {
        return plans.entries.find { it.key.equals(tierName, true) }?.value
    }

    fun findMaterialGroup(material: Material): MaterialGroup? {
        return materialGroups.find { material in it.materials }
    }

    fun getPointsPerStack(material: Material): Int {
        return findMaterialGroup(material)?.pointsPerStack ?: 0
    }

    private fun parseIcon(iconStr: String?, default: String, context: String): Material {
        if (iconStr.isNullOrBlank()) {
            return try { Material.valueOf(default) } catch (e: IllegalArgumentException) { Material.STONE }
        }
        return try { Material.valueOf(iconStr) } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Invalid icon material: $iconStr for $context, using $default")
            try { Material.valueOf(default) } catch (e2: IllegalArgumentException) { Material.STONE }
        }
    }
}
