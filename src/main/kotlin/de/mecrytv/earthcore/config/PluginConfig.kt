package de.mecrytv.earthcore.config

import de.mecrytv.earthcore.database.api.DatabaseCredentials

data class PluginConfig(
    val settings: Settings = Settings(),
    val database: DatabaseCredentials = DatabaseCredentials(),
)

data class Settings(
    val namespace: String = "earthcraft",
    val debug: Boolean = false,
)
