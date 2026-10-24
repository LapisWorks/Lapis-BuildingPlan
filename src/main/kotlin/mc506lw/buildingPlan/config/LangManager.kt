package mc506lw.buildingPlan.config

import mc506lw.buildingPlan.BuildingPlan
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File

class LangManager(private val plugin: BuildingPlan) {

    private val messages = mutableMapOf<String, Map<String, String>>()
    private val miniMessage = MiniMessage.miniMessage()

    /**
     * 当 language.yml 的内容结构有变更（新增/修改 key）时，bump 此版本号，
     * LangManager 会检测磁盘文件的版本号不匹配时强制用 JAR 内的最新版本覆盖。
     */
    private val currentLangVersion = 6

    private val fallbacks = mapOf(
        "gui.shop-title" to "<#00B4D8>建材商店",
        "gui.subscribe-title" to "<#00B4D8>订阅套餐",
        "gui.upgrade-title" to "<#00B4D8>升级套餐"
    )

    fun load() {
        messages.clear()
        saveDefaultLangFile()
        val langFile = File(plugin.dataFolder, "language.yml")
        val config = YamlConfiguration.loadConfiguration(langFile)
        for (localeKey in config.getKeys(false)) {
            val section = config.getConfigurationSection(localeKey) ?: continue
            val map = mutableMapOf<String, String>()
            loadSection(section, "", map)
            messages[localeKey.lowercase()] = map
        }
        if (plugin.configManager.debug) {
            plugin.logger.info("[DEBUG] Loaded ${messages.size} locales: ${messages.keys}")
        }
    }

    private fun loadSection(section: org.bukkit.configuration.ConfigurationSection, prefix: String, map: MutableMap<String, String>) {
        for (key in section.getKeys(false)) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = section.getString(key)
            if (value != null) {
                map[fullKey] = value
            } else {
                val subSection = section.getConfigurationSection(key)
                if (subSection != null) {
                    loadSection(subSection, fullKey, map)
                }
            }
        }
    }

    private fun saveDefaultLangFile() {
        val langFile = File(plugin.dataFolder, "language.yml")
        if (!langFile.exists()) {
            plugin.saveResource("language.yml", false)
            return
        }
        // 检测版本号，不匹配则先备份旧文件再用 JAR 内最新版本覆盖
        val diskConfig = YamlConfiguration.loadConfiguration(langFile)
        val diskVersion = diskConfig.getInt("lang-version", 0)
        if (diskVersion < currentLangVersion) {
            val backupFile = File(plugin.dataFolder, "language.yml.bak.v$diskVersion")
            try {
                langFile.copyTo(backupFile, overwrite = true)
                plugin.logger.info("Backed up old language.yml to ${backupFile.name}")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to backup old language.yml: ${e.message}")
            }
            plugin.logger.info("language.yml outdated (v$diskVersion < v$currentLangVersion), replacing with latest.")
            plugin.saveResource("language.yml", true)
        }
    }

    fun getLocale(player: Player): String {
        val locale = player.locale().toString().lowercase()
        return if (messages.containsKey(locale)) locale else "zh_cn"
    }

    fun get(player: Player, key: String, placeholders: Map<String, String> = emptyMap()): String {
        val locale = getLocale(player)
        val localeMessages = messages[locale] ?: messages["zh_cn"]
        var text = localeMessages?.get(key) ?: messages["zh_cn"]?.get(key) ?: fallbacks[key] ?: return key
        for ((k, v) in placeholders) {
            text = text.replace("{$k}", v)
        }
        return text
    }

    fun msg(player: Player, key: String, placeholders: Map<String, String> = emptyMap()) {
        val prefix = get(player, "prefix")
        val text = get(player, key, placeholders)
        val fullText = prefix + text
        val component = miniMessage.deserialize(fullText)
        player.sendMessage(component)
    }

    fun msg(sender: CommandSender, key: String, placeholders: Map<String, String> = emptyMap()) {
        if (sender is Player) {
            msg(sender, key, placeholders)
        } else {
            val localeMessages = messages["zh_cn"] ?: return
            val prefix = localeMessages["prefix"] ?: ""
            var text = localeMessages[key] ?: key
            for ((k, v) in placeholders) {
                text = text.replace("{$k}", v)
            }
            sender.sendMessage(miniMessage.deserialize(prefix + text))
        }
    }

    fun component(player: Player, key: String, placeholders: Map<String, String> = emptyMap()): Component {
        val text = get(player, key, placeholders)
        return miniMessage.deserialize(text)
    }

    fun componentList(player: Player, key: String, placeholders: Map<String, String> = emptyMap()): List<Component> {
        val locale = getLocale(player)
        val localeMessages = messages[locale] ?: messages["zh_cn"] ?: return emptyList()
        var text = localeMessages[key] ?: return emptyList()
        for ((k, v) in placeholders) {
            text = text.replace("{$k}", v)
        }
        return text.split("\n").map { miniMessage.deserialize(it) }
    }

    fun raw(player: Player, key: String, placeholders: Map<String, String> = emptyMap()): String {
        return get(player, key, placeholders)
    }
}
