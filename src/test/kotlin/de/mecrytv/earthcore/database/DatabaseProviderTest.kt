package de.mecrytv.earthcore.database

import com.google.gson.Gson
import de.mecrytv.earthcore.database.api.DatabaseCredentials
import de.mecrytv.earthcore.database.internal.HikariDatabaseProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseProviderTest {

    private val credentials = DatabaseCredentials(
        host = "db.example.invalid",
        port = 3307,
        database = "earthcore",
        user = "earthcore",
    )

    @Test
    fun `jede datenbank bekommt ihre eigene jdbc-url`() {
        assertEquals("jdbc:mariadb://db.example.invalid:3307/earthcore", credentials.jdbcUrl)
        assertEquals("jdbc:mariadb://db.example.invalid:3307/", credentials.serverUrl)
        assertEquals(
            "jdbc:mariadb://db.example.invalid:3307/earthshop",
            credentials.copy(database = "earthshop").jdbcUrl,
        )
    }

    @Test
    fun `ungueltige datenbanknamen werden vor dem verbindungsversuch abgelehnt`() {
        val provider = HikariDatabaseProvider(credentials, Gson())

        for (name in listOf("earth shop", "earthshop`; DROP DATABASE x", "earth-shop", "", "a".repeat(65))) {
            assertFailsWith<IllegalArgumentException>("'$name' haette abgelehnt werden muessen") {
                provider.of(name)
            }
        }
        assertTrue(provider.names().isEmpty())
    }
}
