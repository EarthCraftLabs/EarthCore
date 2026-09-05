package de.mecrytv.earthcore.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

fun interface ConfigDefaults {

    fun load(gson: Gson): JsonObject

    companion object {

        fun model(model: Any): ConfigDefaults = ConfigDefaults { gson ->
            gson.toJsonTree(model).asJsonObject
        }

        fun resource(
            path: String = "config.json",
            classLoader: ClassLoader? = null,
        ): ConfigDefaults {
            val loader = classLoader
                ?: CallerLookup.outside(ConfigDefaults::class.java)?.classLoader
                ?: ConfigDefaults::class.java.classLoader
            return ConfigDefaults {
                val stream = loader.getResourceAsStream(path)
                    ?: error("Ressource '$path' fehlt im resources-Ordner des Plugins")
                val parsed = stream.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
                parsed as? JsonObject ?: error("Ressource '$path' enthaelt kein JSON-Objekt")
            }
        }

        fun values(vararg entries: Pair<String, Any?>): ConfigDefaults = ConfigDefaults { gson ->
            JsonObject().also { root ->
                entries.forEach { (path, value) -> JsonPaths.set(root, path, gson.toJsonTree(value)) }
            }
        }
    }
}
