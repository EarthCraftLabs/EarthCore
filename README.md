# EarthCore

Core-System des EarthCraft-Netzwerks für **Paper 1.21.11**. Ein Plugin, das die
Dinge bereitstellt, die sonst jedes Plugin einzeln nachbauen müsste: Konfiguration,
Datenbankanbindung und automatische Registrierung von Models, Listenern und
Commands.

**Kotlin und Java werden gleichwertig unterstützt.**

| | |
|---|---|
| 📘 **Kotlin-Plugin schreiben** | → [docs/kotlin.md](docs/kotlin.md) |
| ☕ **Java-Plugin schreiben** | → [docs/java.md](docs/java.md) |

---

## Was EarthCore bereitstellt

| Service | Was es tut |
|---|---|
| `ConfigService` | JSON-Konfiguration über Punkt-Pfade. Fehlende Schlüssel werden beim Start aus den Defaults ergänzt, kaputte Dateien weggesichert statt überschrieben. |
| `DatabaseProvider` | Gibt jedem Plugin **seine eigene** MariaDB-Datenbank und legt sie bei Bedarf an. |
| `DatabaseService` | CRUD auf annotierten Models — HikariCP als Pool, Gson für komplexe Felder. Nie auf dem Tick-Thread. |
| `AutoRegistrar` | Durchsucht die Packages deines Plugins und registriert alles mit passender Annotation. |
| `CooldownRegistry` | Cooldowns pro Spieler, in der Datenbank abgelegt und damit neustartfest. Abfragen bleiben synchron. |
| `CoreVersion` | Sagt, welche EarthCore-Version laeuft, und prueft semver-korrekt gegen eine Mindestanforderung. |
| `LogbookProvider` | Zentrales Logbuch: Konsole, Datenbank und Discord-Webhooks in einem Aufruf. |
| `ItemBuilder` | Unveraenderlicher Builder fuer ItemStacks, MiniMessage inklusive. |
| `GuiProvider` | Deklaratives Menue-System mit Masken, Seiten, Verlauf und Nachladen. |

Alle liegen in Bukkits `ServicesManager`. Dein Plugin braucht die
`EarthCore`-Klasse nie zu kennen — ein `load(DatabaseProvider.class)` reicht.

### Eine Datenbank pro Plugin

```
MariaDB
├── earthcore      EarthCore selbst
├── earthshop      EarthShop
└── earthquests    EarthQuests
```

Jedes Plugin sagt in seiner Hauptklasse, wie seine Datenbank heißt, und bekommt
einen eigenen Verbindungspool darauf. Zugangsdaten kommen zentral aus EarthCores
`config.json` — pro Plugin unterscheidet sich nur der Name.

### Models statt SQL

```kotlin
@Table("shop_profiles")
data class ShopProfile(
    @PrimaryKey val uuid: UUID,
    @Column("last_known_name") val name: String,
    val coins: Long = 0,
    @JsonColumn val purchases: List<String> = emptyList(),
)
```

Die Tabelle wird beim Start angelegt, Typen werden abgeleitet, und alles ohne
eigenen SQL-Typ — Listen, Maps, verschachtelte Klassen — wandert automatisch als
JSON in eine `LONGTEXT`-Spalte.

### Auto-Register

Ein Aufruf in `onEnable`, und Models, Listener und Commands melden sich selbst an:

```kotlin
registrar.register(this, database, "de.mecrytv.earthshop")
```

| Annotation | Wirkung |
|---|---|
| `@Table("name")` | Model — legt die Tabelle an |
| `@PrimaryKey` | Der eindeutige Schlüssel |
| `@Column(name, length)` | Abweichender Spaltenname / `VARCHAR`-Länge |
| `@JsonColumn` | Erzwingt Gson-Serialisierung |
| `@AutoListener(name, description, requires)` | Bukkit-`Listener` |
| `@AutoCommand(name, description, aliases, permission, requires)` | Paper-`BasicCommand` |
| `@Cooldown(seconds, minutes, hours, key, bypassPermission, messageKey)` | Cooldown auf einem Command |

Commands laufen über Papers Brigadier-Lifecycle — kein `commands:`-Block in der
`plugin.yml` nötig.

### Cooldowns

```kotlin
@AutoCommand(name = "kit")
@Cooldown(hours = 24, bypassPermission = "earthshop.kit.bypass")
object KitCommand : BasicCommand { … }
```

Cooldowns liegen in der Datenbank und überleben einen Neustart. Gelesen wird aus
einem Cache, den EarthCore beim Start füllt — Abfragen sind daher synchron und
bremsen den Tick-Thread nicht. Ohne Annotation geht es genauso über die API:
`cooldowns.start(uuid, "teleport", Duration.ofSeconds(30))`.

Die Meldung bei aktivem Cooldown steht in `plugins/EarthCore/messages.json` und
wird mit MiniMessage formatiert. Der Praefix traegt den Namen des **aufrufenden**
Plugins (`%plugin%`) — ein Cooldown aus EarthShop meldet sich als `[EarthShop]`,
nicht als `[EarthCore]`. Fuer den Spieler wirkt es wie ein einziges Plugin.

### Logging mit Discord

```kotlin
val logbook = server.servicesManager.load(LogbookProvider::class.java)!!.of(this)

logbook.error("technik", "Backup fehlgeschlagen", exception)
logbook.record("moderation", team.uniqueId, "Bann", mapOf("ziel" to name, "grund" to "Griefing"))
```

Ein Aufruf, drei Ziele: Konsole, Tabelle `log_entries` und Discord. Welche
Eintraege nach Discord gehen, entscheiden pro Webhook ein Mindest-Level und eine
Kategorienliste - Fehler in den Technik-Kanal, Team-Aktionen in den Team-Kanal.

Der Versand laeuft gebuendelt und asynchron mit Ratenbegrenzung; ein Fehlersturm
bringt weder den Tick-Thread noch Discord ins Straucheln.

### Items bauen

```kotlin
ItemBuilder.of(Material.DIAMOND_SWORD)
    .name("<gold>Scharfes Schwert")
    .lore("<gray>Geschmiedet in EarthCraft")
    .enchant(Enchantment.SHARPNESS, 5)
    .glint(true)
    .tag(NamespacedKey(plugin, "artikel"), "schwert-01")
    .build()
```

Unveraenderlich - jeder Aufruf liefert einen neuen Builder, Vorlagen lassen sich
also gefahrlos mehrfach ableiten. Name und Lore als MiniMessage, kursiv wird
automatisch abgeschaltet. Koepfe, Ruestungsfarben, Traenke, Buecher, Feuerwerk und
Banner sind abgedeckt, eigene Daten liegen im PersistentDataContainer.

Intern ein Mix: Data Components dort, wo sie mehr koennen (Glitzern ohne
Verzauberung, Stapelgroesse, Kopf-Texturen), sonst das stabile `ItemMeta`.

### Menues

```kotlin
class ShopGui(private val artikel: List<Artikel>) : Gui(GuiType.chest(4), titel) {

    override fun render(view: GuiView) {
        view.mask("#########", "#.......#", "P###F###N")
        view.bind('#', Buttons.filler())
        view.paginate('.', artikel) { GuiItem(it.item()) { klick -> kaufen(klick) } }
        view.item(view.slots('N').first(), Buttons.nextPage(view))
    }
}
```

Deklarativ: du beschreibst, wie das Menue bei einem Zustand aussieht, EarthCore
zeichnet nach `refresh()` nur die geaenderten Slots neu. Masken statt Slot-Nummern,
Seiten mit fertigen Blaetter-Buttons, Verlauf mit Zurueck-Knopf, geteilte Menues
fuer mehrere Betrachter, Hopper und Ofen als Typen und der Amboss als Texteingabe.

Inhalte aus der Datenbank holt `view.load(...)` abseits vom Tick-Thread und zeigt
solange einen Platzhalter.

### Threading

Datenbankzugriffe laufen **nie** auf dem Tick-Thread. Kotlin bekommt
`suspend`-Funktionen auf `Dispatchers.IO`, Java dieselben Operationen als
`CompletableFuture` auf demselben Dispatcher. Nur `connect()`, `close()` und
`registerModel()` blockieren bewusst — sie gehören nach `onEnable`/`onDisable`,
wo der Server ohnehin noch keine Spieler annimmt.

---

## Voraussetzungen

- Paper **1.21.11** (`api-version: '1.21'`)
- Java **21**
- MariaDB **10.3+**

---

## Bauen

```bash
./gradlew build
```

Ergebnis: **`build/libs/EarthCore.jar`** (~8 MB) — das Shadow-Jar mit Kotlin,
Coroutines, HikariCP und dem MariaDB-Treiber.

> Daneben liegt `EarthCore-1.14.0.jar` (~78 KB) aus dem Standard-`jar`-Task. Das
> ist das Jar **ohne** Abhängigkeiten und gehört nicht auf den Server.

---

## Auf dem Server einrichten

**1.** `build/libs/EarthCore.jar` nach `<server>/plugins/` kopieren.

**2.** Server einmal starten. EarthCore kopiert seine mitgelieferte Vorlage nach
`plugins/EarthCore/config.json` und `plugins/EarthCore/messages.json` und fährt
sich sofort wieder herunter, weil noch keine Datenbank erreichbar ist.

**3.** Zugangsdaten eintragen:

```json
{
  "settings": { "namespace": "earthcraft", "debug": false },
  "database": {
    "host": "127.0.0.1",
    "port": 3306,
    "database": "earthcore",
    "user": "earthcore",
    "password": "hier-eintragen",
    "poolSize": 10,
    "connectionTimeoutMs": 5000
  },
  "logging": {
    "debug": false,
    "retentionDays": 30,
    "discord": [
      { "url": "https://discord.com/api/webhooks/...", "minLevel": "ERROR", "categories": [], "username": "EarthCraft Fehler" },
      { "url": "https://discord.com/api/webhooks/...", "minLevel": "WARN", "categories": [], "username": "EarthCraft" },
      { "url": "https://discord.com/api/webhooks/...", "minLevel": "INFO", "categories": ["moderation"], "username": "EarthCraft Team" }
    ]
  }
}
```

> Die Vorlage im Jar (`src/main/resources/config.json`) enthält bewusst ein leeres
> Passwort und leere Webhook-URLs — ein Webhook ohne URL ist wirkungslos. Ein Test
> im Build bricht ab, sobald dort echte Zugangsdaten stehen, damit nichts
> versehentlich im Repository landet.
>
> Die Datei in `plugins/EarthCore/` enthält nach dem Eintragen deine echten
> Zugangsdaten und gehört **nicht** in ein Repository.

**4.** Datenbankbenutzer anlegen:

```sql
CREATE USER 'earthcore'@'localhost' IDENTIFIED BY 'geheim';
GRANT ALL PRIVILEGES ON `earth%`.* TO 'earthcore'@'localhost';
FLUSH PRIVILEGES;
```

Das Wildcard-Grant reicht, damit EarthCore die Datenbanken selbst anlegen kann —
seine eigene `earthcore` und je eine pro Plugin. Wer keine `CREATE`-Rechte
vergeben will, legt die Datenbanken von Hand an: EarthCore prüft vorher über
`information_schema` und greift dann nicht zum `CREATE`.

**5.** Server neu starten. Im Log steht dann:

```
EarthCore aktiv - verbunden mit jdbc:mariadb://127.0.0.1:3306/earthcore
```

Ist die Datenbank nicht erreichbar, deaktiviert EarthCore sich selbst. Abhängige
Plugins laufen dann gar nicht erst an, statt reihenweise Folgefehler zu werfen.

---

## Für andere Projekte veröffentlichen

```bash
./gradlew publishToMavenLocal
```

Legt `de.mecrytv:earthcore:1.14.0` in `~/.m2/repository` ab. Veröffentlicht wird
das **Shadow-Jar** — das andere Projekt bekommt mit einer einzigen Abhängigkeit
auch Kotlin, Coroutines und HikariCP auf den Compile-Classpath.

> Für mehrere Rechner statt mavenLocal ein `maven { url = uri("...") }`-Repository
> in den `publishing`-Block eintragen.

Wie du die Abhängigkeit dann einbindest, steht in den Sprach-Docs:
[Kotlin](docs/kotlin.md#projekt-aufsetzen) · [Java](docs/java.md#projekt-aufsetzen)

---

## Aufbau

```
de.mecrytv.earthcore
├── EarthCore.kt              Plugin-Lifecycle, Service-Registrierung
├── config/                   ConfigService + Gson-Implementierung
├── database/
│   ├── annotations/          @Table @PrimaryKey @Column @JsonColumn
│   ├── api/                  DatabaseService, DatabaseProvider, DatabaseCredentials
│   └── internal/             HikariCP-Pools, Reflection auf Models, SQL-Erzeugung
├── registry/
│   ├── annotations/          @AutoListener @AutoCommand @Cooldown
│   ├── api/                  AutoRegistrar, RegistrationSummary, RegisteredEntry
│   └── internal/             Classpath-Scan, Instanziierung, Permission- und Cooldown-Wrapper
├── cooldown/
│   ├── api/                  CooldownRegistry
│   └── internal/             Speicher-Cache mit Datenbank dahinter, Zeitformatierung
├── version/
│   ├── api/                  CoreVersion
│   └── internal/             Semver-Vergleich
├── logging/
│   ├── api/                  Logbook, LogbookProvider, LogEntry, LogLevel, LogSink
│   └── internal/             Konsolen-, Datenbank- und Discord-Senke, Webhook-Versand
├── item/
│   ├── api/                  ItemBuilder
│   └── internal/             MiniMessage-Aufbereitung, Texturpruefung
└── gui/
    ├── api/                  Gui, GuiView, GuiType, GuiMask, Page, Buttons, AnvilPrompt
    └── internal/             Sitzungen, Zeichenpuffer mit Diffing, Nachlade-Cache, Bukkit-Listener
```

Interfaces liegen in `api/`, Implementierungen in `internal/`. Nur `api/` und
`annotations/` sind für andere Plugins gedacht.

---

## Grenzen

**Migrationen nur additiv.** Fehlende Spalten werden beim `registerModel` per
`ALTER TABLE` ergaenzt. Umbenannte oder geloeschte Felder und Typaenderungen
bleiben deine Aufgabe — EarthCore meldet ueberzaehlige Spalten nur im Log und
loescht nie.

**`findAll` lädt ohne Limit.** Für Stamm- und Konfigurationsdaten gedacht, nicht
für Log-Tabellen.

**Keine Transaktionen.** Jede Operation läuft für sich.

**Plugin-Reloads.** Zur Laufzeit nachgeladene Plugins bekommen einen neuen
ClassLoader; die gecachten Schemas des alten bleiben liegen. Bei Bedarf den Server
neu starten statt PlugMan zu benutzen.

---

## Versionierung

`build.gradle.kts` ist die einzige Stelle, an der die Version steht — die
`plugin.yml` zieht sie sich beim Bauen von dort. Neue Funktionen erhöhen die
Minor-, Fehlerbehebungen die Patch-Version. Die Historie steht in
[CHANGELOG.md](CHANGELOG.md), jede Version hat einen Git-Tag `vX.Y.Z`.

An drei weiteren Stellen wird versioniert:

**Datenbank.** `registerModel` gleicht die Tabelle gegen das Model ab und
ergänzt fehlende Spalten per `ALTER TABLE` — additiv, nie löschend.

**Konfiguration.** `ConfigVersioning` mit `ConfigMigration`-Schritten, sobald du
Schlüssel umbenennst oder entfernst. Ohne Angabe passiert nichts und es landet
auch kein Versionsschlüssel in der Datei.

**API.** Abhängige Plugins prüfen mit
`load(CoreVersion::class.java).requireAtLeast("1.9.0")`, ob der laufende Core neu
genug ist — statt später an einem `NoSuchMethodError` zu scheitern.
