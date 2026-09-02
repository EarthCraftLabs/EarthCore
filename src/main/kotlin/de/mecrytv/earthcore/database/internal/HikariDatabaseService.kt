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

class HikariDatabaseService(
    private val credentials: DatabaseCredentials,
    private val gson: Gson,
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
        }
        schemas[modelClass] = schema
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
    }
}
