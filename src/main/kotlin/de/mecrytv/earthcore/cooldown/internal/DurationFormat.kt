package de.mecrytv.earthcore.cooldown.internal

import java.time.Duration
import kotlin.math.ceil

internal object DurationFormat {

    fun humanize(duration: Duration): String {
        val total = ceil(duration.toMillis() / 1000.0).toLong().coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return buildList {
            if (hours > 0) add("${hours}h")
            if (minutes > 0) add("${minutes}m")
            if (seconds > 0 || isEmpty()) add("${seconds}s")
        }.joinToString(" ")
    }
}
