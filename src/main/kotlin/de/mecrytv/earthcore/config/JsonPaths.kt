package de.mecrytv.earthcore.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal object JsonPaths {

    fun find(root: JsonObject, path: String): JsonElement? {
        if (path.isEmpty()) return root
        var current: JsonElement = root
        for (key in path.split('.')) {
            val parent = current as? JsonObject ?: return null
            current = parent.get(key) ?: return null
        }
        return current.takeUnless { it.isJsonNull }
    }

    fun set(root: JsonObject, path: String, value: JsonElement) {
        val keys = path.splitPath()
        var node = root
        for (key in keys.dropLast(1)) {
            val child = node.get(key)
            node = if (child is JsonObject) child else JsonObject().also { node.add(key, it) }
        }
        node.add(keys.last(), value)
    }

    fun remove(root: JsonObject, path: String): Boolean {
        val keys = path.splitPath()
        val parent = if (keys.size == 1) root else find(root, keys.dropLast(1).joinToString(".")) as? JsonObject
        return parent?.remove(keys.last()) != null
    }

    private fun String.splitPath(): List<String> {
        require(isNotEmpty()) { "Pfad darf nicht leer sein" }
        return split('.')
    }
}
