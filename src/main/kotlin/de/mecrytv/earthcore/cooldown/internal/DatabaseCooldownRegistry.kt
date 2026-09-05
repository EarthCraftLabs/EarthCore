package de.mecrytv.earthcore.cooldown.internal

import de.mecrytv.earthcore.cooldown.api.CooldownRegistry
import de.mecrytv.earthcore.database.api.DatabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

class DatabaseCooldownRegistry(
    private val database: DatabaseService,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) : CooldownRegistry {

    private val active = ConcurrentHashMap<String, CooldownRecord>()

    suspend fun load() {
        val now = clock()
        val stored = database.findAll(CooldownRecord::class.java)
        for (record in stored) {
            if (record.expiresAt > now) active[record.id] = record else database.delete(record)
        }
        logger.info("Cooldowns: ${active.size} aktiv, ${stored.size - active.size} abgelaufen entfernt")
    }

    override fun start(subject: UUID, key: String, duration: Duration) {
        require(!duration.isNegative && !duration.isZero) { "Cooldown-Dauer muss groesser als 0 sein" }
        store(CooldownRecord(CooldownRecord.id(subject, key), subject, key, clock() + duration.toMillis()))
    }

    override fun extend(subject: UUID, key: String, by: Duration) {
        require(!by.isNegative && !by.isZero) { "Verlaengerung muss groesser als 0 sein" }
        val id = CooldownRecord.id(subject, key)
        val base = maxOf(active[id]?.expiresAt ?: 0L, clock())
        store(CooldownRecord(id, subject, key, base + by.toMillis()))
    }

    override fun clear(subject: UUID, key: String): Boolean =
        active.remove(CooldownRecord.id(subject, key))?.also { forget(it) } != null

    override fun clearAll(subject: UUID): Int = mine(subject)
        .mapNotNull { active.remove(it.id) }
        .onEach { forget(it) }
        .size

    override fun isActive(subject: UUID, key: String): Boolean = !remaining(subject, key).isZero

    override fun remaining(subject: UUID, key: String): Duration {
        val record = active[CooldownRecord.id(subject, key)] ?: return Duration.ZERO
        val left = record.expiresAt - clock()
        if (left <= 0) {
            active.remove(record.id)
            forget(record)
            return Duration.ZERO
        }
        return Duration.ofMillis(left)
    }

    override fun keys(subject: UUID): Set<String> {
        val now = clock()
        return mine(subject).filter { it.expiresAt > now }.map { it.key }.toSet()
    }

    fun prune(): Int {
        val now = clock()
        return active.values.filter { it.expiresAt <= now }
            .mapNotNull { active.remove(it.id) }
            .onEach { forget(it) }
            .size
    }

    private fun mine(subject: UUID): List<CooldownRecord> = active.values.filter { it.subject == subject }

    private fun store(record: CooldownRecord) {
        active[record.id] = record
        scope.launch {
            runCatching { database.save(record) }
                .onFailure { logger.log(Level.WARNING, "Cooldown '${record.id}' nicht gespeichert", it) }
        }
    }

    private fun forget(record: CooldownRecord) {
        scope.launch {
            runCatching { database.delete(record) }
                .onFailure { logger.log(Level.WARNING, "Cooldown '${record.id}' nicht geloescht", it) }
        }
    }
}
