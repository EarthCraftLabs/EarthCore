package de.mecrytv.earthcore.database

import de.mecrytv.earthcore.database.annotations.Column
import de.mecrytv.earthcore.database.annotations.JsonColumn
import de.mecrytv.earthcore.database.annotations.PrimaryKey
import de.mecrytv.earthcore.database.annotations.Table
import de.mecrytv.earthcore.database.internal.ModelSchema
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelSchemaTest {

    @Table("player_profiles")
    data class PlayerProfile(
        @PrimaryKey val uuid: UUID,
        @Column("last_known_name") val name: String,
        val coins: Long = 0,
        val banned: Boolean = false,
        @JsonColumn val unlockedRegions: List<String> = emptyList(),
        val settings: ProfileSettings = ProfileSettings(),
    )

    data class ProfileSettings(val language: String = "de", val scoreboard: Boolean = true)

    private val schema = ModelSchema.of(PlayerProfile::class.java)

    @Test
    fun `create table uebersetzt jeden feldtyp`() {
        assertEquals(
            "CREATE TABLE IF NOT EXISTS `player_profiles` (" +
                "`uuid` CHAR(36) NOT NULL, " +
                "`last_known_name` VARCHAR(255) NOT NULL, " +
                "`coins` BIGINT NOT NULL, " +
                "`banned` BOOLEAN NOT NULL, " +
                "`unlockedRegions` LONGTEXT NOT NULL, " +
                "`settings` LONGTEXT NOT NULL, " +
                "PRIMARY KEY (`uuid`))",
            schema.createTable,
        )
    }

    @Test
    fun `save schreibt ein upsert, update laesst den schluessel in ruhe`() {
        assertTrue(schema.upsert.startsWith("INSERT INTO `player_profiles`"))
        assertTrue(
            schema.upsert.endsWith(
                "ON DUPLICATE KEY UPDATE `last_known_name` = VALUES(`last_known_name`), " +
                    "`coins` = VALUES(`coins`), `banned` = VALUES(`banned`), " +
                    "`unlockedRegions` = VALUES(`unlockedRegions`), `settings` = VALUES(`settings`)",
            ),
        )
        assertEquals(6, schema.upsert.count { it == '?' })

        assertTrue(schema.update!!.endsWith(" WHERE `uuid` = ?"))
        assertEquals(6, schema.update!!.count { it == '?' })
    }

    @Test
    fun `select filtert auf den primaerschluessel`() {
        assertEquals(
            "SELECT `uuid`, `last_known_name`, `coins`, `banned`, `unlockedRegions`, `settings` " +
                "FROM `player_profiles` WHERE `uuid` = ? LIMIT 1",
            schema.selectById,
        )
    }

    @Test
    fun `instantiate baut die entity aus spaltenwerten`() {
        val id = UUID.randomUUID()
        val values = mapOf(
            "uuid" to id,
            "last_known_name" to "MecryTV",
            "coins" to 1_500L,
            "banned" to false,
            "unlockedRegions" to listOf("spawn", "nether"),
            "settings" to ProfileSettings(language = "en"),
        )

        val profile = schema.instantiate { values[it.name] }

        assertEquals(
            PlayerProfile(id, "MecryTV", 1_500, false, listOf("spawn", "nether"), ProfileSettings("en")),
            profile,
        )
    }

    @Table("no_key")
    data class WithoutKey(val name: String)

    @Table("two_keys")
    data class TwoKeys(@PrimaryKey val a: Int, @PrimaryKey val b: Int)

    @Table("bad name; DROP TABLE x")
    data class BadTable(@PrimaryKey val id: Int)

    data class MissingTable(@PrimaryKey val id: Int)

    @Table("bad_column")
    data class BadColumn(@PrimaryKey val id: Int, @Column("a`b") val value: String)

    @Test
    fun `modelle ohne genau einen primaerschluessel werden abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { ModelSchema.of(WithoutKey::class.java) }
        assertFailsWith<IllegalArgumentException> { ModelSchema.of(TwoKeys::class.java) }
    }

    @Test
    fun `bezeichner mit sql-sonderzeichen werden abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { ModelSchema.of(BadTable::class.java) }
        assertFailsWith<IllegalArgumentException> { ModelSchema.of(BadColumn::class.java) }
    }

    @Test
    fun `fehlendes table wirft`() {
        assertFailsWith<IllegalStateException> { ModelSchema.of(MissingTable::class.java) }
    }

    @Table("nullables")
    data class Nullable(@PrimaryKey val id: Int, val note: String?, @JsonColumn val tags: List<String>?)

    @Test
    fun `nullable felder werden ohne NOT NULL angelegt und akzeptieren null`() {
        val nullableSchema = ModelSchema.of(Nullable::class.java)
        assertTrue(nullableSchema.createTable.contains("`note` VARCHAR(255),"))
        assertTrue(nullableSchema.createTable.contains("`tags` LONGTEXT,"))

        val entity = nullableSchema.instantiate { if (it.name == "id") 7 else null }
        assertEquals(7, entity.id)
        assertNull(entity.note)
        assertNull(entity.tags)
    }
}
