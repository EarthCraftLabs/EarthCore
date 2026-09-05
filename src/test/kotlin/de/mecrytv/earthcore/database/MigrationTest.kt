package de.mecrytv.earthcore.database

import de.mecrytv.earthcore.database.annotations.Column
import de.mecrytv.earthcore.database.annotations.JsonColumn
import de.mecrytv.earthcore.database.annotations.PrimaryKey
import de.mecrytv.earthcore.database.annotations.Table
import de.mecrytv.earthcore.database.internal.ModelSchema
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationTest {

    enum class Rang { SPIELER, TEAM }

    @Table("migrations_probe")
    data class Probe(
        @PrimaryKey val uuid: UUID,
        @Column("last_known_name") val name: String,
        val notiz: String?,
        val coins: Long,
        val aktiv: Boolean,
        val quote: Double,
        val rang: Rang,
        @JsonColumn val eintraege: List<String>,
        val einstellungen: Einstellungen,
    )

    data class Einstellungen(val sprache: String = "de")

    private val schema = ModelSchema.of(Probe::class.java)

    private fun alter(name: String) = schema.addColumn(schema.columns.single { it.name == name })

    @Test
    fun `nullbare spalten bekommen kein NOT NULL und keinen default`() {
        assertEquals("ALTER TABLE `migrations_probe` ADD COLUMN `notiz` VARCHAR(255)", alter("notiz"))
    }

    @Test
    fun `zahlen und wahrheitswerte bekommen null als default`() {
        assertEquals(
            "ALTER TABLE `migrations_probe` ADD COLUMN `coins` BIGINT NOT NULL DEFAULT 0",
            alter("coins"),
        )
        assertEquals(
            "ALTER TABLE `migrations_probe` ADD COLUMN `aktiv` BOOLEAN NOT NULL DEFAULT 0",
            alter("aktiv"),
        )
        assertEquals(
            "ALTER TABLE `migrations_probe` ADD COLUMN `quote` DOUBLE NOT NULL DEFAULT 0",
            alter("quote"),
        )
    }

    @Test
    fun `texte bekommen den leeren string`() {
        assertEquals(
            "ALTER TABLE `migrations_probe` ADD COLUMN `last_known_name` VARCHAR(255) NOT NULL DEFAULT ''",
            alter("last_known_name"),
        )
        assertEquals(
            "ALTER TABLE `migrations_probe` ADD COLUMN `uuid` CHAR(36) NOT NULL DEFAULT ''",
            alter("uuid"),
        )
    }

    @Test
    fun `ein enum bekommt seine erste konstante, nicht den leeren string`() {
        assertEquals(
            "ALTER TABLE `migrations_probe` ADD COLUMN `rang` VARCHAR(255) NOT NULL DEFAULT 'SPIELER'",
            alter("rang"),
        )
    }

    @Test
    fun `json-spalten bekommen ein passendes leeres literal`() {
        assertEquals(
            "ALTER TABLE `migrations_probe` ADD COLUMN `eintraege` LONGTEXT NOT NULL DEFAULT '[]'",
            alter("eintraege"),
        )
        assertEquals(
            "ALTER TABLE `migrations_probe` ADD COLUMN `einstellungen` LONGTEXT NOT NULL DEFAULT '{}'",
            alter("einstellungen"),
        )
    }

    @Test
    fun `die defaults sind so gewaehlt, dass sie wieder einlesbar sind`() {
        val leer = schema.columns.associate { it.name to it.defaultLiteral.trim('\'') }

        assertTrue(Rang.entries.any { it.name == leer.getValue("rang") })
        assertEquals("[]", leer.getValue("eintraege"))
        assertEquals("{}", leer.getValue("einstellungen"))
    }
}
