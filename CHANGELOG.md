# Changelog

Alle nennenswerten Änderungen an EarthCore. Das Format folgt lose
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/), die Versionierung
[Semantic Versioning](https://semver.org/lang/de/): neue Funktionen erhöhen die
Minor-, Fehlerbehebungen die Patch-Version.

Die Version steht ausschließlich in `build.gradle.kts`; die `plugin.yml` zieht
sie sich beim Bauen von dort.

---

## [1.12.0]

### Neu

- **GUI-System.** Deklarativ und reaktiv: `render(view)` beschreibt das Menue fuer
  den aktuellen Zustand, `refresh()` zeichnet nur die tatsaechlich geaenderten
  Slots neu. Kein manuelles `setItem`, damit auch kein vergessenes Update.
- **Masken** statt Slot-Rechnerei: `mask("#########", "#.......#")`, dazu
  `bind(symbol, item)`, `slots(symbol)` und `paginate(symbol, eintraege)`.
- **Seiten** mit `Page` (Index, Anzahl, erste/letzte) und fertigen Blaetter-Buttons,
  die sich am Anfang und Ende selbst ausgrauen.
- **Nachladen** ueber `view.load(key) { ... }`: laeuft abseits vom Tick-Thread,
  liefert zunaechst `null` fuer einen Platzhalter und zeichnet nach dem Laden von
  selbst neu. Pro Schluessel genau ein Ladevorgang.
- **Live-Aktualisierung** ueber `onTick()`.
- **Navigation** mit Verlauf: `open`, `back`, `replace` und ein fertiger
  Zurueck-Knopf.
- **Geteilte Menues** ueber `shared = true` - eine Instanz, viele Betrachter, ein
  `refresh()` erreicht alle.
- **Weitere Inventartypen** (Hopper, Dispenser, Dropper, Ofen, Braustand) und der
  **Amboss als Texteingabe** ueber `AnvilPrompt`.

Klicks sind immer abgebrochen, bis ein Handler etwas anderes tut. Fehler in
`render` oder in einem Klick-Handler landen im Log, statt das Menue oder den
Tick-Thread mitzureissen. Sitzungen raeumt EarthCore beim Schliessen und beim
Verlassen des Servers selbst auf.

## [1.11.0]

### Neu

- **`ItemBuilder`** zum Erstellen von Items. Unveraenderlich: jeder Aufruf liefert
  einen neuen Builder, Vorlagen lassen sich damit gefahrlos mehrfach ableiten.
  Name und Lore als MiniMessage oder fertige `Component`, kursiv wird automatisch
  abgeschaltet. Abgedeckt sind Grundlagen, PersistentDataContainer, Spielerkoepfe
  sowie Ruestungsfarbe, Traenke, Buecher, Feuerwerk und Banner.
- Intern ein Mix aus beiden Paper-APIs: **Data Components** fuer Glitzern ohne
  Verzauberung, Standardname, Stapelgroesse, CustomModelData und Kopf-Profile -
  alles Dinge, die mit `ItemMeta` gar nicht oder nur ueber Umwege gehen. Der Rest
  laeuft ueber das stabile `ItemMeta`. Der Builder kapselt das, ein spaeterer
  Wechsel bricht keine abhaengigen Plugins.
- Passt ein Aufruf nicht zum Material, gibt es eine klare `IllegalStateException`
  beim `build()` statt eines stillen Nichts. Kaputte Kopf-Texturen fallen schon
  beim `skullTexture(...)` auf.

### Intern

- MockBukkit als Test-Abhaengigkeit, damit `build()` gegen einen echten
  Server-Mock geprueft wird und nicht nur uebersetzt.

## [1.10.0]

### Neu

- **Zentrales Logging ueber `LogbookProvider`.** Ein Aufruf schreibt gleichzeitig
  in die Konsole, in die Tabelle `log_entries` und an passende Discord-Webhooks.
  Jeder Eintrag hat eine Kategorie, einen Level und optional Akteur, Details und
  eine Ausnahme. `record(...)` ist der Einstieg fuer nachvollziehbare Aktionen.
- **Discord-Webhooks mit Routing.** Pro Webhook ein Mindest-Level und eine
  Kategorienliste, konfigurierbar in der `config.json`. Der Versand sammelt bis
  zu zehn Embeds pro Nachricht, haelt bei `429` die `Retry-After`-Pause ein und
  laeuft asynchron - der Tick-Thread wartet nie. Die Webhook-URL wird in
  Meldungen maskiert.
- **`@Column(text = true)`** legt eine Spalte als `LONGTEXT` statt `VARCHAR` an -
  noetig fuer Nachrichten und Stacktraces.
- **`DatabaseService.execute(sql, ...)`** als Ausweichweg fuer Abfragen, die sich
  nicht ueber Models ausdruecken lassen. Parameter werden gebunden, nicht
  eingesetzt. Wird intern fuers Aufraeumen der Logtabelle genutzt.

## [1.9.0]

### Neu

- **Datenbank-Migrationen.** `registerModel` vergleicht die Ist-Spalten der
  Tabelle gegen das Model und ergänzt fehlende per `ALTER TABLE`. Ein neues Feld
  an einem bestehenden Model bricht damit nicht mehr jede Abfrage mit
  *Unknown column*. Spalten ohne Feld im Model werden nur gemeldet, nie gelöscht.
- **Config-Versionierung.** `ConfigVersioning` und `ConfigMigration` erlauben
  Migrationsschritte für Konfigurationsdateien — nötig, sobald du Schlüssel
  umbenennst oder entfernst. Ohne Angabe bleibt alles wie bisher, es landet dann
  auch kein Versionsschlüssel in der Datei.
- **`CoreVersion`.** Neuer Service im `ServicesManager`. Abhängige Plugins können
  mit `requireAtLeast("1.9.0")` prüfen, ob der laufende Core neu genug ist, statt
  später an einem `NoSuchMethodError` zu scheitern. Der Vergleich ist semver-
  korrekt: `1.10.0` ist neuer als `1.9.0`.

### Geändert

- `ColumnMeta.field` heißt jetzt `javaField` — `field` ist in Kotlin-Property-
  Gettern reserviert und kollidierte mit dem Backing Field.

## [1.8.0]

### Geändert

- Der Nachrichten-Präfix trägt den Namen des **aufrufenden** Plugins. Ein
  Cooldown aus EarthShop meldet sich als `[EarthShop]`, nicht als `[EarthCore]` —
  für den Spieler wirkt es wie ein einziges Plugin. `messages.json` enthält dafür
  die Vorlage `%plugin%`.

## [1.7.1]

### Neu

- **`CooldownRegistry`.** Cooldowns pro Spieler, in EarthCores Datenbank abgelegt
  und damit neustartfest. Davor sitzt ein Speicher-Cache, der beim Start gefüllt
  wird — alle Abfragen bleiben synchron und blockieren den Tick-Thread nicht.
- **`@Cooldown`** für Commands, verarbeitet vom `AutoRegistrar`. `seconds`,
  `minutes` und `hours` werden addiert; `key` teilt einen Cooldown zwischen
  mehreren Commands, `bypassPermission` hängt ihn aus. Gestartet wird erst nach
  einem erfolgreichen Durchlauf.
- **`messages.json`** im Resource-Ordner als zentrale Ablage für Spielertexte,
  ausgegeben über MiniMessage.

### Behoben

- `ConfigDefaults.resource()` suchte mit dem ClassLoader von EarthCore und damit
  im falschen Jar. Jedes fremde Plugin brach beim Start mit *Ressource fehlt* ab.
  Jetzt wird der ClassLoader der aufrufenden Klasse ermittelt.

## [1.6.0]

Erster committeter Stand. Die Nummern davor waren Zwischenstände während der
Entwicklung und existieren nicht als Commit.

### Neu

- **`ConfigService`** — JSON-Konfiguration über Punkt-Pfade, Defaults werden beim
  Start nachgemergt, kaputte Dateien weggesichert statt überschrieben.
- **`DatabaseProvider`** — eine eigene MariaDB-Datenbank pro Plugin, bei Bedarf
  angelegt. Ein HikariCP-Pool je Datenbankname.
- **`DatabaseService`** — CRUD auf annotierten Models über `@Table`,
  `@PrimaryKey`, `@Column` und `@JsonColumn`. Nie auf dem Tick-Thread.
- **`AutoRegistrar`** — durchsucht die angegebenen Packages und registriert
  Models, Listener (`@AutoListener`) und Commands (`@AutoCommand`) selbst.
  Commands laufen über Papers Brigadier-Lifecycle.
- **Kotlin und Java gleichwertig** — `Class<T>` statt `KClass<T>`, Annotationen
  greifen auf Kotlin-Properties *und* Java-Feldern, zu jeder `suspend`-Methode
  gibt es eine `CompletableFuture`-Variante.

[1.12.0]: https://github.com/EarthCraftLabs/EarthCore/releases/tag/v1.12.0
[1.11.0]: https://github.com/EarthCraftLabs/EarthCore/releases/tag/v1.11.0
[1.10.0]: https://github.com/EarthCraftLabs/EarthCore/releases/tag/v1.10.0
[1.9.0]: https://github.com/EarthCraftLabs/EarthCore/releases/tag/v1.9.0
[1.8.0]: https://github.com/EarthCraftLabs/EarthCore/releases/tag/v1.8.0
[1.7.1]: https://github.com/EarthCraftLabs/EarthCore/releases/tag/v1.7.1
[1.6.0]: https://github.com/EarthCraftLabs/EarthCore/releases/tag/v1.6.0
