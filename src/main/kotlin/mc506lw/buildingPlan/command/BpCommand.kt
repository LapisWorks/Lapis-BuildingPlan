package mc506lw.buildingPlan.command

import mc506lw.buildingPlan.BuildingPlan
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.text.SimpleDateFormat
import java.util.Date

class BpCommand(private val plugin: BuildingPlan) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "shop" -> {
                if (sender !is Player) {
                    plugin.langManager.msg(sender, "player-only")
                    return true
                }
                if (!sender.hasPermission("buildingplan.shop")) {
                    plugin.langManager.msg(sender, "no-permission")
                    return true
                }
                // 检查是否有有效套餐
                val playerData = plugin.planManager.getPlayerData(sender.uniqueId)
                if (playerData == null) {
                    plugin.langManager.msg(sender, "data-loading")
                    return true
                }
                if (playerData.tier == "NONE") {
                    plugin.langManager.msg(sender, "no-subscription")
                    return true
                }
                plugin.planManager.checkExpiration(playerData)
                if (playerData.tier == "NONE") {
                    plugin.langManager.msg(sender, "subscription-expired")
                    return true
                }
                plugin.guiManager.openShop(sender)
            }
            "subscribe" -> {
                if (sender !is Player) {
                    plugin.langManager.msg(sender, "player-only")
                    return true
                }
                if (!sender.hasPermission("buildingplan.subscribe")) {
                    plugin.langManager.msg(sender, "no-permission")
                    return true
                }
                plugin.guiManager.openSubscribe(sender)
            }
            "plan" -> {
                if (sender !is Player) {
                    plugin.langManager.msg(sender, "player-only")
                    return true
                }
                if (!sender.hasPermission("buildingplan.use")) {
                    plugin.langManager.msg(sender, "no-permission")
                    return true
                }
                handlePlan(sender)
            }
            "upgrade" -> {
                if (sender !is Player) {
                    plugin.langManager.msg(sender, "player-only")
                    return true
                }
                if (!sender.hasPermission("buildingplan.use")) {
                    plugin.langManager.msg(sender, "no-permission")
                    return true
                }
                plugin.guiManager.openUpgrade(sender)
            }
            "admin" -> {
                if (!sender.hasPermission("buildingplan.admin")) {
                    plugin.langManager.msg(sender, "no-permission")
                    return true
                }
                handleAdmin(sender, args.drop(1).toTypedArray())
            }
            else -> sendHelp(sender)
        }
        return true
    }

    private fun handlePlan(player: Player) {
        val data = plugin.planManager.getPlayerData(player.uniqueId)
        if (data == null) {
            plugin.langManager.msg(player, "data-loading")
            return
        }
        if (data.tier == "NONE") {
            plugin.langManager.msg(player, "no-subscription")
            return
        }
        val tierName = data.tier
        val expireTime = data.expireTime * 1000
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val expireStr = dateFormat.format(Date(expireTime))
        val days = plugin.planManager.getRemainingDays(data)

        plugin.langManager.msg(player, "plan-info-tier", mapOf("tier" to tierName))
        plugin.langManager.msg(player, "plan-info-expire", mapOf("expire" to expireStr, "days" to days.toString()))

        val tier = plugin.configManager.getPlanForTier(tierName)
        if (tier != null) {
            plugin.langManager.msg(player, "balance-info", mapOf(
                "balance_5h" to data.balance5h.toString(),
                "quota_5h" to tier.quota5h.toString(),
                "balance_daily" to data.balanceDaily.toString(),
                "quota_daily" to tier.quotaDaily.toString()
            ))
            plugin.langManager.msg(player, "balance-info-2", mapOf(
                "balance_weekly" to data.balanceWeekly.toString(),
                "quota_weekly" to tier.quotaWeekly.toString(),
                "pool" to data.pool.toString(),
                "pool_cap" to tier.poolCap.toString()
            ))
            // 如果有 bonusPoints 才显示
            if (data.bonusPoints > 0) {
                plugin.langManager.msg(player, "bonus-info", mapOf("points" to data.bonusPoints.toString()))
            }
        }
    }

    private fun handleAdmin(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            plugin.langManager.msg(sender, "help-admin-give")
            plugin.langManager.msg(sender, "help-admin-reload")
            plugin.langManager.msg(sender, "help-admin-extend")
            plugin.langManager.msg(sender, "help-admin-reset")
            plugin.langManager.msg(sender, "help-admin-resetall")
            return
        }
        when (args[0].lowercase()) {
            "give" -> {
                if (args.size < 3) {
                    plugin.langManager.msg(sender, "help-admin-give")
                    return
                }
                val targetName = args[1]
                val points = args[2].toIntOrNull()
                if (points == null || points <= 0) {
                    plugin.langManager.msg(sender, "invalid-number")
                    return
                }
                val target = Bukkit.getPlayer(targetName)
                if (target == null) {
                    plugin.langManager.msg(sender, "player-not-found")
                    return
                }
                plugin.planManager.giveBonusPoints(target, points)
                plugin.langManager.msg(sender, "admin-give-success",
                    mapOf("player" to targetName, "points" to points.toString()))
            }
            "reload" -> {
                plugin.configManager.reload()
                plugin.langManager.load()
                plugin.langManager.msg(sender, "reloaded")
            }
            "extend" -> {
                if (args.size < 3) {
                    plugin.langManager.msg(sender, "help-admin-extend")
                    return
                }
                val targetName = args[1]
                val days = args[2].toIntOrNull()
                if (days == null || days <= 0) {
                    plugin.langManager.msg(sender, "invalid-number")
                    return
                }
                val target = Bukkit.getPlayer(targetName)
                if (target == null) {
                    plugin.langManager.msg(sender, "player-not-found")
                    return
                }
                val success = plugin.planManager.extendSubscription(target.uniqueId, days)
                if (success) {
                    val data = plugin.planManager.getPlayerData(target.uniqueId)
                    val expireTime = (data?.expireTime ?: 0) * 1000
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
                    val expireStr = dateFormat.format(Date(expireTime))
                    plugin.langManager.msg(sender, "admin-extend-success",
                        mapOf("player" to targetName, "days" to days.toString(), "expire" to expireStr))
                } else {
                    plugin.langManager.msg(sender, "admin-extend-failed")
                }
            }
            "reset" -> {
                if (args.size < 2) {
                    plugin.langManager.msg(sender, "help-admin-reset")
                    return
                }
                val targetName = args[1]
                val target = Bukkit.getPlayer(targetName)
                if (target == null) {
                    plugin.langManager.msg(sender, "player-not-found")
                    return
                }
                val success = plugin.planManager.resetQuotas(target.uniqueId)
                if (success) {
                    plugin.langManager.msg(sender, "admin-reset-success", mapOf("player" to targetName))
                } else {
                    plugin.langManager.msg(sender, "admin-reset-failed")
                }
            }
            "resetall" -> {
                val count = plugin.planManager.resetAllQuotas()
                plugin.langManager.msg(sender, "admin-resetall-success", mapOf("count" to count.toString()))
            }
        }
    }

    private fun sendHelp(sender: CommandSender) {
        plugin.langManager.msg(sender, "help-header")
        plugin.langManager.msg(sender, "help-shop")
        plugin.langManager.msg(sender, "help-subscribe")
        plugin.langManager.msg(sender, "help-plan")
        plugin.langManager.msg(sender, "help-upgrade")
        plugin.langManager.msg(sender, "help-admin-give")
        plugin.langManager.msg(sender, "help-admin-reload")
        plugin.langManager.msg(sender, "help-admin-extend")
        plugin.langManager.msg(sender, "help-admin-reset")
        plugin.langManager.msg(sender, "help-admin-resetall")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when {
            args.size == 1 -> {
                val base = mutableListOf("shop", "subscribe", "plan", "upgrade")
                if (sender.hasPermission("buildingplan.admin")) base.add("admin")
                base.filter { it.startsWith(args[0].lowercase()) }
            }
            args.size == 2 && args[0].lowercase() == "admin" -> listOf("give", "reload", "extend", "reset", "resetall").filter { it.startsWith(args[1].lowercase()) }
            args.size == 3 && args[0].lowercase() == "admin" && (args[1].lowercase() in listOf("give", "extend", "reset")) ->
                Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2]) }
            else -> emptyList()
        }
    }
}
