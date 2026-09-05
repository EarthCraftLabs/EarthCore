package de.mecrytv.earthcore.config

import de.mecrytv.earthcore.database.api.DatabaseCredentials
import de.mecrytv.earthcore.logging.api.LoggingSettings

data class PluginConfig(
    val settings: Settings = Settings(),
    val database: DatabaseCredentials = DatabaseCredentials(),
    val logging: LoggingSettings = LoggingSettings(),
)

data class Settings(
    val namespace: String = "earthcraft",
    val debug: Boolean = false,
)
