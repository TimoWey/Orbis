# Orbis auf einem Server — Alpha.10

Der normale Build `orbis-0.1.0-alpha.10.jar` enthält die Welt-Engine, Minimap und einen einfachen Atlas/Globus für Multiplayer. Die Entwickleroberfläche und ihre automatischen Einzelspieler-Surveys bleiben im separaten Debug-Build. Server und Spieler verwenden dieselbe normale JAR; Kartenrendering startet ausschließlich auf dem Client.

## Versionen und Installation

- Minecraft Java **26.3**, **Java 25**.
- Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**.
- `orbis-0.1.0-alpha.10.jar` in `mods/` auf dem Fabric-Server.
- Für die Minimap: dieselben Versionen von Fabric Loader, Fabric API und Orbis auch im lokalen Minecraft-Profil jedes Spielers installieren.
- Keine separate Toroidal-World-JAR installieren. Alte Orbis-JARs vorher aus `mods/` in einen Backup-Ordner verschieben. Nie normalen und Debug-Build gleichzeitig installieren; `-sources.jar` gehört nicht in `mods/`.

Ein Vanilla-Client kann dem Orbis-Server auch ohne Fabric/Orbis beitreten: Das wurde mit einem zweiten echten Minecraft-26.3-Client lokal geprüft. Für Welt-Wrapping arbeitet die Engine auf dem Server. Ohne Client-Mod fehlen die Minimap und deutsche Orbis-Texte; englische Ersatztexte bleiben lesbar. Andere zusätzlich installierte Mods können eigene Client-Pflichten haben.

**M** öffnet/schließt den Atlas (umlegbar unter Steuerung). Oben: Karte, Globus, auf Spieler zentrieren, Schließen. Karte/Globus stehen mittig; Ziehen verschiebt/dreht, das Mausrad zoomt. Es gibt keine Zoom-Buttons auf der Karte. Die Tastaturkürzel +/− bleiben verfügbar. Unten: Minimap an/aus, Ecke, Größe, eigener Minimap-Zoom. **F1** blendet das HUD aus. Im unbeschränkten End bleibt die Ansicht flach. Im Nether zeigt Gelände den festen Schnitt bei Y=32.

Ab Alpha.10 funktioniert das Ziehen mit der linken Maustaste auch im normalen Spieler-Atlas unter Minecraft 26.3. Danach bleibt die Ansicht an der gewählten Stelle; **Zentrieren** beziehungsweise **Home** schaltet wieder auf Spieler-Verfolgung. Ist die Ansicht bereits zentriert, verändert der Knopf sie nicht sichtbar.

Die Einstellungen werden in `config/orbis-atlas.properties` atomar gespeichert und überstehen Client-Neustarts. Flacher Kartenzoom (sichtbare Breite in Blöcken), Globuszoom und Modus gelten je Dimension; Minimap-Ecke, Größe, Sichtbarkeit und Zoom global. Beim erneuten Öffnen folgt die Karte wieder dem Spieler; eine alte Position wird nicht gespeichert. Die Einstellungen gelten auch auf anderen Servern, ohne dort Gelände zu übertragen.

Der eigene Zeiger verwendet direkt Minecrafts originale weiße Karten-Textur, einschließlich ihrer 16 Blickrichtungen. Mitspieler erscheinen als grüne Vanilla-Kartenmarker; im großen Atlas zeigt Darüberfahren den Spielernamen. Atlas und Minimap zeigen Spieler der aktuellen Dimension innerhalb des sichtbaren Kartenausschnitts; für entfernte Mitspieler im Atlas herauszoomen. Auf dem Globus bleiben Marker der Rückseite verdeckt. Resourcepacks wirken wie bei Minecraft-Karten auch auf diese Originaltexturen.

Mit Alpha.10 auf Server und Karten-Client werden die Positionen zweimal pro Sekunde auch außerhalb der normalen Entity-Sichtweite übertragen. Die anderen Spieler selbst benötigen dafür keinen Orbis-Client. Unsichtbare Spieler und Zuschauer werden ausgeblendet; es werden keine Chunks dafür angefordert. Der Server sendet nur an Clients, die den neuen Kanal unterstützen, maximal 4.096 Spieler pro Dimension. Ohne passenden Server-Build sind lediglich bereits clientseitig geladene Mitspieler sichtbar. Abmeldungen und Dimensionswechsel ersetzen die Liste; beim Trennen wird sie gelöscht. Veraltete Fernpositionen verschwinden nach drei Sekunden ohne Aktualisierung.

Auf entfernten Servern zeichnet der Spieler-Atlas ausschließlich bereits empfangene Chunks auf, ohne Welt-Seed, Struktur-Suche oder zusätzliche Chunk-Anfragen. Der unentdeckte Hintergrund verwendet genau zwei Grautöne: dunkleres Wasser und helleres Land. Er ist symbolisch und keine Vorhersage echten Geländes. Pro Dimension bleiben bis zu 8.192 Gelände-Chunks während der Verbindung im Speicher; beim Verlassen des Servers wird dieser Verlauf gelöscht, damit etwa nach einem Server-Weltreset kein veraltetes Gelände erscheint. Im Einzelspieler verwendet auch der normale Build wieder den persistenten Gelände-Cache und den asynchronen Import bereits gespeicherter Welt-/Distant-Horizons-Daten aus dem bisherigen Entwickler-Atlas. Dabei werden keine zusätzlichen Live-Chunks geladen oder erzeugt. Der Cache liegt unter `orbis-atlas-cache/` im Minecraft-Profil und ist nach Welt und Generator-Konfiguration getrennt; ein Versions-/Modpackwechsel kann ihn sicherheitshalber invalidieren. Biome-/Struktur-Surveys laufen dafür nicht automatisch an. Bereits geladene Chunks werden wie im gestrigen Debug-Build nach Spielernähe priorisiert, mit bis zu 32 Captures pro Tick innerhalb eines weichen 2-ms-Budgets; nach zehn Sekunden werden bekannte Chunks erneut erfasst.

## Admin-Check auf dem Server

Operator-Rechtestufe **3** oder Serverkonsole:

- `/orbis check`: Biome und Strukturen in Oberwelt **und** Nether.
- `/orbis check biomes` / `/orbis check structures`: nur die gewählte Kategorie, jeweils beide Dimensionen.
- `/orbis check status`: Fortschritt oder letztes Ergebnis seit Serverstart.
- `/orbis check cancel`: laufenden Check abbrechen.

Es läuft maximal ein Check pro Server. Das Ergebnis erscheint als einfacher Text beim auslösenden Admin und im Serverlog, auch mit Vanilla-Client. Erwartet werden die vom aktuellen Generator möglichen Biome und Strukturen der jeweiligen Dimension, einschließlich eines separaten Stronghold-Portalraum-Nachweises.

Der Check lädt/generiert keine Welt-Chunks. Er fragt den Biome-Generator ab und prüft mögliche Strukturstarts in nicht gespeicherten Scratch-Chunks. Er arbeitet auf dem Serverthread mit einem weichen Budget von 3 ms pro Tick, maximal einer Strukturplatzierung pro Tick, bis zu 33.792 Biome-Abfragen und 2.048 Struktur-Kandidaten je Dimension. Einzelne native Generatoraufrufe können länger dauern. Nach 2.400 Ticks je Dimension endet er spätestens mit einem Hinweis auf das erreichte Zeitbudget (bei 20 TPS etwa zwei Minuten je Dimension).

**„Nicht gefunden“ bedeutet unbestätigt, nicht bewiesen fehlend.** Ein vollständiger negativer Nachweis würde eine wesentlich größere Suche erfordern. Strukturen sind Generator-Vorhersagen; entfernte Bauwerke oder vor einem Generatorwechsel gespeicherte Chunks werden damit nicht überprüft. Biome/Strukturen außerhalb der Generator-Konfiguration dieser Dimension zählen nicht als fehlend. Kein Preset garantiert vollständige Abdeckung.

## Eine neue Server-Welt anlegen

1. Fabric-Server für die oben genannten Versionen installieren; Fabric API und Orbis in `mods/` legen.
2. Minecraft-EULA lesen und wie beim üblichen Serverstart akzeptieren.
3. Vor der ersten Welterzeugung in `server.properties` beispielsweise Folgendes setzen:

```properties
level-name=orbis-world
level-type=orbis:tiny
level-seed=8675309
online-mode=true
view-distance=8
simulation-distance=6
```

4. Mit Java 25 starten, beispielsweise `java -Xms2G -Xmx4G -jar fabric-server-launch.jar nogui` (Dateiname abhängig vom Fabric-Installer). 4 GB sind ein Startwert, keine Kapazitätsgarantie.
5. Spieler bei aktivierter Gästeliste über die Serverkonsole mit `whitelist add Spielername` freischalten.
6. Im Log die Zeilen `World shape:` prüfen. `/orbis` zeigt die gespeicherte Größe und muss die Welt als Orbis erkennen. Beide X/Z-Achsen müssen im Overworld/Nether als `TORUS` erscheinen, das End als `not wrapped`.

| `level-type` | Overworld-Breite, Blöcke | Nether-Breite, Blöcke |
| --- | ---: | ---: |
| `orbis:tiny` | 16.384 | 2.048 |
| `orbis:small` | 141.312 | 17.664 |
| `orbis:normal` | 1.224.704 | 153.088 |
| `orbis:large` | 10.000.128 | 1.250.016 |
| `orbis:earth` | 23.170.560 | 2.896.320 |

Diese Größen entsprechen den aktuellen Alpha.6–10-Presets, nicht den kleineren historischen Alpha-Vorgaben. Für den ersten gemeinsamen Test ist Tiny überschaubarer. Es wird keine ganze Welt vorgeneriert. Kein Preset garantiert alle Biome und Strukturen.

`level-type` wird nur beim Erstellen einer **neuen** Welt angewendet. Eine schon vorhandene Welt wird dadurch weder konvertiert noch verkleinert. Für eine eigene Größe: eine neue Orbis-Welt im Einzelspieler erstellen, sauber beenden und den kompletten Save in den Server-Weltordner kopieren. Vor Änderungen vorhandene Welten sichern.

## Build

```sh
bash gradlew build
python3 tools/verify_standalone.py build/libs/orbis-0.1.0-alpha.10.jar
```

Der Build prüft beide Archive, fehlende/verbotene Debug-Inhalte, die Server-Preset-Codecs und die gespeicherte Dimensionspolitik. Für Entwickler separat: `bash gradlew build -PorbisDebug=true`; Ausgabe unter `build/debug/libs/`.

Siehe [Testprotokoll und echte Minecraft-Screenshots](verification/alpha10/RESULTS.md) für die tatsächlich geprüften Funktionen und Grenzen dieses Alpha-Builds.
