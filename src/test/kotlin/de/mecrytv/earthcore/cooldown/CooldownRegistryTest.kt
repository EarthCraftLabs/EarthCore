package de.mecrytv.earthcore.cooldown

import de.mecrytv.earthcore.cooldown.internal.CooldownRecord
import de.mecrytv.earthcore.cooldown.internal.DatabaseCooldownRegistry
import de.mecrytv.earthcore.database.api.DatabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeDatabase : DatabaseService {

    val rows = linkedMapOf<String, CooldownRecord>()

    override fun connect() = Unit

    override fun close() = Unit

    override fun registerModel(modelClass: Class<*>) = Unit

    override suspend fun <T : Any> save(entity: T) {
        val record = entity as CooldownRecord
        rows[record.id] = record
    }

    override suspend fun <T : Any> update(entity: T) = save(entity)

    override suspend fun <T : Any> delete(entity: T) {
        rows.remove((entity as CooldownRecord).id)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any, ID : Any> findById(modelClass: Class<T>, id: ID): T? =
        rows[id.toString()] as T?

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> findAll(modelClass: Class<T>): List<T> = rows.values.toList() as List<T>

    override suspend fun execute(sql: String, vararg parameters: Any?): Int = 0

    override fun executeAsync(sql: String, vararg parameters: Any?): CompletableFuture<Int> = unsupported()

    override fun <T : Any> saveAsync(entity: T): CompletableFuture<Void> = unsupported()

    override fun <T : Any> updateAsync(entity: T): CompletableFuture<Void> = unsupported()

    override fun <T : Any> deleteAsync(entity: T): CompletableFuture<Void> = unsupported()

    override fun <T : Any, ID : Any> findByIdAsync(modelClass: Class<T>, id: ID): CompletableFuture<T?> = unsupported()

    override fun <T : Any> findAllAsync(modelClass: Class<T>): CompletableFuture<List<T>> = unsupported()

    private fun <R> unsupported(): R = throw UnsupportedOperationException("im Test nicht genutzt")
}

class CooldownRegistryTest {

    private val spieler = UUID.randomUUID()
    private val anderer = UUID.randomUUID()
    private val database = FakeDatabase()

    private var jetzt = 1_000_000L

    private val registry = DatabaseCooldownRegistry(
        database = database,
        scope = CoroutineScope(Dispatchers.Unconfined),
        logger = Logger.getLogger("test"),
        clock = { jetzt },
    )

    @Test
    fun `starten macht aktiv und schreibt in die datenbank`() {
        registry.start(spieler, "kit", Duration.ofSeconds(30))

        assertTrue(registry.isActive(spieler, "kit"))
        assertEquals(Duration.ofSeconds(30), registry.remaining(spieler, "kit"))
        assertEquals(1, database.rows.size)
        assertEquals(jetzt + 30_000, database.rows.values.single().expiresAt)
    }

    @Test
    fun `nach ablauf ist der cooldown weg und die zeile geloescht`() {
        registry.start(spieler, "kit", Duration.ofSeconds(30))
        jetzt += 30_001

        assertFalse(registry.isActive(spieler, "kit"))
        assertEquals(Duration.ZERO, registry.remaining(spieler, "kit"))
        assertTrue(database.rows.isEmpty())
    }

    @Test
    fun `verlaengern rechnet auf die restzeit auf`() {
        registry.start(spieler, "kit", Duration.ofSeconds(30))
        jetzt += 10_000
        registry.extend(spieler, "kit", Duration.ofSeconds(15))

        assertEquals(Duration.ofSeconds(35), registry.remaining(spieler, "kit"))
    }

    @Test
    fun `verlaengern eines abgelaufenen cooldowns startet neu`() {
        registry.start(spieler, "kit", Duration.ofSeconds(30))
        jetzt += 60_000
        registry.extend(spieler, "kit", Duration.ofSeconds(10))

        assertEquals(Duration.ofSeconds(10), registry.remaining(spieler, "kit"))
    }

    @Test
    fun `loeschen entfernt nur den eigenen eintrag`() {
        registry.start(spieler, "kit", Duration.ofSeconds(30))
        registry.start(anderer, "kit", Duration.ofSeconds(30))

        assertTrue(registry.clear(spieler, "kit"))
        assertFalse(registry.clear(spieler, "kit"))
        assertFalse(registry.isActive(spieler, "kit"))
        assertTrue(registry.isActive(anderer, "kit"))
        assertEquals(1, database.rows.size)
    }

    @Test
    fun `clearAll trifft nur den angegebenen spieler`() {
        registry.start(spieler, "kit", Duration.ofSeconds(30))
        registry.start(spieler, "warp", Duration.ofSeconds(30))
        registry.start(anderer, "kit", Duration.ofSeconds(30))

        assertEquals(2, registry.clearAll(spieler))
        assertEquals(emptySet(), registry.keys(spieler))
        assertContentEquals(listOf("kit"), registry.keys(anderer).toList())
        assertEquals(1, database.rows.size)
    }

    @Test
    fun `laden uebernimmt aktive und raeumt abgelaufene weg`() = runBlocking {
        database.rows["aktiv"] = CooldownRecord("aktiv", spieler, "kit", jetzt + 5_000)
        database.rows["alt"] = CooldownRecord("alt", spieler, "warp", jetzt - 1)

        registry.load()

        assertContentEquals(listOf("kit"), registry.keys(spieler).toList())
        assertContentEquals(listOf("aktiv"), database.rows.keys.toList())
    }

    @Test
    fun `prune raeumt abgelaufene ohne vorherige abfrage weg`() {
        registry.start(spieler, "kit", Duration.ofSeconds(30))
        registry.start(spieler, "warp", Duration.ofHours(1))
        jetzt += 31_000

        assertEquals(1, registry.prune())
        assertContentEquals(listOf("warp"), registry.keys(spieler).toList())
        assertEquals(1, database.rows.size)
    }

    @Test
    fun `dauer null oder negativ wird abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { registry.start(spieler, "kit", Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { registry.start(spieler, "kit", Duration.ofSeconds(-1)) }
        assertFailsWith<IllegalArgumentException> { registry.extend(spieler, "kit", Duration.ZERO) }
    }
}
