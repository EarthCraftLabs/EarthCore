package de.mecrytv.earthcore.database.internal

import com.google.gson.Gson
import de.mecrytv.earthcore.database.api.DatabaseCredentials
import de.mecrytv.earthcore.database.api.DatabaseProvider
import de.mecrytv.earthcore.database.api.DatabaseService
import org.mariadb.jdbc.Driver
import java.sql.Connection
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class HikariDatabaseProvider(
    private val credentials: DatabaseCredentials,
    private val gson: Gson,
    private val logger: Logger = Logger.getLogger(HikariDatabaseProvider::class.java.name),
) : DatabaseProvider {

    private val services = ConcurrentHashMap<String, HikariDatabaseService>()

    override fun of(name: String): DatabaseService {
        val database = Identifiers.check(name)
        return services.computeIfAbsent(database) {
            createIfMissing(it)
            HikariDatabaseService(credentials.copy(database = it), gson, logger).apply { connect() }
        }
    }

    override fun names(): Set<String> = services.keys.toSet()

    fun close() {
        services.values.forEach { it.close() }
        services.clear()
    }

    private fun createIfMissing(name: String) {
        serverConnection().use { connection ->
            connection.prepareStatement(EXISTS).use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { if (it.next()) return }
            }
            connection.createStatement().use {
                it.executeUpdate("CREATE DATABASE `$name` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
            }
        }
    }

    private fun serverConnection(): Connection {
        val properties = Properties()
        properties["user"] = credentials.user
        properties["password"] = credentials.password
        return Driver().connect(credentials.serverUrl, properties)
            ?: error("MariaDB-Treiber akzeptiert die URL '${credentials.serverUrl}' nicht")
    }

    private companion object {

        const val EXISTS = "SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?"
    }
}
