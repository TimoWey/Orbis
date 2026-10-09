# Orbis auf einem Server — Alpha.7

Der normale Build `orbis-0.1.0-alpha.7.jar` enthält die Welt-Engine und eine einfache Client-Minimap. Er enthält **keinen Debug-Atlas, keine Biome-/Struktursuche und keine Debug-Klassen**. Server und Spieler verwenden dieselbe JAR; die Minimap wird ausschließlich auf dem Client initialisiert.

## Versionen und Installation

- Minecraft Java **26.3**, **Java 25**.
- Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**.
- `orbis-0.1.0-alpha.7.jar` in `mods/` auf dem Fabric-Server.
- Für die Minimap: dieselben Versionen von Fabric Loader, Fabric API und Orbis auch im lokalen Minecraft-Profil jedes Spielers installieren.
- Keine separate Toroidal-World-JAR installieren. Alte Orbis-JARs vorher aus `mods/` in einen Backup-Ordner verschieben. Nie normalen und Debug-Build gleichzeitig installieren; `-sources.jar` gehört nicht in `mods/`.

Ein Vanilla-Client kann dem Orbis-Server auch ohne Fabric/Orbis beitreten: Das wurde mit einem zweiten echten Minecraft-26.3-Client lokal geprüft. Für Welt-Wrapping arbeitet die Engine auf dem Server. Ohne Client-Mod fehlen die Minimap und deutsche Orbis-Texte; englische Ersatztexte bleiben lesbar. Andere zusätzlich installierte Mods können eigene Client-Pflichten haben.

Die Minimap wird nicht vom Server an Spieler verteilt. Sie zeigt bereits empfangenes Gelände in einem Ausschnitt von 128 × 128 Blöcken, einen Spieler-/Richtungsmarker und Koordinaten. **M** blendet sie ein/aus (unter Steuerung umbelegbar), **F1** blendet das gesamte HUD aus. Im Nether zeigt sie einen festen Schnitt bei Y=32. Sie verrät weder den Welt-Seed noch Strukturen oder nicht geladene Regionen. Der große Flat-/Globe-Debug-Atlas bleibt separat und einzelspielergebunden.

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

Diese Größen entsprechen den aktuellen Alpha.6/7-Presets, nicht den kleineren historischen Alpha-Vorgaben. Für den ersten gemeinsamen Test ist Tiny überschaubarer. Es wird keine ganze Welt vorgeneriert. Kein Preset garantiert alle Biome und Strukturen.

`level-type` wird nur beim Erstellen einer **neuen** Welt angewendet. Eine schon vorhandene Welt wird dadurch weder konvertiert noch verkleinert. Für eine eigene Größe: eine neue Orbis-Welt im Einzelspieler erstellen, sauber beenden und den kompletten Save in den Server-Weltordner kopieren. Vor Änderungen vorhandene Welten sichern.

## Build

```sh
bash gradlew build
python3 tools/verify_standalone.py build/libs/orbis-0.1.0-alpha.7.jar
```

Der Build prüft beide Archive, fehlende/verbotene Debug-Inhalte, die Server-Preset-Codecs und die gespeicherte Dimensionspolitik. Für Entwickler separat: `bash gradlew build -PorbisDebug=true`; Ausgabe unter `build/debug/libs/`.

Siehe [Testprotokoll und echte Minecraft-Screenshots](verification/alpha7/RESULTS.md) für die tatsächlich geprüften Funktionen und Grenzen dieses Alpha-Builds.
