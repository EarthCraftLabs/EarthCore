package de.mecrytv.earthcore.logging.internal

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.mecrytv.earthcore.logging.api.DiscordRoute
import de.mecrytv.earthcore.logging.api.LogCategory
import de.mecrytv.earthcore.logging.api.LogEntry
import de.mecrytv.earthcore.logging.api.LogSink
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level
import java.util.logging.Logger

data class WebhookResponse(val status: Int, val retryAfterMillis: Long = 0)

fun interface WebhookSender {

    fun send(url: String, body: String): WebhookResponse
}

class DiscordSink(
    private val routes: List<DiscordRoute>,
    private val sender: WebhookSender,
    private val logger: Logger,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis,
) : LogSink {

    override val name: String = "discord"

    private val queues: Map<DiscordRoute, ConcurrentLinkedQueue<LogEntry>> =
        routes.filter { it.url.isNotBlank() }.associateWith { ConcurrentLinkedQueue() }

    private val gesperrtBis = HashMap<DiscordRoute, Long>()

    init {
        queues.keys.filter { it.categories.size != it.categories.filterNotNull().size }.forEach {
            logger.warning(
                "Discord-Webhook " + mask(it.url) + " nennt unbekannte Kategorien. Erlaubt: " +
                    LogCategory.entries.joinToString(),
            )
        }
    }

    override fun accept(entry: LogEntry) {
        for ((route, queue) in queues) {
            if (!route.accepts(entry)) continue
            if (queue.size >= MAX_QUEUE) {
                queue.poll()
                continue
            }
            queue += entry
        }
    }

    override fun close() = queues.values.forEach { it.clear() }

    fun flush(): Int {
        var gesendet = 0
        for ((route, queue) in queues) {
            if (queue.isEmpty()) continue
            if (clock() < (gesperrtBis[route] ?: 0L)) continue

            val stapel = buildList { while (size < MAX_EMBEDS) add(queue.poll() ?: break) }
            if (stapel.isEmpty()) continue

            val antwort = runCatching { sender.send(route.url, payload(route, stapel)) }
                .getOrElse { fehler ->
                    logger.log(Level.WARNING, "Discord-Webhook ${mask(route.url)} nicht erreichbar.", fehler)
                    WebhookResponse(-1)
                }

            when {
                antwort.status in 200..299 -> gesendet += stapel.size
                antwort.status == 429 -> {
                    gesperrtBis[route] = clock() + maxOf(antwort.retryAfterMillis, MIN_BACKOFF)
                    stapel.asReversed().forEach { queue.offer(it) }
                }
                else -> logger.log(
                    Level.WARNING,
                    "Discord-Webhook ${mask(route.url)} antwortete mit ${antwort.status}, " +
                        "${stapel.size} Eintraege verworfen.",
                )
            }
        }
        return gesendet
    }

    fun payload(route: DiscordRoute, entries: List<LogEntry>): String {
        val embeds = JsonArray()
        entries.forEach { embeds.add(embed(it)) }
        return gson.toJson(
            JsonObject().apply {
                addProperty("username", route.username)
                add("embeds", embeds)
            },
        )
    }

    private fun embed(entry: LogEntry): JsonObject = JsonObject().apply {
        addProperty("title", "${entry.level.name} - ${entry.category}")
        addProperty("description", entry.message.take(DESCRIPTION_LIMIT))
        addProperty("color", entry.level.color)
        addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(entry.timestamp))
        add("footer", JsonObject().apply { addProperty("text", entry.plugin.ifEmpty { "EarthCore" }) })

        val felder = JsonArray()
        entry.actor?.let { felder.add(field("Ausgeloest von", it.toString(), true)) }
        entry.details.entries.take(MAX_FIELDS).forEach {
            felder.add(field(it.key, (it.value?.toString() ?: "null").take(FIELD_LIMIT), true))
        }
        entry.error?.let {
            felder.add(field("Fehler", "```" + LogRecord.stacktrace(it).take(TRACE_LIMIT) + "```", false))
        }
        if (felder.size() > 0) add("fields", felder)
    }

    private fun field(name: String, value: String, inline: Boolean): JsonObject = JsonObject().apply {
        addProperty("name", name.take(FIELD_NAME_LIMIT))
        addProperty("value", value.ifEmpty { "-" })
        addProperty("inline", inline)
    }

    companion object {

        const val MAX_EMBEDS = 10
        const val MAX_QUEUE = 500
        const val MIN_BACKOFF = 2_000L

        private const val DESCRIPTION_LIMIT = 2000
        private const val FIELD_LIMIT = 1000
        private const val FIELD_NAME_LIMIT = 256
        private const val TRACE_LIMIT = 900
        private const val MAX_FIELDS = 8

        fun mask(url: String): String = "..." + url.takeLast(6)

        fun timestamp(millis: Long): String =
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
    }
}
