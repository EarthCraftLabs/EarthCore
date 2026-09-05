package de.mecrytv.earthcore.item.api

import de.mecrytv.earthcore.item.internal.ItemText
import com.destroystokyo.paper.profile.ProfileProperty
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.block.banner.Pattern
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.FireworkMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionType
import java.util.UUID
import java.util.function.Consumer

class ItemBuilder private constructor(
    private val base: () -> ItemStack,
    private val steps: List<Consumer<ItemStack>>,
) {

    val stepCount: Int get() = steps.size

    // ---------------------------------------------------------------- Grundlagen

    fun amount(amount: Int): ItemBuilder {
        require(amount >= 1) { "Die Menge muss mindestens 1 sein, war $amount" }
        return with { it.amount = amount }
    }

    fun name(miniMessage: String): ItemBuilder = name(ItemText.parse(miniMessage))

    fun name(component: Component): ItemBuilder = meta { it.displayName(ItemText.upright(component)) }

    fun itemName(miniMessage: String): ItemBuilder = itemName(ItemText.parse(miniMessage))

    fun itemName(component: Component): ItemBuilder =
        with { it.setData(DataComponentTypes.ITEM_NAME, ItemText.upright(component)) }

    fun lore(vararg lines: String): ItemBuilder = loreComponents(lines.map(ItemText::parse))

    fun loreComponents(lines: List<Component>): ItemBuilder =
        meta { it.lore(lines.map(ItemText::upright)) }

    fun addLore(vararg lines: String): ItemBuilder = meta { meta ->
        meta.lore((meta.lore() ?: emptyList()) + lines.map(ItemText::parse))
    }

    fun enchant(enchantment: Enchantment, level: Int): ItemBuilder {
        require(level >= 1) { "Das Level muss mindestens 1 sein, war $level" }
        return meta { it.addEnchant(enchantment, level, true) }
    }

    fun flags(vararg flags: ItemFlag): ItemBuilder = meta { it.addItemFlags(*flags) }

    fun unbreakable(unbreakable: Boolean): ItemBuilder = meta { it.isUnbreakable = unbreakable }

    fun customModelData(value: Int): ItemBuilder = customModelData(
        CustomModelData.customModelData().addFloat(value.toFloat()).build(),
    )

    fun customModelData(value: String): ItemBuilder = customModelData(
        CustomModelData.customModelData().addString(value).build(),
    )

    fun customModelData(value: CustomModelData): ItemBuilder =
        with { it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, value) }

    fun damage(damage: Int): ItemBuilder {
        require(damage >= 0) { "Der Schaden darf nicht negativ sein, war $damage" }
        return meta(Damageable::class.java) { it.damage = damage }
    }

    // ---------------------------------------------------------------- Data Components

    fun glint(glint: Boolean): ItemBuilder =
        with { it.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, glint) }

    fun maxStackSize(size: Int): ItemBuilder {
        require(size in 1..99) { "Die Stapelgroesse muss zwischen 1 und 99 liegen, war $size" }
        return with { it.setData(DataComponentTypes.MAX_STACK_SIZE, size) }
    }

    // ---------------------------------------------------------------- Eigene Daten

    fun <P : Any, C : Any> data(
        key: NamespacedKey,
        type: PersistentDataType<P, C>,
        value: C,
    ): ItemBuilder = meta { it.persistentDataContainer.set(key, type, value) }

    fun tag(key: NamespacedKey, value: String): ItemBuilder =
        data(key, PersistentDataType.STRING, value)

    // ---------------------------------------------------------------- Koepfe

    fun skull(owner: OfflinePlayer): ItemBuilder = skull(
        ResolvableProfile.resolvableProfile().uuid(owner.uniqueId).also { builder ->
            owner.name?.let(builder::name)
        }.build(),
    )

    fun skullTexture(base64: String): ItemBuilder {
        ItemText.verifyTexture(base64)
        return skull(
            ResolvableProfile.resolvableProfile()
                .uuid(UUID.nameUUIDFromBytes(base64.toByteArray()))
                .addProperty(ProfileProperty("textures", base64))
                .build(),
        )
    }

    fun skull(profile: ResolvableProfile): ItemBuilder =
        with { it.setData(DataComponentTypes.PROFILE, profile) }

    // ---------------------------------------------------------------- Spezialisiert

    fun armorColor(color: Color): ItemBuilder =
        meta(LeatherArmorMeta::class.java) { it.setColor(color) }

    fun potion(type: PotionType): ItemBuilder =
        meta(PotionMeta::class.java) { it.basePotionType = type }

    fun potionEffect(effect: PotionEffect, overwrite: Boolean): ItemBuilder =
        meta(PotionMeta::class.java) { it.addCustomEffect(effect, overwrite) }

    fun book(title: String, author: String, pages: List<String>): ItemBuilder =
        meta(BookMeta::class.java) { meta ->
            meta.title(ItemText.parse(title))
            meta.author(ItemText.parse(author))
            meta.pages(pages.map(ItemText::parse))
        }

    fun firework(power: Int, vararg effects: FireworkEffect): ItemBuilder {
        require(power in 0..127) { "Die Flughoehe muss zwischen 0 und 127 liegen, war $power" }
        return meta(FireworkMeta::class.java) { meta ->
            meta.power = power
            effects.forEach(meta::addEffect)
        }
    }

    fun bannerPatterns(vararg patterns: Pattern): ItemBuilder =
        meta(BannerMeta::class.java) { meta -> patterns.forEach(meta::addPattern) }

    // ---------------------------------------------------------------- Ausweichwege

    fun edit(action: Consumer<ItemStack>): ItemBuilder = with(action)

    fun <M : ItemMeta> meta(type: Class<M>, action: Consumer<M>): ItemBuilder = with { stack ->
        if (!stack.editMeta(type, action)) {
            error("${stack.type} hat keine ${type.simpleName} - dieser Aufruf passt nicht zum Material")
        }
    }

    // ---------------------------------------------------------------- Bauen

    fun build(): ItemStack = base().also { stack -> steps.forEach { it.accept(stack) } }

    private fun with(step: Consumer<ItemStack>): ItemBuilder = ItemBuilder(base, steps + step)

    private fun meta(action: (ItemMeta) -> Unit): ItemBuilder = with { it.editMeta(action) }

    private fun <M : ItemMeta> meta(type: Class<M>, action: (M) -> Unit): ItemBuilder =
        meta(type, Consumer(action))

    companion object {

        @JvmStatic
        fun of(material: Material): ItemBuilder = ItemBuilder({ ItemStack(material) }, emptyList())

        @JvmStatic
        fun of(material: Material, amount: Int): ItemBuilder = of(material).amount(amount)

        @JvmStatic
        fun from(stack: ItemStack): ItemBuilder = ItemBuilder({ stack.clone() }, emptyList())
    }
}
