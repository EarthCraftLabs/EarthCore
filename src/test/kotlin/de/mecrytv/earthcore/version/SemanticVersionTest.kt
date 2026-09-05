package de.mecrytv.earthcore.version

import de.mecrytv.earthcore.version.internal.PluginCoreVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemanticVersionTest {

    private fun core(version: String) = PluginCoreVersion(version)

    @Test
    fun `zehn ist groesser als neun, nicht kleiner`() {
        assertTrue(core("1.10.0").isAtLeast("1.9.0"))
        assertFalse(core("1.9.0").isAtLeast("1.10.0"))
        assertTrue(core("2.0.0").isAtLeast("1.99.99"))
    }

    @Test
    fun `gleiche versionen erfuellen die anforderung`() {
        assertTrue(core("1.9.0").isAtLeast("1.9.0"))
    }

    @Test
    fun `fehlende stellen zaehlen als null`() {
        assertTrue(core("1.9").isAtLeast("1.9.0"))
        assertTrue(core("1.9.0").isAtLeast("1.9"))
        assertFalse(core("1.9").isAtLeast("1.9.1"))
        assertTrue(core("2").isAtLeast("1.9.9"))
    }

    @Test
    fun `eine vorabversion ist aelter als die fertige`() {
        assertFalse(core("1.9.0-SNAPSHOT").isAtLeast("1.9.0"))
        assertTrue(core("1.9.0").isAtLeast("1.9.0-SNAPSHOT"))
        assertTrue(core("1.9.0-SNAPSHOT").isAtLeast("1.8.9"))
    }

    @Test
    fun `build-metadaten werden ignoriert`() {
        assertTrue(core("1.9.0+build7").isAtLeast("1.9.0"))
    }

    @Test
    fun `requireAtLeast wirft mit lesbarer meldung`() {
        core("1.9.0").requireAtLeast("1.8.0")

        val fehler = assertFailsWith<IllegalStateException> { core("1.7.0").requireAtLeast("1.9.0") }
        assertTrue(fehler.message!!.contains("1.7.0"), fehler.message!!)
        assertTrue(fehler.message!!.contains("1.9.0"), fehler.message!!)
    }

    @Test
    fun `unsinnige versionen werden abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { core("keine-version") }
        assertFailsWith<IllegalArgumentException> { core("1..0") }
        assertFailsWith<IllegalArgumentException> { core("") }
    }

    @Test
    fun `die eigene version bleibt unveraendert lesbar`() {
        assertEquals("1.9.0-SNAPSHOT", core("1.9.0-SNAPSHOT").version)
    }
}
