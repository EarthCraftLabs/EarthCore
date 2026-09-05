package de.mecrytv.earthcore.config

import com.google.gson.JsonObject

fun interface ConfigMigration {

    fun migrate(root: JsonObject)
}
