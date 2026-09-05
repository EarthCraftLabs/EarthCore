# EarthCore für Kotlin-Plugins

Alles, was du brauchst, um ein Kotlin-Plugin auf EarthCore aufzusetzen.
Serverseitige Einrichtung steht im [Haupt-README](../README.md).

> Java-Plugin? → [java.md](java.md)

---

## Inhalt

- [Projekt aufsetzen](#projekt-aufsetzen)
- [Hauptklasse](#hauptklasse)
- [Models](#models)
- [Datenbankzugriff](#datenbankzugriff)
- [Listener](#listener)
- [Commands](#commands)
- [Cooldowns](#cooldowns)
- [Konfiguration](#konfiguration)
- [Versionierung](#versionierung)
- [Annotationen](#annotationen)
- [API-Referenz](#api-referenz)
- [Fallstricke](#fallstricke)

---

## Projekt aufsetzen

### `gradle.properties`

```properties
kotlin.code.style=official
kotlin.stdlib.default.dependency=false
```

Die zweite Zeile ist wichtig: EarthCore bringt die Kotlin-Runtime bereits mit.
Ohne sie landet `kotlin-stdlib` ein zweites Mal in deinem Jar.

### `build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm") version "2.3.21"
}

group = "de.mecrytv"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("de.mecrytv:earthcore:1.9.0")
}

kotlin {
    jvmToolchain(21)
}
```

`compileOnly` statt `implementation`: die Klassen liegen zur Laufzeit bereits im
EarthCore-Jar auf dem Server. Du brauchst **kein** Shadow-Plugin — dein fertiges
Jar bleibt bei wenigen Kilobyte, weil Kotlin, Coroutines, HikariCP und der
MariaDB-Treiber alle aus EarthCore kommen.

### `src/main/resources/plugin.yml`

```yml
name: EarthShop
version: '1.0.0'
main: de.mecrytv.earthshop.EarthShop
api-version: '1.21'
depend:
  - EarthCore
```

`depend` ist **Pflicht**. Es regelt die Ladereihenfolge und sorgt dafür, dass dein
ClassLoader die Klassen aus EarthCore sieht. Ohne den Eintrag bekommst du beim
Start `NoClassDefFoundError`.

### Empfohlene Paketstruktur

```
de.mecrytv.earthshop
├── EarthShop.kt          Hauptklasse
├── model/                @Table-Datenklassen
├── listener/             @AutoListener-Klassen
└── command/              @AutoCommand-Klassen
```

Die Ordnernamen sind frei wählbar — du gibst sie beim Registrieren selbst an.

---

## Hauptklasse

```kotlin
package de.mecrytv.earthshop

import de.mecrytv.earthcore.config.ConfigDefaults
import de.mecrytv.earthcore.config.ConfigService
import de.mecrytv.earthcore.config.JsonConfigService
import de.mecrytv.earthcore.config.getOrDefault
import de.mecrytv.earthcore.database.api.DatabaseProvider
import de.mecrytv.earthcore.database.api.DatabaseService
import de.mecrytv.earthcore.registry.api.AutoRegistrar
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class ShopConfig(
    val database: String = "earthshop",
    val startGuthaben: Long = 100,
    val debug: Boolean = false,
)

class EarthShop : JavaPlugin() {

    lateinit var configService: ConfigService
        private set

    lateinit var coreConfig: ConfigService
        private set

    lateinit var database: DatabaseService
        private set

    override fun onEnable() {
        configService = JsonConfigService(
            file = File(dataFolder, "config.json"),
            defaults = ConfigDefaults.model(ShopConfig()),
            logger = logger,
        )

        coreConfig = server.servicesManager.load(ConfigService::class.java)
            ?: error("EarthCore ist nicht geladen")

        val databases = server.servicesManager.load(DatabaseProvider::class.java)!!
        database = databases.of(configService.getOrDefault("database", "earthshop"))

        val registrar = server.servicesManager.load(AutoRegistrar::class.java)!!
        val summary = registrar.register(
            this,
            database,
            "de.mecrytv.earthshop.model",
            "de.mecrytv.earthshop.listener",
            "de.mecrytv.earthshop.command",
        )

        logger.info("Gestartet: $summary")
    }
}
```

Vier Schritte:

1. **`JsonConfigService(...)`** legt deine eigene `plugins/EarthShop/config.json`
   an. Die Feldwerte von `ShopConfig` sind gleichzeitig Schema und Standardwerte:
   fehlende Schlüssel werden beim Start ergänzt, vom Nutzer gesetzte Werte bleiben
   unangetastet. Neue Einstellungen fügst du nur in der Datenklasse hinzu.

   > Nenn die Property **nicht** `config` — `JavaPlugin` hat schon ein
   > `getConfig()` für die YAML-Konfiguration, und Kotlin meldet einen Konflikt.

2. **`load(ConfigService::class.java)`** gibt dir EarthCores eigene Konfiguration,
   falls du an gemeinsame Werte wie `settings.namespace` musst. Rein optional —
   für plugin-eigene Einstellungen nimmst du Schritt 1.

3. **`databases.of(...)`** gibt dir einen `DatabaseService` auf deine **eigene**
   Datenbank. Eigener Verbindungspool, eigene Tabellen — nichts landet in
   `earthcore` oder bei einem anderen Plugin. Existiert die Datenbank noch nicht,
   legt EarthCore sie an. Host, Port, Benutzer und Passwort kommen aus EarthCores
   `config.json`; pro Plugin unterscheidet sich nur der Name. Den holst du dir hier
   aus der eigenen Config, damit ein Serverbetreiber ihn ändern kann, ohne dein
   Plugin neu zu bauen.

4. **`registrar.register(...)`** durchsucht die angegebenen Packages und meldet
   alles an, was eine passende Annotation trägt.

Die Reihenfolge ist Absicht: die Konfiguration zuerst, weil der Datenbankname aus
ihr kommt.

Werte liest du danach überall über den `configService`:

```kotlin
val startGuthaben = configService.getLong("startGuthaben", 100)
val debug = configService.getBoolean("debug")
val alles = configService.asModel<ShopConfig>()
```

Mehr dazu unter [Konfiguration](#konfiguration).

Ohne Package-Angabe wird das Package deiner Hauptklasse samt Unterpaketen
durchsucht:

```kotlin
registrar.register(this, database)
```

Hat dein Plugin gar keine Models, lässt du den `database`-Parameter weg:

```kotlin
registrar.register(this, "de.mecrytv.earthshop.listener")
```

Aufräumen musst du nichts — EarthCore schließt beim Serverstopp alle Pools.

---

## Models

Ein Model ist eine `data class` mit `@Table` und genau einem `@PrimaryKey`-Feld.

```kotlin
package de.mecrytv.earthshop.model

import de.mecrytv.earthcore.database.annotations.Column
import de.mecrytv.earthcore.database.annotations.JsonColumn
import de.mecrytv.earthcore.database.annotations.PrimaryKey
import de.mecrytv.earthcore.database.annotations.Table
import java.util.UUID

@Table("shop_profiles")
data class ShopProfile(
    @PrimaryKey val uuid: UUID,
    @Column("last_known_name") val name: String,
    val coins: Long = 0,
    val notiz: String? = null,
    @JsonColumn val purchases: List<String> = emptyList(),
    val settings: ProfileSettings = ProfileSettings(),
)

data class ProfileSettings(
    val language: String = "de",
    val scoreboard: Boolean = true,
)
```

Gebaut wird über den **primären Konstruktor** — deine `val`s dürfen also
unveränderlich bleiben. Alle persistierten Felder müssen im primären Konstruktor
stehen; Properties im Klassenrumpf werden ignoriert.

### Typen

| Kotlin | MariaDB |
|---|---|
| `String` | `VARCHAR(255)` |
| `UUID` | `CHAR(36)` |
| `Int` / `Long` / `Short` | `INT` / `BIGINT` / `SMALLINT` |
| `Boolean` | `BOOLEAN` |
| `Double` / `Float` | `DOUBLE` / `FLOAT` |
| Enum | `VARCHAR(255)` — gespeichert wird der Konstantenname |
| alles andere | `LONGTEXT` als JSON |

Listen, Maps und verschachtelte Datenklassen brauchen **keine** Annotation — sie
werden automatisch als JSON abgelegt. `@JsonColumn` brauchst du nur, wenn ein Typ
sonst direkt in eine Spalte passen würde (etwa ein `String`, den du als JSON-Blob
willst).

Generics bleiben erhalten: `List<String>` kommt als `List<String>` zurück, nicht
als `List<LinkedTreeMap>`.

`String?`, `Long?` und andere nullable Typen werden ohne `NOT NULL` angelegt.
Nicht-nullable Typen bekommen `NOT NULL`. Steht in der Spalte trotzdem `NULL`,
knallt es beim Lesen sofort statt irgendwo später.

### Spaltenlänge

```kotlin
@Column("beschreibung", length = 2000) val beschreibung: String
```

`length` wirkt nur auf `VARCHAR`-Spalten (String und Enum).

---

## Datenbankzugriff

Alle CRUD-Methoden sind `suspend` und laufen intern auf `Dispatchers.IO`. Der
Tick-Thread blockiert nie.

```kotlin
import de.mecrytv.earthcore.database.api.findAll
import de.mecrytv.earthcore.database.api.findById

scope.launch {
    val profile = database.findById<ShopProfile, UUID>(uuid)   // T? oder null
    val alle = database.findAll<ShopProfile>()                 // List<T>

    database.save(profile)     // Upsert: legt an oder überschreibt
    database.update(profile)   // nur wenn der Schlüssel existiert
    database.delete(profile)
}
```

`save` ist ein Upsert (`INSERT … ON DUPLICATE KEY UPDATE`) — der übliche Weg zum
Speichern. `update` ist ein reines `UPDATE … WHERE pk`; existiert der Schlüssel
nicht, passiert nichts.

Die `reified`-Kurzformen (`findById<T, ID>`, `findAll<T>`, `registerModel<T>`)
liegen im Package `de.mecrytv.earthcore.database.api` und müssen einzeln
importiert werden. Ohne sie geht es genauso:

```kotlin
database.findById(ShopProfile::class.java, uuid)
database.findAll(ShopProfile::class.java)
```

### Zurück auf den Main-Thread

Coroutinen laufen **nicht** auf dem Tick-Thread. Bukkit-API — Spieler, Welt,
Inventare — ist nicht thread-sicher. Also zurückspringen:

```kotlin
scope.launch {
    val profile = database.findById<ShopProfile, UUID>(player.uniqueId) ?: return@launch

    server.scheduler.runTask(plugin, Runnable {
        player.sendMessage("Guthaben: ${profile.coins}")
    })
}
```

### Eigener Scope

Jedes Plugin bringt seinen eigenen `CoroutineScope` mit und beendet ihn im
`onDisable`:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

override fun onDisable() = scope.cancel()
```

`SupervisorJob` sorgt dafür, dass ein Fehler in einer Coroutine nicht alle
anderen mitreißt.

---

## Listener

```kotlin
package de.mecrytv.earthshop.listener

import de.mecrytv.earthcore.database.api.findById
import de.mecrytv.earthcore.registry.annotations.AutoListener
import de.mecrytv.earthshop.EarthShop
import de.mecrytv.earthshop.model.ShopProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID

@AutoListener("Join", "Legt beim ersten Join ein Shop-Profil an")
class JoinListener(private val plugin: EarthShop) : Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        scope.launch {
            val profile = plugin.database.findById<ShopProfile, UUID>(player.uniqueId)
                ?: ShopProfile(player.uniqueId, player.name).also { plugin.database.save(it) }

            plugin.server.scheduler.runTask(plugin, Runnable {
                player.sendMessage("Guthaben: ${profile.coins}")
            })
        }
    }
}
```

`name` und `description` sind optional; ohne `name` wird der Klassenname genommen.
Beide tauchen in der `RegistrationSummary` und im Startlog auf.

### Abhängigkeit von anderen Plugins

```kotlin
@AutoListener("Vault", "Wirtschafts-Anbindung", requires = ["Vault"])
class VaultListener : Listener
```

Fehlt eines der genannten Plugins, wird der Listener übersprungen und landet in
`summary.skipped` — statt beim Start mit `NoClassDefFoundError` zu knallen.

---

## Commands

Commands laufen über Papers Brigadier-`BasicCommand` und werden über den
Lifecycle-Registrar angemeldet. Es gehört **kein** `commands:`-Block in deine
`plugin.yml`.

```kotlin
package de.mecrytv.earthshop.command

import de.mecrytv.earthcore.registry.annotations.AutoCommand
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack

@AutoCommand(
    name = "balance",
    description = "Zeigt dein Guthaben",
    aliases = ["bal", "money"],
    permission = "earthshop.balance",
)
object BalanceCommand : BasicCommand {

    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        source.sender.sendMessage("Dein Guthaben wird geladen...")
    }

    override fun suggest(source: CommandSourceStack, args: Array<out String>): Collection<String> =
        listOf("info", "top")
}
```

Ein `object` reicht, wenn der Command keinen Zustand braucht. Sonst eine Klasse
mit Plugin-Konstruktor:

```kotlin
@AutoCommand(name = "shop", permission = "earthshop.use")
class ShopCommand(private val plugin: EarthShop) : BasicCommand { … }
```

`permission` in der Annotation setzt `BasicCommand.permission()`. Überschreibt
deine Klasse `permission()` zusätzlich selbst, müssen **beide** Berechtigungen
erfüllt sein — der strengere Fall gewinnt, damit eine Annotation nie eine im Code
gesetzte Sperre aufweicht. Nimm eins von beidem, nicht beides.

---

## Cooldowns

Cooldowns liegen in EarthCores Datenbank und **überleben einen Serverneustart**.
Gelesen wird trotzdem aus dem Speicher: EarthCore lädt beim Start alle noch
laufenden Cooldowns in einen Cache und schreibt Änderungen im Hintergrund zurück.
Deshalb sind alle Abfragen synchron und blockieren den Tick-Thread nicht.

### Per Annotation am Command

```kotlin
@AutoCommand(name = "kit", description = "Holt dein Kit", permission = "earthshop.kit")
@Cooldown(hours = 24, bypassPermission = "earthshop.kit.bypass")
object KitCommand : BasicCommand {

    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        source.sender.sendMessage("Kit ausgegeben.")
    }
}
```

Läuft noch ein Cooldown, wird `execute` gar nicht erst aufgerufen und der Spieler
bekommt die Meldung aus der `messages.json`. Gestartet wird der Cooldown **nach**
einem erfolgreichen Durchlauf — wirft dein Command, bleibt der Spieler frei.

| Feld | Default | Wirkung |
|---|---|---|
| `seconds` / `minutes` / `hours` | `0` | Werden addiert. Mindestens eines muss gesetzt sein. |
| `key` | Command-Name | Mehrere Commands mit demselben `key` teilen sich einen Cooldown |
| `bypassPermission` | `""` | Wer sie hat, wird nie gebremst |
| `messageKey` | `"cooldown.active"` | Pfad in der `messages.json` |

```kotlin
@AutoCommand(name = "warp")
@Cooldown(minutes = 1, seconds = 30, key = "teleport")
object WarpCommand : BasicCommand { … }
```

Die Konsole hat nie einen Cooldown — nur Spieler werden gebremst.

### Direkt über die API

```kotlin
import de.mecrytv.earthcore.cooldown.api.CooldownRegistry
import java.time.Duration

val cooldowns = server.servicesManager.load(CooldownRegistry::class.java)!!

cooldowns.start(player.uniqueId, "teleport", Duration.ofSeconds(30))
cooldowns.extend(player.uniqueId, "teleport", Duration.ofSeconds(10))

if (cooldowns.isActive(player.uniqueId, "teleport")) {
    val rest = cooldowns.remaining(player.uniqueId, "teleport")
    player.sendMessage("Noch ${rest.seconds}s")
    return
}

cooldowns.clear(player.uniqueId, "teleport")
cooldowns.clearAll(player.uniqueId)
cooldowns.keys(player.uniqueId)
```

`start` überschreibt einen laufenden Cooldown, `extend` rechnet auf die Restzeit
auf. Ist der Cooldown bereits abgelaufen, startet `extend` von jetzt an neu.
`remaining` liefert `Duration.ZERO`, wenn keiner läuft.

### Die Meldung anpassen

Die Texte stehen in `plugins/EarthCore/messages.json`:

```json
{
  "prefix": "<gray>[<gold>%plugin%<gray>]</gray> ",
  "cooldown": {
    "active": "%prefix%<red>Bitte warte noch <yellow>%remaining%</yellow>."
  }
}
```

`%plugin%` ist der Name **deines** Plugins, nicht EarthCore. Ein Cooldown aus
EarthShop meldet sich also als `[EarthShop]` — fuer den Spieler sieht es aus wie
ein einziges Plugin, obwohl EarthCore die Nachricht verschickt.

`%remaining%` wird als `1h 30m 15s` eingesetzt, Nullwerte fallen weg, angebrochene
Sekunden werden aufgerundet. Formatiert wird mit MiniMessage.

Eigene Texte legst du daneben und verweist per `messageKey` darauf:

```json
{
  "kit": { "active": "%prefix%<red>Dein Kit gibt es erst in <yellow>%remaining%</yellow> wieder." }
}
```

```kotlin
@Cooldown(hours = 24, messageKey = "kit.active")
```

Fehlende Schlüssel werden beim Start aus dem Jar ergänzt, deine Änderungen bleiben
stehen.

---

## Konfiguration

EarthCores eigene Konfiguration liegt im `ServicesManager` und ist über
Punkt-Pfade erreichbar:

```kotlin
import de.mecrytv.earthcore.config.ConfigService
import de.mecrytv.earthcore.config.get
import de.mecrytv.earthcore.config.getOrDefault

val config = server.servicesManager.load(ConfigService::class.java)!!

val namespace = config.getString("settings.namespace")
val debug = config.getBoolean("settings.debug")
val settings = config.get<Settings>("settings")
```

Platzhalter werden in einem Durchlauf ersetzt — eingesetzte Werte werden also
nicht erneut als Platzhalter gelesen:

```kotlin
config.getString("messages.welcome", "player" to player.name, "coins" to 42)
```

Eine eigene Konfigurationsdatei für dein Plugin baust du mit derselben Klasse:

```kotlin
import de.mecrytv.earthcore.config.ConfigDefaults
import de.mecrytv.earthcore.config.JsonConfigService
import java.io.File

data class ShopConfig(val startGuthaben: Long = 100, val maxSlots: Int = 54)

val shopConfig = JsonConfigService(
    file = File(dataFolder, "config.json"),
    defaults = ConfigDefaults.model(ShopConfig()),
    logger = logger,
)
```

Statt `ConfigDefaults.model(...)` kannst du eine fertige Datei aus deinem
`resources`-Ordner als Vorlage nehmen:

```kotlin
defaults = ConfigDefaults.resource("config.json")
```

Gesucht wird im Jar **deines** Plugins, nicht in EarthCores. Brauchst du einen
anderen ClassLoader, gibst du ihn als zweites Argument mit.

Fehlende Schlüssel werden beim Start aus den Defaults ergänzt, vorhandene Werte
bleiben unangetastet. Eine kaputte Datei wird weggesichert statt überschrieben.

---

## Versionierung

### Gegen eine Mindestversion pruefen

```kotlin
import de.mecrytv.earthcore.version.api.CoreVersion

override fun onEnable() {
    val core = server.servicesManager.load(CoreVersion::class.java)
        ?: error("EarthCore ist nicht geladen")
    core.requireAtLeast("1.9.0")
    ...
}
```

`requireAtLeast` wirft mit einer lesbaren Meldung, wenn der laufende Core zu alt
ist — deutlich hilfreicher als ein `NoSuchMethodError` mitten im Spielbetrieb.
`isAtLeast(...)` gibt stattdessen ein `Boolean` zurueck, wenn du eine Funktion
optional nutzen willst.

Der Vergleich ist semver-korrekt: `1.10.0` ist neuer als `1.9.0`, und eine
Vorabversion wie `1.9.0-SNAPSHOT` gilt als aelter als `1.9.0`.

### Neue Felder an bestehenden Models

`registerModel` gleicht die Tabelle gegen dein Model ab und ergaenzt fehlende
Spalten selbst:

```
[EarthCore] Spalte `notiz` zu `shop_profiles` ergaenzt.
[EarthCore] Spalte `coins` zu `shop_profiles` ergaenzt. Bestehende Zeilen bekommen 0.
```

Nicht-nullbare Spalten bekommen dabei einen Default, damit bestehende Zeilen
gueltig bleiben: `0` fuer Zahlen und Wahrheitswerte, `''` fuer Texte, die erste
Konstante fuer Enums, `[]` oder `{}` fuer JSON-Spalten. Willst du andere Werte,
setz sie nach dem Start per `UPDATE`.

Umbenannte oder geloeschte Felder und Typaenderungen macht EarthCore **nicht**.
Ueberzaehlige Spalten werden nur gemeldet:

```
[EarthCore] `shop_profiles` hat Spalten ohne Feld im Model: [alteSpalte].
```

### Konfigurationsdateien migrieren

Neue Schluessel brauchen nichts — die werden beim Start aus den Defaults
ergaenzt. Erst wenn du Schluessel **umbenennst oder entfernst**, brauchst du
Versionierung:

```kotlin
import de.mecrytv.earthcore.config.ConfigMigration
import de.mecrytv.earthcore.config.ConfigVersioning

configService = JsonConfigService(
    file = File(dataFolder, "config.json"),
    defaults = ConfigDefaults.model(ShopConfig()),
    versioning = ConfigVersioning(
        current = 2,
        steps = mapOf(
            2 to ConfigMigration { root ->
                val server = root.getAsJsonObject("server")
                server.add("name", server.remove("title"))
            },
        ),
    ),
    logger = logger,
)
```

Der Schluessel `configVersion` landet in der Datei, sobald `current` groesser 1
ist oder Schritte hinterlegt sind — vorher bleibt die Datei unberuehrt. Beim
Start laufen genau die Schritte, die zwischen dem Stand auf der Platte und
`current` liegen, in aufsteigender Reihenfolge. Eine Datei ohne Versionsschluessel
gilt als Version 1.

Scheitert ein Schritt, bricht der Start ab und die Datei auf der Platte bleibt
unveraendert — lieber ein lauter Fehler als eine halb migrierte Konfiguration.

---

## Annotationen

| Annotation | Wirkung |
|---|---|
| `@Table("name")` | Model — legt die Tabelle an. Pflicht. |
| `@PrimaryKey` | Der eindeutige Schlüssel. Genau einer pro Model. |
| `@Column(name = "", length = 255)` | Abweichender Spaltenname / `VARCHAR`-Länge |
| `@JsonColumn(name = "")` | Erzwingt Gson-Serialisierung nach `LONGTEXT` |
| `@AutoListener(name, description, requires)` | Bukkit-`Listener` — `registerEvents` |
| `@AutoCommand(name, description, aliases, permission, requires)` | Paper-`BasicCommand` |
| `@Cooldown(seconds, minutes, hours, key, bypassPermission, messageKey)` | Cooldown auf einem Command |

`@Table`, `@PrimaryKey`, `@Column` und `@JsonColumn` liegen in
`de.mecrytv.earthcore.database.annotations`, `@AutoListener` und `@AutoCommand` in
`de.mecrytv.earthcore.registry.annotations`.

### Wie eine Klasse erzeugt wird

Der Registrar probiert in dieser Reihenfolge:

1. Kotlin `object` → die Instanz selbst
2. Konstruktor mit genau einem Parameter, auf den die Plugin-Instanz passt
   (`JavaPlugin` oder deine konkrete Klasse)
3. parameterloser Konstruktor

Passt nichts davon, wird die Klasse mit einer Warnung übersprungen — der Rest
registriert sich trotzdem.

---

## API-Referenz

### `DatabaseProvider`

```kotlin
fun of(name: String): DatabaseService
fun names(): Set<String>
```

Pro Name genau ein Pool, gecacht. Der Name wird gegen `[A-Za-z0-9_]{1,64}`
geprüft, bevor eine Verbindung aufgebaut wird.

### `AutoRegistrar`

```kotlin
fun register(plugin: JavaPlugin, database: DatabaseService, vararg packages: String): RegistrationSummary
fun register(plugin: JavaPlugin, vararg packages: String): RegistrationSummary
```

### `CooldownRegistry`

```kotlin
fun start(subject: UUID, key: String, duration: Duration)
fun extend(subject: UUID, key: String, by: Duration)
fun clear(subject: UUID, key: String): Boolean
fun clearAll(subject: UUID): Int
fun isActive(subject: UUID, key: String): Boolean
fun remaining(subject: UUID, key: String): Duration
fun keys(subject: UUID): Set<String>
```

Alle Methoden sind synchron. Persistiert wird im Hintergrund in EarthCores
Datenbank, Tabelle `cooldowns`.

### `RegistrationSummary`

```kotlin
val entries: List<RegisteredEntry>
val skipped: List<String>
val models: Int
val listeners: Int
val commands: Int
val total: Int
fun of(kind: RegisteredEntry.Kind): List<RegisteredEntry>
```

`RegisteredEntry` trägt `kind` (`MODEL`/`LISTENER`/`COMMAND`), `name`,
`description` und `type`.

### `DatabaseService`

```kotlin
fun registerModel(modelClass: Class<*>)

suspend fun <T : Any> save(entity: T)
suspend fun <T : Any> update(entity: T)
suspend fun <T : Any> delete(entity: T)
suspend fun <T : Any, ID : Any> findById(modelClass: Class<T>, id: ID): T?
suspend fun <T : Any> findAll(modelClass: Class<T>): List<T>
```

Dazu die `reified`-Kurzformen `registerModel<T>()`, `findById<T, ID>(id)` und
`findAll<T>()`.

`connect()` und `close()` gehören EarthCore — ruf sie nicht selbst auf.

---

## Fallstricke

**`depend: [EarthCore]` vergessen.** Führt zu `NoClassDefFoundError` beim Start,
nicht zu einer verständlichen Meldung.

**`kotlin.stdlib.default.dependency=false` vergessen.** Dein Jar bringt eine
zweite Kotlin-Runtime mit. Läuft meistens, ist aber unnötiger Ballast und kann bei
Versionsunterschieden Probleme machen.

**Bukkit-API aus einer Coroutine.** Sieht aus, als würde es funktionieren, und
zerlegt dir bei Last den Server. Immer mit `scheduler.runTask` zurückspringen.

**Property im Klassenrumpf statt im Konstruktor.** Wird nicht persistiert und
taucht auch nicht in der Tabelle auf — ohne Fehlermeldung.

**Neues Feld an einem bestehenden Model.** `registerModel` macht nur
`CREATE TABLE IF NOT EXISTS`, kein `ALTER`. Die Spalte fehlt, und jede Abfrage auf
die Tabelle scheitert mit *Unknown column*. Bis auf Weiteres manuell:

```sql
ALTER TABLE shop_profiles ADD COLUMN notiz VARCHAR(255);
```

**Cooldown-Dauer von 0.** `@Cooldown()` ohne `seconds`/`minutes`/`hours` und
`start(..., Duration.ZERO)` werfen beide eine `IllegalArgumentException`. Beim
Command fliegt sie schon beim Registrieren, nicht erst beim Ausführen.

**`findAll` auf einer großen Tabelle.** Lädt ohne Limit alles in den Heap. Für
Stamm- und Konfigurationsdaten gedacht, nicht für Log-Tabellen.

**Zwei Plugins, derselbe Datenbankname.** `of("shop")` in zwei Plugins gibt beiden
denselben Pool und dieselben Tabellen. Nimm einen Namen, der zu deinem Plugin
passt.
