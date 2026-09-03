# EarthCore für Java-Plugins

Alles, was du brauchst, um ein Java-Plugin auf EarthCore aufzusetzen. Du brauchst
weder Kotlin-Kenntnisse noch das Kotlin-Gradle-Plugin. Serverseitige Einrichtung
steht im [Haupt-README](../README.md).

> Kotlin-Plugin? → [kotlin.md](kotlin.md)

---

## Inhalt

- [Projekt aufsetzen](#projekt-aufsetzen)
- [Hauptklasse](#hauptklasse)
- [Models](#models)
- [Datenbankzugriff](#datenbankzugriff)
- [Listener](#listener)
- [Commands](#commands)
- [Konfiguration](#konfiguration)
- [Annotationen](#annotationen)
- [API-Referenz](#api-referenz)
- [Fallstricke](#fallstricke)

---

## Projekt aufsetzen

### Gradle

```kotlin
plugins {
    java
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
    compileOnly("de.mecrytv:earthcore:1.6.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
```

### Maven

```xml
<repositories>
  <repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.11-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
  </dependency>
  <dependency>
    <groupId>de.mecrytv</groupId>
    <artifactId>earthcore</artifactId>
    <version>1.6.0</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

`compileOnly` bzw. `provided`: die Klassen liegen zur Laufzeit bereits im
EarthCore-Jar auf dem Server. Du brauchst **kein** Shade-Plugin — dein fertiges
Jar bleibt bei wenigen Kilobyte, weil Kotlin-Runtime, HikariCP und der
MariaDB-Treiber alle aus EarthCore kommen.

### `src/main/resources/plugin.yml`

```yml
name: EarthJavaShop
version: '1.0.0'
main: de.mecrytv.earthjavashop.EarthJavaShop
api-version: '1.21'
depend:
  - EarthCore
```

`depend` ist **Pflicht**. Es regelt die Ladereihenfolge und sorgt dafür, dass dein
ClassLoader die Klassen aus EarthCore sieht. Ohne den Eintrag bekommst du beim
Start `NoClassDefFoundError`.

### Empfohlene Paketstruktur

```
de.mecrytv.earthjavashop
├── EarthJavaShop.java    Hauptklasse
├── model/                @Table-POJOs
├── listener/             @AutoListener-Klassen
└── command/              @AutoCommand-Klassen
```

Die Ordnernamen sind frei wählbar — du gibst sie beim Registrieren selbst an.

---

## Hauptklasse

Zuerst eine kleine Klasse, deren Feldwerte Schema **und** Standardwerte deiner
`config.json` sind:

```java
package de.mecrytv.earthjavashop;

public class ShopConfig {

    public String database = "earthjavashop";
    public long startGuthaben = 100;
    public boolean debug = false;
}
```

Dann die Hauptklasse:

```java
package de.mecrytv.earthjavashop;

import de.mecrytv.earthcore.config.ConfigDefaults;
import de.mecrytv.earthcore.config.ConfigService;
import de.mecrytv.earthcore.config.JsonConfigService;
import de.mecrytv.earthcore.config.PatternPlaceholderResolver;
import de.mecrytv.earthcore.database.api.DatabaseProvider;
import de.mecrytv.earthcore.database.api.DatabaseService;
import de.mecrytv.earthcore.registry.api.AutoRegistrar;
import de.mecrytv.earthcore.registry.api.RegistrationSummary;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class EarthJavaShop extends JavaPlugin {

    private ConfigService configService;
    private ConfigService coreConfig;
    private DatabaseService database;

    @Override
    public void onEnable() {
        configService = new JsonConfigService(
                new File(getDataFolder(), "config.json"),
                ConfigDefaults.Companion.model(new ShopConfig()),
                JsonConfigService.Companion.defaultGson(true),
                new PatternPlaceholderResolver("%", "%"),
                getLogger());

        coreConfig = getServer().getServicesManager().load(ConfigService.class);
        if (coreConfig == null) {
            throw new IllegalStateException("EarthCore ist nicht geladen");
        }

        DatabaseProvider databases = getServer().getServicesManager().load(DatabaseProvider.class);
        database = databases.of(configService.getOrDefault("database", String.class, "earthjavashop"));

        AutoRegistrar registrar = getServer().getServicesManager().load(AutoRegistrar.class);
        RegistrationSummary summary = registrar.register(
                this,
                database,
                "de.mecrytv.earthjavashop.model",
                "de.mecrytv.earthjavashop.listener",
                "de.mecrytv.earthjavashop.command");

        getLogger().info("Gestartet: " + summary);
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public DatabaseService getDatabase() {
        return database;
    }
}
```

Vier Schritte:

1. **`new JsonConfigService(...)`** legt deine eigene
   `plugins/EarthJavaShop/config.json` an. Fehlende Schlüssel werden beim Start
   aus `ShopConfig` ergänzt, vom Nutzer gesetzte Werte bleiben unangetastet. Neue
   Einstellungen fügst du nur in der Klasse hinzu.

   > Die fünf Argumente sind Pflicht: die Kotlin-Seite hat für `gson`,
   > `placeholderResolver` und `logger` Standardwerte, und die gibt es aus Java
   > nicht. `defaultGson(true)` heißt eingerückte, gut lesbare JSON-Datei.
   >
   > Nenn den Getter `getConfigService()`, **nicht** `getConfig()` — den hat
   > `JavaPlugin` schon für die YAML-Konfiguration.

2. **`load(ConfigService.class)`** gibt dir EarthCores eigene Konfiguration, falls
   du an gemeinsame Werte wie `settings.namespace` musst. Rein optional — für
   plugin-eigene Einstellungen nimmst du Schritt 1.

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

```java
long startGuthaben = configService.getLong("startGuthaben", 100);
boolean debug = configService.getBoolean("debug", false);
ShopConfig alles = configService.asModel(ShopConfig.class);
```

Mehr dazu unter [Konfiguration](#konfiguration).

Ohne Package-Angabe wird das Package deiner Hauptklasse samt Unterpaketen
durchsucht:

```java
registrar.register(this, database);
```

Hat dein Plugin gar keine Models, lässt du den `database`-Parameter weg:

```java
registrar.register(this, "de.mecrytv.earthjavashop.listener");
```

Aufräumen musst du nichts — EarthCore schließt beim Serverstopp alle Pools.

---

## Models

Ein Model ist ein **POJO mit parameterlosem Konstruktor und nicht-finalen
Feldern**. EarthCore erzeugt die Instanz über den No-Arg-Konstruktor und befüllt
die Felder per Reflection — Getter und Setter sind dafür nicht nötig, du kannst
sie aber natürlich für deinen eigenen Code haben.

```java
package de.mecrytv.earthjavashop.model;

import de.mecrytv.earthcore.database.annotations.Column;
import de.mecrytv.earthcore.database.annotations.JsonColumn;
import de.mecrytv.earthcore.database.annotations.PrimaryKey;
import de.mecrytv.earthcore.database.annotations.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Table("shop_profiles")
public class ShopProfile {

    @PrimaryKey
    private UUID uuid;

    @Column(name = "last_known_name")
    private String name;

    private long coins;

    private boolean banned;

    @JsonColumn
    private List<String> purchases = new ArrayList<>();

    private transient String nichtGespeichert;

    public ShopProfile() {
    }

    public ShopProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public List<String> getPurchases() {
        return purchases;
    }
}
```

Regeln:

- **Parameterloser Konstruktor ist Pflicht.** Er darf `private` sein.
- **Felder dürfen nicht `final` sein** — sie werden per Reflection gesetzt.
- **`transient` schließt ein Feld aus.** Es bekommt keine Spalte und wird beim
  Laden nicht angefasst.
- Felder aus Oberklassen werden mitgenommen.
- `static` und vom Compiler erzeugte Felder werden ignoriert.

### Typen

| Java | MariaDB |
|---|---|
| `String` | `VARCHAR(255)` |
| `UUID` | `CHAR(36)` |
| `int` / `long` / `short` und die Wrapper | `INT` / `BIGINT` / `SMALLINT` |
| `boolean` / `Boolean` | `BOOLEAN` |
| `double` / `float` und die Wrapper | `DOUBLE` / `FLOAT` |
| Enum | `VARCHAR(255)` — gespeichert wird der Konstantenname |
| alles andere | `LONGTEXT` als JSON |

Listen, Maps und verschachtelte Klassen brauchen **keine** Annotation — sie werden
automatisch als JSON abgelegt. `@JsonColumn` brauchst du nur, wenn ein Typ sonst
direkt in eine Spalte passen würde (etwa ein `String`, den du als JSON-Blob
willst).

Generics bleiben erhalten: `List<String>` kommt als `List<String>` zurück, nicht
als `List<LinkedTreeMap>`.

### Nullability

Java kennt keine Nullability im Typ. EarthCore geht deshalb nach dem Primitiv-Typ:

- **primitiv** (`long`, `boolean`, `int`, `double`, …) → `NOT NULL`
- **Objekt** (`String`, `UUID`, `List`, `Long`, …) → darf `NULL` sein

Willst du ein Objektfeld als `NOT NULL`, nimm den primitiven Typ — oder setze die
Spalte per `ALTER TABLE` nach.

### Spaltenlänge

```java
@Column(name = "beschreibung", length = 2000)
private String beschreibung;
```

`length` wirkt nur auf `VARCHAR`-Spalten (String und Enum).

---

## Datenbankzugriff

Die Kotlin-Seite nutzt `suspend`-Funktionen, die aus Java nicht aufrufbar sind.
Für Java gibt es zu jeder eine `…Async`-Variante mit `CompletableFuture`. Sie
laufen auf demselben IO-Dispatcher — der Tick-Thread blockiert also genauso wenig.

| Methode | Rückgabe |
|---|---|
| `saveAsync(entity)` | `CompletableFuture<Void>` |
| `updateAsync(entity)` | `CompletableFuture<Void>` |
| `deleteAsync(entity)` | `CompletableFuture<Void>` |
| `findByIdAsync(Model.class, id)` | `CompletableFuture<Model>` — `null`, wenn nicht gefunden |
| `findAllAsync(Model.class)` | `CompletableFuture<List<Model>>` |

```java
database.findByIdAsync(ShopProfile.class, uuid)
        .thenAccept(profile -> {
            if (profile == null) {
                getLogger().info("Kein Profil vorhanden");
                return;
            }
            getLogger().info("Guthaben: " + profile.getCoins());
        })
        .exceptionally(error -> {
            getLogger().warning("Fehlgeschlagen: " + error.getMessage());
            return null;
        });
```

`saveAsync` ist ein Upsert (`INSERT … ON DUPLICATE KEY UPDATE`) — der übliche Weg
zum Speichern. `updateAsync` ist ein reines `UPDATE … WHERE pk`; existiert der
Schlüssel nicht, passiert nichts.

### Zurück auf den Main-Thread

Der Callback von `thenAccept` läuft **nicht** auf dem Tick-Thread. Bukkit-API —
Spieler, Welt, Inventare — ist nicht thread-sicher. Also zurückspringen:

```java
database.findByIdAsync(ShopProfile.class, player.getUniqueId())
        .thenAccept(profile -> getServer().getScheduler().runTask(this,
                () -> player.sendMessage("Guthaben: " + profile.getCoins())));
```

### Fehler nicht verschlucken

Ein `CompletableFuture` ohne `exceptionally` schluckt Ausnahmen stillschweigend.
Hänge es an jede Kette, sonst suchst du später nach Fehlern, die nie im Log
standen.

### Mehrere Schritte verketten

```java
private CompletableFuture<ShopProfile> ladenOderAnlegen(Player player) {
    return database.findByIdAsync(ShopProfile.class, player.getUniqueId())
            .thenCompose(profile -> {
                if (profile != null) {
                    return CompletableFuture.completedFuture(profile);
                }
                ShopProfile neu = new ShopProfile(player.getUniqueId(), player.getName());
                return database.saveAsync(neu).thenApply(ignored -> neu);
            });
}
```

`thenCompose` statt `thenApply`, wenn der nächste Schritt selbst ein Future
liefert — sonst bekommst du ein `CompletableFuture<CompletableFuture<…>>`.

---

## Listener

```java
package de.mecrytv.earthjavashop.listener;

import de.mecrytv.earthcore.registry.annotations.AutoListener;
import de.mecrytv.earthjavashop.EarthJavaShop;
import de.mecrytv.earthjavashop.model.ShopProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.CompletableFuture;

@AutoListener(name = "Join", description = "Legt beim ersten Join ein Shop-Profil an")
public class JoinListener implements Listener {

    private final EarthJavaShop plugin;

    public JoinListener(EarthJavaShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDatabase()
                .findByIdAsync(ShopProfile.class, player.getUniqueId())
                .thenCompose(profile -> profile != null
                        ? CompletableFuture.completedFuture(profile)
                        : neuesProfil(player))
                .thenAccept(profile -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.sendMessage("Guthaben: " + profile.getCoins())))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Profil nicht ladbar: " + error.getMessage());
                    return null;
                });
    }

    private CompletableFuture<ShopProfile> neuesProfil(Player player) {
        ShopProfile profile = new ShopProfile(player.getUniqueId(), player.getName());
        return plugin.getDatabase().saveAsync(profile).thenApply(ignored -> profile);
    }
}
```

`name` und `description` sind optional; ohne `name` wird der Klassenname genommen.
Beide tauchen in der `RegistrationSummary` und im Startlog auf.

### Abhängigkeit von anderen Plugins

```java
@AutoListener(name = "Vault", description = "Wirtschafts-Anbindung", requires = {"Vault"})
public class VaultListener implements Listener { }
```

Fehlt eines der genannten Plugins, wird der Listener übersprungen und landet in
`summary.getSkipped()` — statt beim Start mit `NoClassDefFoundError` zu knallen.

---

## Commands

Commands laufen über Papers Brigadier-`BasicCommand` und werden über den
Lifecycle-Registrar angemeldet. Es gehört **kein** `commands:`-Block in deine
`plugin.yml`.

```java
package de.mecrytv.earthjavashop.command;

import de.mecrytv.earthcore.registry.annotations.AutoCommand;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@AutoCommand(
        name = "balance",
        description = "Zeigt dein Guthaben",
        aliases = {"bal", "money"},
        permission = "earthjavashop.balance")
public class BalanceCommand implements BasicCommand {

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        source.getSender().sendMessage("Dein Guthaben wird geladen...");
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        return List.of("info", "top");
    }
}
```

Braucht der Command Zugriff aufs Plugin, nimm einen Konstruktor mit der
Plugin-Instanz:

```java
public class ShopCommand implements BasicCommand {

    private final EarthJavaShop plugin;

    public ShopCommand(EarthJavaShop plugin) {
        this.plugin = plugin;
    }
    …
}
```

`permission` in der Annotation setzt `BasicCommand.permission()`. Überschreibst du
`permission()` zusätzlich selbst, müssen **beide** Berechtigungen erfüllt sein —
der strengere Fall gewinnt, damit eine Annotation nie eine im Code gesetzte Sperre
aufweicht. Nimm eins von beidem, nicht beides.

---

## Konfiguration

EarthCores Konfiguration liegt im `ServicesManager` und ist über Punkt-Pfade
erreichbar:

```java
import de.mecrytv.earthcore.config.ConfigService;

ConfigService config = getServer().getServicesManager().load(ConfigService.class);

String namespace = config.getString("settings.namespace");
boolean debug = config.getBoolean("settings.debug", false);
Settings settings = config.get("settings", Settings.class);
```

Zwei Dinge sind aus Java anders als aus Kotlin:

**Default-Argumente gibt es nicht.** `getBoolean("pfad")` geht in Kotlin, aus Java
musst du den Fallback immer mitgeben: `getBoolean("pfad", false)`. Dasselbe gilt
für `getInt`, `getLong`, `getDouble` und `keys("")`.

**Platzhalter erwarten `kotlin.Pair`.** Etwas sperrig, funktioniert aber:

```java
import kotlin.Pair;

String text = config.getString("messages.welcome",
        new Pair<>("player", player.getName()),
        new Pair<>("coins", 42));
```

Eine eigene Konfigurationsdatei für dein Plugin baust du mit derselben Klasse:

```java
import de.mecrytv.earthcore.config.ConfigDefaults;
import de.mecrytv.earthcore.config.JsonConfigService;
import java.io.File;

public class ShopConfig {
    public long startGuthaben = 100;
    public int maxSlots = 54;
}

JsonConfigService shopConfig = new JsonConfigService(
        new File(getDataFolder(), "config.json"),
        ConfigDefaults.Companion.model(new ShopConfig()),
        JsonConfigService.Companion.defaultGson(true),
        new PatternPlaceholderResolver("%", "%"),
        getLogger());
```

Fehlende Schlüssel werden beim Start aus den Defaults ergänzt, vorhandene Werte
bleiben unangetastet. Eine kaputte Datei wird weggesichert statt überschrieben.

---

## Annotationen

| Annotation | Java-Schreibweise |
|---|---|
| `@Table` | `@Table("shop_profiles")` |
| `@PrimaryKey` | `@PrimaryKey` |
| `@Column` | `@Column(name = "last_known_name", length = 255)` |
| `@JsonColumn` | `@JsonColumn` |
| `@AutoListener` | `@AutoListener(name = "Join", description = "…", requires = {"Vault"})` |
| `@AutoCommand` | `@AutoCommand(name = "balance", description = "…", aliases = {"bal"}, permission = "…")` |

`@Table`, `@PrimaryKey`, `@Column` und `@JsonColumn` liegen in
`de.mecrytv.earthcore.database.annotations`, `@AutoListener` und `@AutoCommand` in
`de.mecrytv.earthcore.registry.annotations`.

**Zur Schreibweise:** Javas Kurzform ohne Elementnamen gilt nur bei genau einem
Element namens `value`. Deshalb geht `@Table("x")` positionell, alle anderen
brauchen `name = …`. Arrays werden mit geschweiften Klammern geschrieben:
`aliases = {"bal", "money"}`.

### Wie eine Klasse erzeugt wird

Der Registrar probiert in dieser Reihenfolge:

1. statisches `INSTANCE`-Feld (Kotlin-`object`, für Java irrelevant)
2. Konstruktor mit genau einem Parameter, auf den die Plugin-Instanz passt
   (`JavaPlugin` oder deine konkrete Klasse)
3. parameterloser Konstruktor

Passt nichts davon, wird die Klasse mit einer Warnung übersprungen — der Rest
registriert sich trotzdem.

---

## API-Referenz

### `DatabaseProvider`

```java
DatabaseService of(String name);
Set<String> names();
```

Pro Name genau ein Pool, gecacht. Der Name wird gegen `[A-Za-z0-9_]{1,64}`
geprüft, bevor eine Verbindung aufgebaut wird.

### `AutoRegistrar`

```java
RegistrationSummary register(JavaPlugin plugin, DatabaseService database, String... packages);
RegistrationSummary register(JavaPlugin plugin, String... packages);
```

### `RegistrationSummary`

```java
List<RegisteredEntry> getEntries();
List<String> getSkipped();
int getModels();
int getListeners();
int getCommands();
int getTotal();
List<RegisteredEntry> of(RegisteredEntry.Kind kind);
```

`RegisteredEntry` trägt `getKind()` (`MODEL`/`LISTENER`/`COMMAND`), `getName()`,
`getDescription()` und `getType()`.

### `DatabaseService`

```java
void registerModel(Class<?> modelClass);

CompletableFuture<Void> saveAsync(T entity);
CompletableFuture<Void> updateAsync(T entity);
CompletableFuture<Void> deleteAsync(T entity);
CompletableFuture<T> findByIdAsync(Class<T> modelClass, ID id);
CompletableFuture<List<T>> findAllAsync(Class<T> modelClass);
```

Die Methoden ohne `Async` sind Kotlin-`suspend`-Funktionen — sie erwarten einen
`Continuation`-Parameter und sind aus Java nicht sinnvoll aufrufbar. Nimm immer
die `…Async`-Variante.

`connect()` und `close()` gehören EarthCore — ruf sie nicht selbst auf.

---

## Fallstricke

**`depend: [EarthCore]` vergessen.** Führt zu `NoClassDefFoundError` beim Start,
nicht zu einer verständlichen Meldung.

**Model ohne parameterlosen Konstruktor.** Sobald du einen Konstruktor mit
Parametern schreibst, erzeugt Java den Standardkonstruktor nicht mehr selbst. Du
musst ihn dann explizit hinschreiben, sonst wird das Model beim Registrieren
übersprungen.

**`final` Felder im Model.** Können per Reflection nicht gesetzt werden. Beim
Laden bleibt der Wert leer oder es fliegt eine Ausnahme.

**Die `suspend`-Methode statt der `…Async`-Variante erwischt.** Die
Autovervollständigung zeigt beide. `save(entity, continuation)` gibt dir ein
`Object` und tut nicht, was du denkst.

**Kein `exceptionally` am Future.** Fehler verschwinden lautlos.

**Bukkit-API im `thenAccept`-Callback.** Sieht aus, als würde es funktionieren,
und zerlegt dir bei Last den Server. Immer mit `getScheduler().runTask` zurück.

**Neues Feld an einem bestehenden Model.** `registerModel` macht nur
`CREATE TABLE IF NOT EXISTS`, kein `ALTER`. Die Spalte fehlt, und jede Abfrage auf
die Tabelle scheitert mit *Unknown column*. Bis auf Weiteres manuell:

```sql
ALTER TABLE shop_profiles ADD COLUMN notiz VARCHAR(255);
```

**`findAllAsync` auf einer großen Tabelle.** Lädt ohne Limit alles in den Heap.
Für Stamm- und Konfigurationsdaten gedacht, nicht für Log-Tabellen.

**Zwei Plugins, derselbe Datenbankname.** `of("shop")` in zwei Plugins gibt beiden
denselben Pool und dieselben Tabellen. Nimm einen Namen, der zu deinem Plugin
passt.
