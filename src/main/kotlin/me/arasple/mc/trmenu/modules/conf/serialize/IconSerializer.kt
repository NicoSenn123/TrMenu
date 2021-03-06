package me.arasple.mc.trmenu.modules.conf.serialize

import io.izzel.taboolib.internal.apache.lang3.math.NumberUtils
import me.arasple.mc.trmenu.api.inventory.InvClickType
import me.arasple.mc.trmenu.modules.conf.menu.MenuConfiguration
import me.arasple.mc.trmenu.modules.conf.property.Property
import me.arasple.mc.trmenu.modules.display.Icon
import me.arasple.mc.trmenu.modules.display.animation.Animated
import me.arasple.mc.trmenu.modules.display.icon.IconClickHandler
import me.arasple.mc.trmenu.modules.display.icon.IconDisplay
import me.arasple.mc.trmenu.modules.display.icon.IconProperty
import me.arasple.mc.trmenu.modules.display.icon.IconSettings
import me.arasple.mc.trmenu.modules.display.item.DynamicItem
import me.arasple.mc.trmenu.modules.display.item.property.Lore
import me.arasple.mc.trmenu.modules.display.item.property.Mat
import me.arasple.mc.trmenu.modules.display.item.property.Meta
import me.arasple.mc.trmenu.modules.display.position.Position
import me.arasple.mc.trmenu.util.Utils
import org.bukkit.configuration.ConfigurationSection

/**
 * @author Arasple
 * @date 2020/7/7 14:42
 */
@Suppress("UNCHECKED_CAST")
object IconSerializer {

    /**
     * 加载菜单图标
     */
    fun loadIcons(c: MenuConfiguration): Set<Icon> = mutableSetOf<Icon>().let { icons ->
        val section = Utils.asSection(c.getIcons())

        section?.getKeys(false)?.forEach { key ->
            val iconSection = section.getConfigurationSection(key)
            if (iconSection != null) {
                val subIcons = iconSection.getList(Utils.getSectionKey(iconSection, Property.ICON_SUB_ICONS))
                val iconSettings = IconSettings(iconSection.getInt(Utils.getSectionKey(iconSection, Property.ICON_REFRESH), -1), Utils.asIntArray(iconSection.get(Utils.getSectionKey(iconSection, Property.ICON_UPDATE))))

                val defIcon = loadIconProperty(iconSection) ?: return@forEach
                val subs = mutableListOf<IconProperty>()

                if (subIcons != null && subIcons.isNotEmpty()) {
                    var order = subIcons.filterNotNull().size

                    subIcons.forEach {
                        it?.let {
                            Utils.asSection(it)?.let { sub ->
                                val subIcon = loadIconProperty(sub)
                                if (subIcon != null) {
                                    subIcon.priority = sub.getInt(Utils.getSectionKey(sub, Property.PRIORITY), order--)
                                    subIcon.condition = sub.getString(Utils.getSectionKey(sub, Property.REQUIREMENT)) ?: "false"
                                    subIcon.inherit = sub.getBoolean(Utils.getSectionKey(sub, Property.INHERIT))
                                    subs.add(subIcon)
                                }
                            }
                        }
                    }
                }
                subs.forEach {
                    // 继承位置, 材质
                    if (it.display.position.isEmpty()) it.display.position = defIcon.display.position
                    if (it.display.item.material.isEmpty()) it.display.item.material = defIcon.display.item.material
                    // 深度继承
                    if (it.inherit) {
                        if (it.display.name.isEmpty()) it.display.name = defIcon.display.name
                        if (it.display.lore.isEmpty()) it.display.lore = defIcon.display.lore
                    }
                }
                icons.add(Icon(key, iconSettings, defIcon, subs.sortedByDescending { it.priority }, mutableMapOf()))
            }
        }

        return@let icons
    }

    /**
     * 加载图标显示及点击处理属性
     */
    fun loadIconProperty(section: ConfigurationSection): IconProperty? {
        val displaySection = Utils.asSection(section.get(Utils.getSectionKey(section, Property.ICON_DISPLAY))) ?: return null
        val clickHandlerSection = Utils.asSection(section.get(Utils.getSectionKey(section, Property.ACTIONS)))
        val iconDisplay = loadIconDisplay(displaySection)
        val clickHandler = loadIconClickHandler(clickHandlerSection)

        return IconProperty(-1, "", false, iconDisplay, clickHandler)
    }

    /**
     * 加载图标的显示部分
     */
    fun loadIconDisplay(display: ConfigurationSection): IconDisplay {
        val page = display.getIntegerList(Utils.getSectionKey(display, Property.ICON_DISPLAY_PAGE)).let {
            if (it.isEmpty()) it.add(0)
            return@let it
        }
        val slots = Utils.asAnyList(display.get(Utils.getSectionKey(display, Property.ICON_DISPLAY_SLOT)))
        // 加载坐标
        val positions = let {
            if (slots.isNotEmpty()) {
                val pos = Animated(mutableListOf<Position>().let {
                    if (slots.first() is List<*>) slots.forEach { s -> it.add(Position.createPosition(s as List<String>)) }
                    else it.add(Position.createPosition(slots))
                    it
                }.toTypedArray())
                return@let mutableMapOf<Int, Animated<Position>>().let {
                    page.forEach { p -> it[NumberUtils.toInt(p.toString(), 0)] = pos }
                    it
                }
            } else return@let null
        }

        // 加载显示物品
        val items = let {
            val mats = mutableListOf<Mat>()
            val meta = Meta()
            meta.amount(display.getString(Utils.getSectionKey(display, Property.ICON_DISPLAY_AMOUNT)) ?: "1")
            meta.shiny(display.getString(Utils.getSectionKey(display, Property.ICON_DISPLAY_SHINY)) ?: "false")
            meta.flags(display.getStringList(Utils.getSectionKey(display, Property.ICON_DISPLAY_FLAGS)))
            meta.nbt(display.getConfigurationSection(Utils.getSectionKey(display, Property.ICON_DISPLAY_NBT)))

            Utils.asList(display.get(Utils.getSectionKey(display, Property.ICON_DISPLAY_MATERIAL))).forEach {
                mats.add(Mat.createMat(it))
            }

            return@let DynamicItem(Animated(mats.toTypedArray()), meta, mutableMapOf())
        }

        // 加载显示名称、Lore 描述

        val names = Utils.asList(display.get(Utils.getSectionKey(display, Property.ICON_DISPLAY_NAME)))
        val lores = mutableListOf<Lore>().let { lore ->
            display.getList(Utils.getSectionKey(display, Property.ICON_DISPLAY_LORE))?.let { it ->
                if (it.firstOrNull() is List<*>) it.forEach { lore.add(Lore(it as List<String>)) }
                else lore.add(Lore(it as List<String>))
            }
            return@let lore
        }

        return IconDisplay(positions ?: mutableMapOf(), items, Animated(names.toTypedArray()), Animated(lores.toTypedArray()))
    }

    /**
     * 加载图标的点击部分
     */
    fun loadIconClickHandler(click: ConfigurationSection?): IconClickHandler {
        val handlers = mutableListOf<IconClickHandler.Handler>()

        click?.getKeys(false)?.forEach { type ->
            val types = InvClickType.matches(type)
            if (types.isNotEmpty()) {
                val reactions = ReactionSerializer.serializeReactions(click[type])
                if (!reactions.isEmpty) {
                    handlers.add(IconClickHandler.Handler(types, reactions))
                }
            }
        }

        return IconClickHandler(handlers)
    }


}