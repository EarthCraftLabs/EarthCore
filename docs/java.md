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
- [Cooldowns](#cooldowns)
- [Items bauen](#items-bauen)
- [Menues bauen](#menues-bauen)
- [Logging](#logging)
- [Konfiguration](#konfiguration)
- [Versionierung](#versionierung)
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
    compileOnly("de.mecrytv:earthcore:1.14.0")
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
    <version>1.14.0</version>
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

## Cooldowns

Cooldowns liegen in EarthCores Datenbank und **überleben einen Serverneustart**.
Gelesen wird trotzdem aus dem Speicher: EarthCore lädt beim Start alle noch
laufenden Cooldowns in einen Cache und schreibt Änderungen im Hintergrund zurück.
Deshalb sind alle Abfragen synchron — kein `CompletableFuture`, kein Blockieren
des Tick-Threads.

### Per Annotation am Command

```java
@AutoCommand(name = "kit", description = "Holt dein Kit", permission = "earthjavashop.kit")
@Cooldown(hours = 24, bypassPermission = "earthjavashop.kit.bypass")
public class KitCommand implements BasicCommand {

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        source.getSender().sendMessage("Kit ausgegeben.");
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

```java
@AutoCommand(name = "warp")
@Cooldown(minutes = 1, seconds = 30, key = "teleport")
public class WarpCommand implements BasicCommand { }
```

Die Konsole hat nie einen Cooldown — nur Spieler werden gebremst.

### Direkt über die API

```java
import de.mecrytv.earthcore.cooldown.api.CooldownRegistry;
import java.time.Duration;

CooldownRegistry cooldowns = getServer().getServicesManager().load(CooldownRegistry.class);
UUID uuid = player.getUniqueId();

cooldowns.start(uuid, "teleport", Duration.ofSeconds(30));
cooldowns.extend(uuid, "teleport", Duration.ofSeconds(10));

if (cooldowns.isActive(uuid, "teleport")) {
    player.sendMessage("Noch " + cooldowns.remaining(uuid, "teleport").getSeconds() + "s");
    return;
}

cooldowns.clear(uuid, "teleport");
cooldowns.clearAll(uuid);
Set<String> laufende = cooldowns.keys(uuid);
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

```java
@Cooldown(hours = 24, messageKey = "kit.active")
```

Fehlende Schlüssel werden beim Start aus dem Jar ergänzt, deine Änderungen bleiben
stehen.

---

## Items bauen

`ItemBuilder` ist unveraenderlich: jeder Aufruf liefert einen neuen Builder, erst
`build()` erzeugt den `ItemStack`.

```java
import de.mecrytv.earthcore.item.api.ItemBuilder;

ItemStack schwert = ItemBuilder.of(Material.DIAMOND_SWORD)
        .name("<gold>Scharfes Schwert")
        .lore("<gray>Geschmiedet in EarthCraft", "<dark_gray>Einzelstueck")
        .enchant(Enchantment.SHARPNESS, 5)
        .flags(ItemFlag.HIDE_ENCHANTS)
        .unbreakable(true)
        .glint(true)
        .tag(new NamespacedKey(plugin, "artikel"), "schwert-01")
        .build();
```

Name und Lore nimmst du als MiniMessage entgegen - die Texte kommen aus deinem
eigenen Plugin, EarthCore parst sie nur. Wenn du bereits eine `Component` hast,
gibst du sie direkt: `name(component)` bzw. `loreComponents(liste)`.

**Kursiv wird automatisch abgeschaltet.** Minecraft schreibt Namen und Lore sonst
schraeg. Setzt du `<i>` ausdruecklich, bleibt es erhalten.

### Koepfe

```java
ItemBuilder.of(Material.PLAYER_HEAD).skull(spieler).build();
ItemBuilder.of(Material.PLAYER_HEAD).skullTexture(base64).build();
```

Die Base64-Textur wird sofort geprueft: kein gueltiges Base64, kein
`textures.SKIN.url` oder eine URL ausserhalb von `textures.minecraft.net` fliegen
mit klarer Meldung raus, statt spaeter einen unsichtbaren Kopf zu ergeben.

### Spezialisierte Items

```java
ItemBuilder.of(Material.LEATHER_CHESTPLATE).armorColor(Color.RED).build();
ItemBuilder.of(Material.POTION).potion(PotionType.STRENGTH).build();
ItemBuilder.of(Material.WRITTEN_BOOK).book("<gold>Regeln", "EarthCraft", List.of("Seite eins")).build();
ItemBuilder.of(Material.FIREWORK_ROCKET).firework(2, effekt).build();
ItemBuilder.of(Material.WHITE_BANNER).bannerPatterns(muster).build();
```

### Warum unveraenderlich

Jeder Aufruf liefert einen **neuen** Builder. Damit kannst du Vorlagen anlegen und
mehrfach ableiten, ohne dass sie sich gegenseitig veraendern:

```java
ItemBuilder vorlage = ItemBuilder.of(Material.PAPER).name("<gold>Gutschein");

ItemStack einzeln = vorlage.amount(1).build();
ItemStack stapel = vorlage.amount(64).lore("<gray>Grosspackung").build();
```

Bei einem mutierenden Builder haette die zweite Ableitung die erste ueberschrieben
- ein Fehler, der erst im Spiel auffaellt.

`build()` liefert jedes Mal einen frischen `ItemStack`; zwei Aufrufe teilen sich
nichts.

### Alte und neue Paper-API

Der Builder nutzt beides und versteckt den Unterschied. Ueber **Data Components**
laufen die Dinge, die mit `ItemMeta` gar nicht oder nur ueber Umwege gehen:

| Aufruf | Warum Data Component |
|---|---|
| `glint(true)` | Glitzern ohne Verzauberung - mit `ItemMeta` braeuchte es eine Schein-Verzauberung plus `HIDE_ENCHANTS` |
| `itemName(...)` | Setzt den *Standardnamen*, nicht den Anzeigenamen. Das Item gilt im Amboss nicht als umbenannt. |
| `maxStackSize(16)` | Mit `ItemMeta` gar nicht moeglich |
| `customModelData(...)` | Die `ItemMeta`-Variante ist seit 1.21.4 veraltet |
| `skullTexture(...)` | Setzt das Profil direkt, ohne die veralteten `SkullMeta`-Wege |

Alles andere laeuft ueber `ItemMeta`, weil das stabil ist. Da der Builder das
kapselt, koennen wir intern wechseln, ohne dass du eine Zeile anfassen musst.

### Wenn der Aufruf nicht zum Material passt

`armorColor(...)` auf einem Stein ist ein Fehler, kein stilles Nichts:

```
IllegalStateException: STONE hat keine LeatherArmorMeta - dieser Aufruf passt nicht zum Material
```

Der Fehler faellt beim `build()`, nicht erst wenn ein Spieler das Item in der Hand
haelt. Eine kaputte Kopf-Textur meldet sich sogar noch frueher, direkt beim
`skullTexture(...)`.

### Ausweichwege

Fuer alles, was der Builder nicht kennt:

```java
ItemBuilder.of(Material.STONE)
        .edit(stack -> stack.setAmount(7))
        .meta(SkullMeta.class, meta -> meta.setNoteBlockSound(key))
        .build();
```

---

## Menues bauen

Das GUI-System ist **deklarativ**: du beschreibst in `render`, wie das Menue bei
einem gegebenen Zustand aussieht. Zustand aendern, `refresh()` rufen - EarthCore
zeichnet nur die Slots neu, die sich tatsaechlich geaendert haben. Ein manuelles
`setItem` gibt es nicht, und der Fehler *vergessen zu aktualisieren* kann nicht
passieren.

```java
public class ShopGui extends Gui {

    private final List<Artikel> artikel;
    private boolean nurGuenstig = false;

    public ShopGui(List<Artikel> artikel) {
        super(GuiType.chest(4), Component.text("Shop"));
        this.artikel = artikel;
    }

    @Override
    public void render(GuiView view) {
        view.mask("#########", "#.......#", "#.......#", "P###F###N");
        view.bind('#', Buttons.filler());

        view.paginate('.', artikel, eintrag -> GuiItem.of(
                ItemBuilder.of(Material.EMERALD).name("<green>" + eintrag.name()),
                klick -> klick.getViewer().sendMessage("Gekauft: " + eintrag.name())));

        view.item(view.slots('P').get(0), Buttons.previousPage(view));
        view.item(view.slots('N').get(0), Buttons.nextPage(view));
        view.item(view.slots('F').get(0), GuiItem.of(filterItem(), klick -> {
            nurGuenstig = !nurGuenstig;
            refresh();
        }));
    }
}

GuiProvider guis = getServer().getServicesManager().load(GuiProvider.class);
guis.open(spieler, new ShopGui(artikel));
```

### Zusammenspiel mit dem ItemBuilder

Jede Zeichenmethode nimmt den `ItemBuilder` direkt entgegen, ein `.build()` am Ende
brauchst du nicht:

```java
view.item(11, ItemBuilder.of(Material.DIAMOND).name("<aqua>Diamant"));
view.button(15, ItemBuilder.of(Material.EMERALD).name("<green>Kaufen"), klick -> { });
view.bind('#', ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" "));
view.fill(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" "));
```

Gebaut wird bei jedem Zeichnen neu, jeder Slot bekommt also seinen eigenen
`ItemStack`. Die `ItemStack`-Varianten gibt es weiterhin, wenn du ein Item schon
fertig hast.

### Masken statt Slot-Nummern

`mask(...)` beschreibt das Layout als Bild. Jedes Zeichen ist ein Symbol, das du
danach belegst:

- `bind('#', item)` legt das Item auf **alle** Slots mit diesem Symbol
- `slots('P')` gibt dir die Slot-Nummern zurueck, wenn du sie einzeln brauchst
- `paginate('.', eintraege, render)` verteilt eine Liste auf die Slots des Symbols

Leerzeichen in der Maske dienen nur der Lesbarkeit: `"# # # # #"` ist dasselbe wie
`"#####"`. Ungleich lange Zeilen werden mit klarer Meldung abgelehnt.

### Seiten

`paginate` uebernimmt das Rechnen. `view.getPage()` sagt dir, wo du stehst
(`getIndex()`, `getCount()`, `getFirst()`, `getLast()`), `nextPage()` und
`previousPage()` blaettern und loesen automatisch ein Neuzeichnen aus. Die fertigen
Blaetter-Buttons in `Buttons` graut EarthCore am Anfang und Ende selbst aus.

### Nachladen aus der Datenbank

```java
@Override
public void render(GuiView view) {
    List<Artikel> artikel = view.load("artikel", () -> database.findAllAsync(Artikel.class).join());
    if (artikel == null) {
        view.bind('.', Buttons.loading());
        return;
    }
    view.paginate('.', artikel, eintrag -> ...);
}
```

Der erste `render` startet das Laden **abseits vom Tick-Thread** und liefert
`null`. Sobald die Daten da sind, zeichnet EarthCore das Menue von selbst neu und
`load` gibt den Wert zurueck. Pro Schluessel wird genau einmal geladen, auch wenn
zwischendurch mehrfach gezeichnet wird. Faellt der Ladevorgang auf die Nase, steht
es im Log und das Menue bleibt bedienbar.

### Live-Aktualisierung

```java
@Override
public boolean onTick() {
    return true;
}
```

Gibt `onTick()` `true` zurueck, zeichnet EarthCore das Menue einmal pro Sekunde
neu - dank Diffing kostet das nur die Slots, die sich wirklich aendern.

### Navigation

```java
view.open(new EinstellungenGui());   // legt das aktuelle Menue auf den Verlauf
view.back();                         // eine Ebene zurueck
guis.replace(spieler, gui);          // ohne Verlaufseintrag
```

`Buttons.back(view)` ist der fertige Zurueck-Knopf. `view.getCanGoBack()` sagt dir,
ob es ueberhaupt etwas gibt, wohin.

### Geteilte Menues

```java
public class TeamKiste extends Gui {
    public TeamKiste() {
        super(GuiType.chest(3), Component.text("Team"), true);
    }
}
```

Standard ist eine Instanz pro Spieler. Mit `shared = true` teilen sich alle
Betrachter eine Instanz - ein `refresh()` erreicht dann alle gleichzeitig. Gedacht
fuer Auktionen oder Team-Kisten.

### Weitere Inventartypen

```java
new Gui(GuiType.HOPPER, titel) { ... };
new Gui(GuiType.DISPENSER, titel) { ... };
new Gui(GuiType.chest(6), titel) { ... };
```

Fuer Texteingabe gibt es den Amboss:

```java
guis.open(spieler, new AnvilPrompt(
        Component.text("Name eingeben"),
        "Vorschlag",
        text -> spieler.sendMessage("Eingegeben: " + text)));
```

### Was EarthCore dabei abfaengt

Klicks im Menue sind **immer abgebrochen**, dein Handler entscheidet, was
passiert. Willst du, dass Spieler ihr eigenes Inventar bedienen duerfen, setzt du
`setInteractablePlayerInventory(true)`.

Wirft dein `render` oder ein Klick-Handler, landet das im Log statt den
Tick-Thread mitzureissen - das Menue bleibt offen und bedienbar.

Schliesst ein Spieler das Menue oder verlaesst den Server, raeumt EarthCore die
Sitzung selbst auf. Du musst nichts abmelden.

---

## Logging

EarthCore bringt ein zentrales Logbuch mit. Jeder Eintrag geht gleichzeitig an
die Konsole, in die Datenbank und - wenn er zum Filter passt - an einen
Discord-Webhook.

```java
import de.mecrytv.earthcore.logging.api.Logbook;
import de.mecrytv.earthcore.logging.api.LogbookProvider;

LogbookProvider provider = getServer().getServicesManager().load(LogbookProvider.class);
Logbook logbook = provider.of(this);

logbook.info("shop", "Laden geoeffnet");
logbook.warn("shop", "Lager fast leer");
logbook.error("shop", "Kauf fehlgeschlagen", exception);
logbook.debug("shop", "Nur sichtbar, wenn debug an ist");
```

Der erste Parameter ist die **Kategorie**. Danach wird nach Discord geroutet und
in der Datenbank gefiltert - waehl sie so, wie du spaeter suchen willst
(`shop`, `moderation`, `technik`).

### Nachvollziehbare Aktionen

Fuer *wer hat was getan* gibt es `record`, mit Akteur und beliebigen Details:

```java
logbook.record(
        "moderation",
        team.getUniqueId(),
        "Bann ausgesprochen",
        Map.of("ziel", opfer.getName(), "grund", "Griefing", "dauer", "7d"));
```

Die Details landen als JSON in der Datenbank und als Felder im Discord-Embed.
Bei `error` ist die Ausnahme das dritte Argument - ohne eine gibst du `null` mit.

### Volle Kontrolle

```java
logbook.log(new LogEntry(
        LogLevel.WARN,
        "technik",
        "Backup uebersprungen",
        "",
        null,
        Map.of("grund", "kein Platz"),
        null,
        Instant.now()));
```

Aus Java braucht der Konstruktor alle Felder - Kotlin-Standardwerte gibt es dort
nicht. Fuer den Normalfall reichen die Methoden oben. Ein leerer String bei
`plugin` wird von EarthCore gefuellt.

### Einrichtung in der config.json

```json
"logging": {
  "debug": false,
  "retentionDays": 30,
  "discord": [
    { "url": "https://discord.com/api/webhooks/...", "minLevel": "WARN", "categories": [], "username": "EarthCraft" },
    { "url": "https://discord.com/api/webhooks/...", "minLevel": "INFO", "categories": ["moderation"], "username": "EarthCraft" }
  ]
}
```

Pro Eintrag ein Webhook. `minLevel` ist die Untergrenze (`DEBUG`, `INFO`, `WARN`,
`ERROR`), `categories` schraenkt zusaetzlich ein - leer heisst alle. So gehen
Fehler in den Technik-Kanal und Team-Aktionen in den Team-Kanal, ohne sich zu
vermischen.

`retentionDays` raeumt die Tabelle `log_entries` auf; `0` schaltet das Aufraeumen
ab. `debug` entscheidet, ob `debug(...)`-Eintraege ueberhaupt in der Konsole
landen.

### Was EarthCore dabei abfaengt

Discord wird **nie** direkt aus deinem Aufruf heraus angesprochen. Eintraege
sammeln sich in einer Warteschlange und gehen alle zwei Sekunden gebuendelt raus,
bis zu zehn Embeds pro Nachricht. Antwortet Discord mit `429`, wird die Pause aus
`Retry-After` eingehalten und danach weitergemacht. Die Warteschlange ist
gedeckelt, ein Fehlersturm kann also keinen Speicher fressen.

Faellt ein Ziel aus, laufen die anderen weiter - und Fehler eines Logziels gehen
an den normalen Plugin-Logger, nicht wieder ins Logbuch. Sonst haettest du eine
Endlosschleife.

Die Webhook-URL taucht in keiner Meldung auf; im Log steht nur `...abc123`.

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

Statt `ConfigDefaults.Companion.model(...)` kannst du eine fertige Datei aus
deinem `resources`-Ordner als Vorlage nehmen:

```java
ConfigDefaults.Companion.resource("config.json", null)
```

Gesucht wird im Jar **deines** Plugins, nicht in EarthCores. Brauchst du einen
anderen ClassLoader, gibst du ihn statt `null` mit.

Fehlende Schlüssel werden beim Start aus den Defaults ergänzt, vorhandene Werte
bleiben unangetastet. Eine kaputte Datei wird weggesichert statt überschrieben.

---

## Versionierung

### Gegen eine Mindestversion pruefen

```java
import de.mecrytv.earthcore.version.api.CoreVersion;

@Override
public void onEnable() {
    CoreVersion core = getServer().getServicesManager().load(CoreVersion.class);
    if (core == null) {
        throw new IllegalStateException("EarthCore ist nicht geladen");
    }
    core.requireAtLeast("1.9.0");
    ...
}
```

`requireAtLeast` wirft mit einer lesbaren Meldung, wenn der laufende Core zu alt
ist — deutlich hilfreicher als ein `NoSuchMethodError` mitten im Spielbetrieb.
`isAtLeast(...)` gibt stattdessen ein `boolean` zurueck, wenn du eine Funktion
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
Konstante fuer Enums, `[]` oder `{}` fuer JSON-Spalten. In Java sind das genau
die primitiven Felder — Objektfelder sind ohnehin nullbar und bekommen keinen
Default.

Umbenannte oder geloeschte Felder und Typaenderungen macht EarthCore **nicht**.
Ueberzaehlige Spalten werden nur gemeldet.

### Konfigurationsdateien migrieren

Neue Schluessel brauchen nichts — die werden beim Start aus den Defaults
ergaenzt. Erst wenn du Schluessel **umbenennst oder entfernst**, brauchst du
Versionierung:

```java
import de.mecrytv.earthcore.config.ConfigMigration;
import de.mecrytv.earthcore.config.ConfigVersioning;

import java.util.Map;

ConfigVersioning versioning = new ConfigVersioning(
        2,
        "configVersion",
        Map.of(2, (ConfigMigration) root -> {
            var server = root.getAsJsonObject("server");
            server.add("name", server.remove("title"));
        }));

configService = new JsonConfigService(
        new File(getDataFolder(), "config.json"),
        ConfigDefaults.Companion.model(new ShopConfig()),
        versioning,
        JsonConfigService.Companion.defaultGson(true),
        new PatternPlaceholderResolver("%", "%"),
        getLogger());
```

> `ConfigVersioning` braucht aus Java alle drei Argumente — Kotlin-Standardwerte
> gibt es aus Java nicht. Den `JsonConfigService` gibt es weiterhin auch mit fuenf
> Argumenten ohne `versioning`; bestehender Code muss also nicht angefasst werden.

Der Schluessel `configVersion` landet in der Datei, sobald `current` groesser 1
ist oder Schritte hinterlegt sind — vorher bleibt die Datei unberuehrt. Beim
Start laufen genau die Schritte, die zwischen dem Stand auf der Platte und
`current` liegen, in aufsteigender Reihenfolge. Eine Datei ohne Versionsschluessel
gilt als Version 1.

Scheitert ein Schritt, bricht der Start ab und die Datei auf der Platte bleibt
unveraendert — lieber ein lauter Fehler als eine halb migrierte Konfiguration.

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
| `@Cooldown` | `@Cooldown(hours = 24, bypassPermission = "…", messageKey = "kit.active")` |

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

### `CooldownRegistry`

```java
void start(UUID subject, String key, Duration duration);
void extend(UUID subject, String key, Duration by);
boolean clear(UUID subject, String key);
int clearAll(UUID subject);
boolean isActive(UUID subject, String key);
Duration remaining(UUID subject, String key);
Set<String> keys(UUID subject);
```

Alle Methoden sind synchron — hier gibt es bewusst keine `…Async`-Varianten.
Persistiert wird im Hintergrund in EarthCores Datenbank, Tabelle `cooldowns`.

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

**Cooldown-Dauer von 0.** `@Cooldown` ohne `seconds`/`minutes`/`hours` und
`start(..., Duration.ZERO)` werfen beide eine `IllegalArgumentException`. Beim
Command fliegt sie schon beim Registrieren, nicht erst beim Ausführen.

**`findAllAsync` auf einer großen Tabelle.** Lädt ohne Limit alles in den Heap.
Für Stamm- und Konfigurationsdaten gedacht, nicht für Log-Tabellen.

**Zwei Plugins, derselbe Datenbankname.** `of("shop")` in zwei Plugins gibt beiden
denselben Pool und dieselben Tabellen. Nimm einen Namen, der zu deinem Plugin
passt.
