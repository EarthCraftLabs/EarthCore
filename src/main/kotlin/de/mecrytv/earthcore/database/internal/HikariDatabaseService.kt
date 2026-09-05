package de.mecrytv.earthcore.database.internal

import com.google.gson.Gson
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.mecrytv.earthcore.database.api.DatabaseCredentials
import de.mecrytv.earthcore.database.api.DatabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

class HikariDatabaseService(
    private val credentials: DatabaseCredentials,
    private val gson: Gson,
    private val logger: Logger = Logger.getLogger(HikariDatabaseService::class.java.name),
) : DatabaseService {

    private val schemas = ConcurrentHashMap<Class<*>, ModelSchema<*>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var source: HikariDataSource? = null

    override fun connect() {
        check(source == null) { "connect() wurde bereits aufgerufen" }
        val config = HikariConfig()
        config.jdbcUrl = credentials.jdbcUrl
        config.driverClassName = MARIADB_DRIVER
        config.username = credentials.user
        config.password = credentials.password
        config.maximumPoolSize = credentials.poolSize
        config.connectionTimeout = credentials.connectionTimeoutMs
        config.poolName = "EarthCore"
        config.maxLifetime = 30 * 60 * 1000L
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("useServerPrepStmts", "true")
        source = HikariDataSource(config)
    }

    override fun close() {
        scope.cancel()
        source?.close()
        source = null
    }

    override fun registerModel(modelClass: Class<*>) {
        val schema = ModelSchema.of(modelClass)
        connection().use { connection ->
            connection.createStatement().use { it.executeUpdate(schema.createTable) }
            migrate(connection, schema)
        }
        schemas[modelClass] = schema
    }

    private fun migrate(connection: Connection, schema: ModelSchema<*>) {
        val vorhanden = existingColumns(connection, schema.table)

        for (column in schema.columns.filterNot { it.name in vorhanden }) {
            connection.createStatement().use { it.executeUpdate(schema.addColumn(column)) }
            val hinweis = if (column.definition.endsWith("NOT NULL")) {
                " Bestehende Zeilen bekommen ${column.defaultLiteral}."
            } else {
                ""
            }
            logger.log(Level.INFO, "Spalte `${column.name}` zu `${schema.table}` ergaenzt.$hinweis")
        }

        val ueberzaehlig = vorhanden - schema.columns.map { it.name }.toSet()
        if (ueberzaehlig.isNotEmpty()) {
            logger.log(
                Level.WARNING,
                "`${schema.table}` hat Spalten ohne Feld im Model: $ueberzaehlig. " +
                    "EarthCore loescht nichts - bei Bedarf selbst per DROP COLUMN entfernen.",
            )
        }
    }

    private fun existingColumns(connection: Connection, table: String): Set<String> =
        connection.prepareStatement(COLUMNS).use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { row ->
                buildSet { while (row.next()) add(row.getString(1)) }
            }
        }

    override suspend fun <T : Any> save(entity: T): Unit = withContext(Dispatchers.IO) {
        val schema = schemaOf(entity.javaClass)
        connection().use { connection ->
            connection.prepareStatement(schema.upsert).use { statement ->
                schema.bindAll(statement, entity, gson)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun <T : Any> update(entity: T): Unit = withContext(Dispatchers.IO) {
        val schema = schemaOf(entity.javaClass)
        val sql = schema.update ?: return@withContext
        connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                schema.bindForUpdate(statement, entity, gson)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun <T : Any> delete(entity: T): Unit = withContext(Dispatchers.IO) {
        val schema = schemaOf(entity.javaClass)
        connection().use { connection ->
            connection.prepareStatement(schema.deleteById).use { statement ->
                schema.id.bind(statement, 1, entity, gson)
                statement.executeUpdate()
            }
        }
    }

    override suspend fun <T : Any, ID : Any> findById(modelClass: Class<T>, id: ID): T? =
        withContext(Dispatchers.IO) {
            val schema = schemaOf(modelClass)
            connection().use { connection ->
                connection.prepareStatement(schema.selectById).use { statement ->
                    schema.id.bindValue(statement, 1, id, gson)
                    statement.executeQuery().use { row ->
                        if (row.next()) schema.instantiate { it.read(row, gson) } else null
                    }
                }
            }
        }

    override suspend fun <T : Any> findAll(modelClass: Class<T>): List<T> = withContext(Dispatchers.IO) {
        val schema = schemaOf(modelClass)
        connection().use { connection ->
            connection.prepareStatement(schema.selectAll).use { statement ->
                statement.executeQuery().use { row ->
                    buildList { while (row.next()) add(schema.instantiate { it.read(row, gson) }) }
                }
            }
        }
    }

    override suspend fun execute(sql: String, vararg parameters: Any?): Int = withContext(Dispatchers.IO) {
        connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }
    }

    override fun executeAsync(sql: String, vararg parameters: Any?): CompletableFuture<Int> {
        val kopie = parameters.copyOf()
        return supplyAsync { execute(sql, *kopie) }
    }

    override fun <T : Any> saveAsync(entity: T): CompletableFuture<Void> = runAsync { save(entity) }

    override fun <T : Any> updateAsync(entity: T): CompletableFuture<Void> = runAsync { update(entity) }

    override fun <T : Any> deleteAsync(entity: T): CompletableFuture<Void> = runAsync { delete(entity) }

    override fun <T : Any, ID : Any> findByIdAsync(modelClass: Class<T>, id: ID): CompletableFuture<T?> =
        supplyAsync { findById(modelClass, id) }

    override fun <T : Any> findAllAsync(modelClass: Class<T>): CompletableFuture<List<T>> =
        supplyAsync { findAll(modelClass) }

    private fun runAsync(block: suspend () -> Unit): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        scope.launch {
            try {
                block()
                future.complete(null)
            } catch (ex: Throwable) {
                future.completeExceptionally(ex)
            }
        }
        return future
    }

    private fun <R> supplyAsync(block: suspend () -> R): CompletableFuture<R> {
        val future = CompletableFuture<R>()
        scope.launch {
            try {
                future.complete(block())
            } catch (ex: Throwable) {
                future.completeExceptionally(ex)
            }
        }
        return future
    }

    private fun connection(): Connection =
        (source ?: error("DatabaseService ist nicht verbunden - connect() fehlt")).connection

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> schemaOf(type: Class<out T>): ModelSchema<T> =
        schemas[type] as? ModelSchema<T>
            ?: error("${type.simpleName} ist nicht registriert - registerModel(${type.simpleName}.class) fehlt")

    private companion object {

        const val MARIADB_DRIVER = "org.mariadb.jdbc.Driver"

        const val COLUMNS =
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?"
    }
}
