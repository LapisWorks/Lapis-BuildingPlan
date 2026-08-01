package mc506lw.buildingPlan.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.util.concurrent.ConcurrentHashMap

object GuiUtils {

    private val mm = MiniMessage.miniMessage()

    fun mm(text: String): Component = mm.deserialize(text)

    fun formatExpire(expireTime: Long): String {
        if (expireTime <= 0) return "N/A"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return sdf.format(java.util.Date(expireTime * 1000))
    }

    /**
     * 返回材料显示名称的 MiniMessage 片段，由客户端根据自身语言自动翻译。
     * 通过 ItemStack.translationKey() 获取 Minecraft 官方翻译键，
     * 再包装为 <lang:key> 标签交给客户端渲染（zh_cn/en_us 等所有语言均支持）。
     * 无需联网、无需写死对照表。失败时回退为 material 小写形式。
     * 结果会按 material 缓存以避免重复查找。
     */
    private val nameCache = ConcurrentHashMap<String, String>()

    fun materialDisplayName(material: String): String {
        nameCache[material]?.let { return it }

        val fallback = material.lowercase().replace('_', ' ')
        val mat = try {
            Material.valueOf(material)
        } catch (e: IllegalArgumentException) {
            nameCache[material] = fallback
            return fallback
        }
        if (mat.isAir) {
            nameCache[material] = fallback
            return fallback
        }

        val name = try {
            val key = ItemStack.of(mat).translationKey()
            "<lang:$key>"
        } catch (e: Exception) {
            fallback
        }
        nameCache[material] = name
        return name
    }

    /** 构造带 MiniMessage 名称/Lore 的物品 */
    fun item(material: Material, name: String? = null, lore: List<String> = emptyList(), amount: Int = 1): ItemStack {
        val stack = ItemStack.of(material, amount)
        return item(stack, name, lore)
    }

    /** 在已有 ItemStack 上设置 MiniMessage 名称/Lore */
    fun item(stack: ItemStack, name: String? = null, lore: List<String> = emptyList()): ItemStack {
        if (name == null && lore.isEmpty()) return stack
        stack.editMeta { meta ->
            if (name != null) meta.displayName(mm(name))
            if (lore.isNotEmpty()) meta.lore(lore.map { mm(it) })
        }
        return stack
    }

    /** 构造一个隐藏 Tooltip 的纯背景物品 */
    fun background(material: Material = Material.GRAY_STAINED_GLASS_PANE): ItemStack {
        return ItemStack.of(material).apply { editMeta { it.isHideTooltip = true } }
    }

    /** 构造带耐久值的物品（用于额度条展示） */
    fun damaged(material: Material, damage: Int): ItemStack {
        return ItemStack.of(material).apply {
            editMeta { meta ->
                if (meta is Damageable) meta.damage = damage
            }
        }
    }
}
