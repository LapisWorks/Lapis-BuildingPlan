package mc506lw.buildingPlan

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import mc506lw.buildingPlan.command.BpCommand
import mc506lw.buildingPlan.config.ConfigManager
import mc506lw.buildingPlan.config.LangManager
import mc506lw.buildingPlan.database.DatabaseManager
import mc506lw.buildingPlan.gui.GuiManager
import mc506lw.buildingPlan.manager.PlanManager
import mc506lw.buildingPlan.papi.PAPIExpansion
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

class BuildingPlan : JavaPlugin(), Listener {

    companion object {
        lateinit var instance: BuildingPlan
            private set
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

    override fun onEnable() {
        instance = this

        configManager = ConfigManager(this)
        configManager.load()

        databaseManager = DatabaseManager(dataFolder)
        databaseManager.init()

        langManager = LangManager(this)
        langManager.load()

        planManager = PlanManager(this)
        guiManager = GuiManager(this)

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
        planManager.stopSettlementTask()
        for (player in server.onlinePlayers) {
            planManager.onPlayerQuit(player.uniqueId)
        }
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
