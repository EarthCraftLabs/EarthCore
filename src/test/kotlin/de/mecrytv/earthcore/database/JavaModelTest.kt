package de.mecrytv.earthcore.database

import de.mecrytv.earthcore.database.internal.ModelSchema
import de.mecrytv.earthcore.javafixtures.JavaProfile
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JavaModelTest {

    private val schema = ModelSchema.of(JavaProfile::class.java)

    @Test
    fun `annotationen auf java-feldern werden gelesen`() {
        assertEquals("java_profiles", schema.table)
        assertEquals("uuid", schema.id.name)
        assertEquals(
            listOf("uuid", "last_known_name", "coins", "banned", "purchases"),
            schema.columns.map { it.name },
        )
    }

    @Test
    fun `transiente felder werden nicht persistiert`() {
        assertFalse(schema.columns.any { it.name == "ignoriert" })
    }

    @Test
    fun `java-typen werden korrekt uebersetzt`() {
        assertEquals(
            "CREATE TABLE IF NOT EXISTS `java_profiles` (" +
                "`uuid` CHAR(36), " +
                "`last_known_name` VARCHAR(255), " +
                "`coins` BIGINT NOT NULL, " +
                "`banned` BOOLEAN NOT NULL, " +
                "`purchases` LONGTEXT, " +
                "PRIMARY KEY (`uuid`))",
            schema.createTable,
        )
    }

    @Test
    fun `ein java-pojo wird ueber no-arg-konstruktor und felder befuellt`() {
        val id = UUID.randomUUID()
        val values = mapOf(
            "uuid" to id,
            "last_known_name" to "MecryTV",
            "coins" to 1_500L,
            "banned" to false,
            "purchases" to listOf("schwert", "bogen"),
        )

        val profile = schema.instantiate { values[it.name] }

        assertEquals(id, profile.uuid)
        assertEquals("MecryTV", profile.name)
        assertEquals(1_500L, profile.coins)
        assertFalse(profile.isBanned)
        assertEquals(listOf("schwert", "bogen"), profile.purchases)
        assertNull(profile.ignoriert)
    }

    @Test
    fun `primitive java-felder sind NOT NULL, objekte nicht`() {
        assertTrue(schema.createTable.contains("`coins` BIGINT NOT NULL"))
        assertTrue(schema.createTable.contains("`last_known_name` VARCHAR(255),"))
    }
}
