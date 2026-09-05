package de.mecrytv.earthcore.cooldown

import de.mecrytv.earthcore.cooldown.internal.DurationFormat
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class DurationFormatTest {

    @Test
    fun `nullwerte fallen raus, sekunden bleiben immer`() {
        assertEquals("0s", DurationFormat.humanize(Duration.ZERO))
        assertEquals("30s", DurationFormat.humanize(Duration.ofSeconds(30)))
        assertEquals("5m", DurationFormat.humanize(Duration.ofMinutes(5)))
        assertEquals("1m 30s", DurationFormat.humanize(Duration.ofSeconds(90)))
        assertEquals("2h", DurationFormat.humanize(Duration.ofHours(2)))
        assertEquals("1h 1m 1s", DurationFormat.humanize(Duration.ofSeconds(3661)))
        assertEquals("24h", DurationFormat.humanize(Duration.ofDays(1)))
    }

    @Test
    fun `angebrochene sekunden werden aufgerundet`() {
        assertEquals("2s", DurationFormat.humanize(Duration.ofMillis(1500)))
        assertEquals("1s", DurationFormat.humanize(Duration.ofMillis(1)))
    }

    @Test
    fun `negative dauer meldet null`() {
        assertEquals("0s", DurationFormat.humanize(Duration.ofSeconds(-5)))
    }
}
