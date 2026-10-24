package mc506lw.buildingPlan

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import mc506lw.buildingPlan.api.BuildingPlanAPI
import mc506lw.buildingPlan.api.BuildingPlanApiImpl
import mc506lw.buildingPlan.command.BpCommand
import mc506lw.buildingPlan.config.ConfigManager
import mc506lw.buildingPlan.config.LangManager
import mc506lw.buildingPlan.database.DatabaseManager
import mc506lw.buildingPlan.gui.GuiManager
import mc506lw.buildingPlan.gui.ShopGui
import mc506lw.buildingPlan.manager.PlanManager
import mc506lw.buildingPlan.papi.PAPIExpansion
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.function.Consumer

class BuildingPlan : JavaPlugin(), Listener {

    companion object {
        lateinit var instance: BuildingPlan
            private set

        @Volatile
        private var apiRef: BuildingPlanAPI? = null

        /**
         * 获取对外 API。插件尚未启用或已卸载时返回 null。
         *
         * 也可以走 Bukkit 服务通道：
         * `Bukkit.getServicesManager().load(BuildingPlanAPI::class.java)`。
         */
        @JvmStatic
        fun getAPI(): BuildingPlanAPI? = apiRef
    }

    val isFolia: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    lateinit var databaseManager: DatabaseManager
        private set
    lateinit var configManager: ConfigManager
        private set
    lateinit var langManager: LangManager
        private set
    lateinit var planManager: PlanManager
        private set
    lateinit var guiManager: GuiManager
        private set

    /**
     * v1.5 起插件名由 BuildingPlan 改为 Lapis-BuildingPlan，数据目录随之从
     * `plugins/BuildingPlan/` 变成 `plugins/Lapis-BuildingPlan/`。
     *
     * 这里做一次性迁移：仅当「新目录还没有数据库」且「旧目录有数据库」时才复制，
     * 绝不覆盖已有数据；迁移失败只告警，不影响插件启动。
     */
    private fun migrateLegacyDataFolder() {
        val legacy = File(dataFolder.parentFile, "BuildingPlan")
        if (!legacy.isDirectory) return
        if (File(dataFolder, "data.mv.db").exists()) return
        if (!File(legacy, "data.mv.db").exists()) return
        try {
            legacy.copyRecursively(dataFolder, overwrite = false)
            logger.info("已将旧数据目录 ${legacy.name} 迁移到 ${dataFolder.name}")
        } catch (e: Exception) {
            logger.warning("迁移旧数据目录失败，请手动把 ${legacy.path} 的内容复制到 ${dataFolder.path}: ${e.message}")
        }
    }

    override fun onEnable() {
        instance = this

        // 必须在读取配置和打开数据库之前完成，否则新目录会是空的
        migrateLegacyDataFolder()

        configManager = ConfigManager(this)
        configManager.load()

        databaseManager = DatabaseManager(dataFolder)
        databaseManager.init()

        langManager = LangManager(this)
        langManager.load()

        planManager = PlanManager(this)
        guiManager = GuiManager(this)

        // 对外 API：静态入口 + Bukkit 服务管理器双通道，方便其他插件按需取用
        val apiImpl = BuildingPlanApiImpl(this)
        apiRef = apiImpl
        server.servicesManager.register(BuildingPlanAPI::class.java, apiImpl, this, ServicePriority.Normal)

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(guiManager, this)

        getCommand("bp")?.setExecutor(BpCommand(this))
        getCommand("bp")?.tabCompleter = BpCommand(this)

        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            PAPIExpansion(this).register()
            logger.info("PlaceholderAPI expansion registered!")
        }

        for (player in server.onlinePlayers) {
            planManager.onPlayerJoin(player.uniqueId)
        }

        planManager.startSettlementTask()

        logger.info("${pluginMeta.name} v${pluginMeta.version} enabled [Folia=$isFolia]")
    }

    override fun onDisable() {
        apiRef = null
        planManager.stopSettlementTask()
        for (player in server.onlinePlayers) {
            planManager.onPlayerQuit(player.uniqueId)
        }
        databaseManager.clearLocks()
        databaseManager.close()
        logger.info("${pluginMeta.name} disabled")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        planManager.onPlayerJoin(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        planManager.onPlayerQuit(event.player.uniqueId)
        ShopGui.clearTab(event.player.uniqueId)
    }

    fun runTask(runnable: Runnable) {
        if (isFolia) {
            server.globalRegionScheduler.run(this, Consumer<ScheduledTask> { runnable.run() })
        } else {
            server.scheduler.runTask(this, runnable)
        }
    }

    fun runTaskLater(runnable: Runnable, delayTicks: Long) {
        if (isFolia) {
            server.globalRegionScheduler.runDelayed(this, Consumer<ScheduledTask> { runnable.run() }, delayTicks)
        } else {
            server.scheduler.runTaskLater(this, runnable, delayTicks)
        }
    }

    fun runTaskTimer(runnable: Runnable, delayTicks: Long, periodTicks: Long): Any {
        return if (isFolia) {
            server.globalRegionScheduler.runAtFixedRate(
                this,
                Consumer<ScheduledTask> { runnable.run() },
                delayTicks,
                periodTicks
            )
        } else {
            server.scheduler.runTaskTimer(this, runnable, delayTicks, periodTicks)
        }
    }

    fun runOnEntity(entity: org.bukkit.entity.Entity, runnable: Runnable) {
        if (isFolia) {
            entity.scheduler.run(this, Consumer<ScheduledTask> { runnable.run() }, null)
        } else {
            server.scheduler.runTask(this, runnable)
        }
    }

    fun runOnLocation(location: org.bukkit.Location, runnable: Runnable) {
        if (isFolia) {
            server.regionScheduler.run(this, location, Consumer<ScheduledTask> { runnable.run() })
        } else {
            server.scheduler.runTask(this, runnable)
        }
    }
}
