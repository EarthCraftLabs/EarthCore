package de.mecrytv.earthcore.database.api

data class DatabaseCredentials(
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "earthcore",
    val user: String = "earthcore",
    val password: String = "",
    val poolSize: Int = 10,
    val connectionTimeoutMs: Long = 5_000,
) {

    val jdbcUrl: String get() = "jdbc:mariadb://$host:$port/$database"

    val serverUrl: String get() = "jdbc:mariadb://$host:$port/"
}
