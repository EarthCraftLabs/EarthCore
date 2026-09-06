package de.mecrytv.earthcore.logging.api

data class LoggingSettings(
    val debug: Boolean = false,
    val retentionDays: Int = 30,
    val discord: List<DiscordRoute> = emptyList(),
)

data class DiscordRoute(
    val url: String = "",
    val minLevel: LogLevel = LogLevel.WARN,
    val username: String = "EarthCraft",
) {

    fun accepts(entry: LogEntry): Boolean = url.isNotBlank() && entry.level.atLeast(minLevel)
}
