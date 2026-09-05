package de.mecrytv.earthcore.item.internal

import com.google.gson.JsonParser
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.Base64

internal object ItemText {

    private val mini = MiniMessage.miniMessage()

    fun parse(raw: String): Component = upright(mini.deserialize(raw))

    fun upright(component: Component): Component =
        component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    fun verifyTexture(base64: String): String {
        val json = runCatching { String(Base64.getDecoder().decode(base64.trim()), Charsets.UTF_8) }
            .getOrElse { error("Die Textur ist kein gueltiges Base64") }

        val url = runCatching {
            JsonParser.parseString(json).asJsonObject
                .getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url").asString
        }.getOrElse { error("Die Textur enthaelt kein textures.SKIN.url") }

        require(url.startsWith("http://textures.minecraft.net/") || url.startsWith("https://textures.minecraft.net/")) {
            "Skin-URL muss auf textures.minecraft.net zeigen, war '$url'"
        }
        return url
    }
}
