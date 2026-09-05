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
wird mit MiniMessage formatiert.

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

> Daneben liegt `EarthCore-1.7.1.jar` (~78 KB) aus dem Standard-`jar`-Task. Das
> ist das Jar **ohne** Abhängigkeiten und gehört nicht auf den Server.

---

## Auf dem Server einrichten

**1.** `build/libs/EarthCore.jar` nach `<server>/plugins/` kopieren.

**2.** Server einmal starten. EarthCore legt `plugins/EarthCore/config.json` an
und fährt sich sofort wieder herunter, weil noch keine Datenbank erreichbar ist.

**3.** Zugangsdaten eintragen:

```json
{
  "settings": { "namespace": "earthcraft", "debug": false },
  "database": {
    "host": "127.0.0.1",
    "port": 3306,
    "database": "earthcore",
    "user": "earthcore",
    "password": "geheim",
    "poolSize": 10,
    "connectionTimeoutMs": 5000
  }
}
```

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

Legt `de.mecrytv:earthcore:1.7.1` in `~/.m2/repository` ab. Veröffentlicht wird
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
└── cooldown/
    ├── api/                  CooldownRegistry
    └── internal/             Speicher-Cache mit Datenbank dahinter, Zeitformatierung
```

Interfaces liegen in `api/`, Implementierungen in `internal/`. Nur `api/` und
`annotations/` sind für andere Plugins gedacht.

---

## Grenzen

**Keine Migrationen.** `registerModel` macht `CREATE TABLE IF NOT EXISTS`, kein
`ALTER`. Ein neues Feld an einem Model, dessen Tabelle schon existiert, braucht
ein manuelles `ALTER TABLE`.

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
Minor-, Fehlerbehebungen die Patch-Version.
