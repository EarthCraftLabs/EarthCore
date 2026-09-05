package de.mecrytv.earthcore.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level
import java.util.logging.Logger

class JsonConfigService(
    override val file: File,
    private val defaults: ConfigDefaults,
    private val versioning: ConfigVersioning = ConfigVersioning.NONE,
    private val gson: Gson = defaultGson(),
    private val placeholderResolver: PlaceholderResolver = PatternPlaceholderResolver(),
    private val logger: Logger = Logger.getLogger(JsonConfigService::class.java.name),
) : ConfigService {

    constructor(
        file: File,
        defaults: ConfigDefaults,
        gson: Gson,
        placeholderResolver: PlaceholderResolver,
        logger: Logger,
    ) : this(file, defaults, ConfigVersioning.NONE, gson, placeholderResolver, logger)

    private var root: JsonObject = JsonObject()

    init {
        reload()
    }

    @Synchronized
    override fun reload() {
        val defaultTree = defaults.load(gson)
        val onDisk = read()
        root = if (onDisk == null) defaultTree else merge(defaultTree, upgrade(onDisk))
        if (versioning.enabled) root.addProperty(versioning.key, versioning.current)
        if (root != onDisk) write()
    }

    private fun upgrade(onDisk: JsonObject): JsonObject {
        val gefunden = onDisk.get(versioning.key)?.takeIf { it.isJsonPrimitive }?.asInt ?: 1
        if (gefunden >= versioning.current) return onDisk

        var aktuell = onDisk
        for ((schritt, migration) in versioning.pending(gefunden)) {
            val naechster = aktuell.deepCopy()
            try {
                migration.migrate(naechster)
            } catch (ex: Exception) {
                logger.log(Level.SEVERE, "Migration von " + file.name + " auf Version " + schritt + " fehlgeschlagen.", ex)
                throw ex
            }
            aktuell = naechster
            logger.info(file.name + " auf Version " + schritt + " gehoben.")
        }
        return aktuell
    }

    @Synchronized
    override fun save() = write()

    @Synchronized
    override fun resetToDefaults() {
        root = defaults.load(gson)
        if (versioning.enabled) root.addProperty(versioning.key, versioning.current)
        write()
    }

    @Synchronized
    override fun contains(path: String): Boolean = find(path) != null

    @Synchronized
    override fun find(path: String): JsonElement? = JsonPaths.find(root, path)

    @Synchronized
    override fun keys(path: String): Set<String> =
        (find(path) as? JsonObject)?.keySet()?.toSet() ?: emptySet()

    @Synchronized
    override fun <T : Any> get(path: String, type: Class<T>): T? {
        val element = find(path) ?: return null
        return runCatching { gson.fromJson(element, type) }
            .onFailure { warnMismatch(path, type.simpleName, it) }
            .getOrNull()
    }

    override fun <T : Any> getOrDefault(path: String, type: Class<T>, fallback: T): T =
        get(path, type) ?: fallback

    @Synchronized
    override fun <T : Any> getList(path: String, type: Class<T>): List<T> {
        val element = find(path)?.takeIf { it.isJsonArray } ?: return emptyList()
        val listType = TypeToken.getParameterized(List::class.java, type).type
        return runCatching { gson.fromJson<List<T>>(element, listType) }
            .onFailure { warnMismatch(path, "List<" + type.simpleName + ">", it) }
            .getOrNull()
            .orEmpty()
    }

    @Synchronized
    override fun getString(path: String, vararg placeholders: Pair<String, Any?>): String? {
        val raw = find(path)?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        return format(raw, *placeholders)
    }

    override fun getStringList(path: String, vararg placeholders: Pair<String, Any?>): List<String> =
        getList(path, String::class.java).map { format(it, *placeholders) }

    override fun getBoolean(path: String, fallback: Boolean): Boolean = primitive(path, fallback) { it.asBoolean }

    override fun getInt(path: String, fallback: Int): Int = primitive(path, fallback) { it.asInt }

    override fun getLong(path: String, fallback: Long): Long = primitive(path, fallback) { it.asLong }

    override fun getDouble(path: String, fallback: Double): Double = primitive(path, fallback) { it.asDouble }

    @Synchronized
    override fun <T : Any> asModel(type: Class<T>): T = gson.fromJson(root, type)

    @Synchronized
    override fun set(path: String, value: Any?) = JsonPaths.set(root, path, gson.toJsonTree(value))

    @Synchronized
    override fun delete(path: String): Boolean = JsonPaths.remove(root, path)

    override fun format(text: String, vararg placeholders: Pair<String, Any?>): String =
        placeholderResolver.resolve(text, placeholders.toMap())

    private inline fun <T> primitive(path: String, fallback: T, read: (JsonElement) -> T): T {
        val element = find(path) ?: return fallback
        return runCatching { read(element) }.getOrDefault(fallback)
    }

    private fun warnMismatch(path: String, expected: String, cause: Throwable) {
        logger.log(Level.WARNING, "Wert '" + path + "' in " + file.name + " passt nicht zu " + expected + ".", cause)
    }

    private fun merge(defaults: JsonObject, actual: JsonObject): JsonObject {
        val merged = actual.deepCopy()
        for ((key, defaultValue) in defaults.entrySet()) {
            val existing = merged.get(key)
            when {
                existing == null || existing.isJsonNull -> merged.add(key, defaultValue.deepCopy())
                defaultValue is JsonObject && existing is JsonObject -> merged.add(key, merge(defaultValue, existing))
            }
        }
        return merged
    }

    private fun read(): JsonObject? {
        if (!file.isFile) return null
        return try {
            val parsed = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
            parsed as? JsonObject ?: error("Wurzel von " + file.name + " ist kein JSON-Objekt")
        } catch (ex: Exception) {
            quarantine(ex)
            null
        }
    }

    private fun quarantine(cause: Exception) {
        val backup = File(file.parentFile, file.name + ".broken-" + System.currentTimeMillis())
        runCatching { file.copyTo(backup, overwrite = true) }
        logger.log(
            Level.SEVERE,
            file.name + " konnte nicht gelesen werden. Backup: " + backup.name + ". Es gelten die Standardwerte.",
            cause,
        )
    }

    private fun write() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writer(Charsets.UTF_8).use { gson.toJson(root, it) }
        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {

        fun defaultGson(pretty: Boolean = true): Gson = GsonBuilder()
            .disableHtmlEscaping()
            .apply { if (pretty) setPrettyPrinting() }
            .create()
    }
}
