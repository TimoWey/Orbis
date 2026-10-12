# Alpha.7 — lokaler Multiplayer-Test, 2026-10-09

**Ergebnis:** Ein dedizierter Fabric-Server läuft mit Orbis. Ein normaler Orbis-Client und ein unveränderter Vanilla-26.3-Client konnten gleichzeitig beitreten. Die normale Orbis-Minimap funktioniert im Multiplayer. Orbis ist lokal für diese Minimap erforderlich, nicht für den bloßen Beitritt zu diesem Server ohne weitere Mods.

## Getesteter Stand

- Ausgangspunkt: `main` (`4b22ede`), auf einem neuen Branch per Fast-forward bis `codex/orbis-alpha3-atlas` (`cf9e178`) übernommen.
- Implementierung: `914d492bf52cf9938a129f7116f5e8dd8f61e1ed`, Branch `codex/orbis-server-release`; kein Merge auf `main`.
- Minecraft 26.3; Temurin Java 25.0.4.1; Fabric Loader 0.19.5; Fabric API 0.161.0+26.3; Orbis 0.1.0-alpha.7, normal.
- Debian 13, vier CPU-Kerne, Xvfb 1280 × 800, Mesa llvmpipe 25.0.7 mit `SDL_VIDEO_FORCE_EGL=1`. Echte Minecraft-Fenster und offizielle Client-/Serverdateien, keine nachgebauten UI-Bilder.
- Server: Seed `8675309`, `level-type=orbis:tiny`, Sichtweite 6, Simulationsdistanz 4, 3 GB maximaler Heap. Clients: Sichtweite 6, GUI-Skalierung 2.
- Ausschließlich `127.0.0.1:25565`, Offline-Modus für lokale Testidentitäten `OrbisTester` und `VanillaTester`. Online-Authentifizierung/öffentliches Hosting wurde nicht getestet; die Installationsanleitung verwendet `online-mode=true`.

## Automatische Prüfungen

Normales Gradle/Loom-Buildverfahren, keine Ersatz-Compilerpipeline:

```sh
bash gradlew build -PorbisCommit=914d492bf52cf9938a129f7116f5e8dd8f61e1ed
bash gradlew build -PorbisDebug=true -PorbisCommit=914d492bf52cf9938a129f7116f5e8dd8f61e1ed
python3 tools/verify_standalone.py build/libs/orbis-0.1.0-alpha.7.jar
python3 tools/verify_standalone.py --debug build/debug/libs/orbis-0.1.0-alpha.7-debug.jar
```

- Normal: **38 bestanden**, keine Fehler/Skips.
- Debug: **116 bestanden**, keine Fehler, drei optionale Benchmarks übersprungen (119 erfasste Tests).
- `verifyPresets`, `verifyDistribution`, die separaten Python-Verifikationen und `git diff --check` erfolgreich.
- Alle fünf Server-Presets mit echten Minecraft-Registry-Codecs dekodiert und nach erneutem Serialisieren geprüft: Orbis-Identität, Overworld-/Nether-Größe und unbeschränktes End.
- Normale JAR enthält `PlayerMinimap`, aber keine `dev/orbis/debug/`-Klassen, Debug-Ressourcen oder Debug-Entrypoints. Debug-Artefakt bleibt separat und registriert seine bisherige M-Taste/Atlas-Anzeige.

Auch die [GitHub-Actions-Matrix für denselben Code-Commit](https://github.com/TimoWey/Orbis/actions/runs/37930417665) hat beide Varianten erfolgreich gebaut und JARs/Testberichte als Artefakte veröffentlicht.

## Live-Prüfungen

| Prüfung | Beobachtung |
| --- | --- |
| Neue Server-Welt | Overworld TORUS, 1.024 Chunks / 16.384 Blöcke breit; Nether TORUS, 128 Chunks / 2.048 Blöcke; End `not wrapped` |
| Speichern, Serverneustart, erneuter Beitritt | Weltgrößen und Spielerpositionen bleiben erhalten; `/orbis` erkennt weiterhin die Welt |
| Zwei Clients gleichzeitig | `list` meldet `VanillaTester, OrbisTester`; Vanilla ohne Fabric/Orbis erfolgreich |
| Einfache Minimap | Oberwelt, Nether-Schnitt Y=32 und End sichtbar; Position/Richtungsmarker und Koordinaten vorhanden |
| M-Taste | Blendet nur die Minimap um, öffnet keinen Debug-Atlas |
| F1 | Versteckt auch die Orbis-Minimap zusammen mit dem restlichen HUD |
| Dimensionswechsel | Karte wird für die neue Dimension neu aufgebaut; kein alter Terrain-Ausschnitt bleibt stehen |
| Vanilla-Texte | Nach der im Test gefundenen Korrektur zeigt der Vanilla-Client den lesbaren englischen Orbis-Hinweis und `/orbis`-Text; modifizierter Client erhält deutsche Texte |
| Echtes Netherportal | Ein im Test errichtetes Portal bei Overworld X/Z=100,5/100,5 führt ohne Dimensions-Teleportbefehl in den Nether zu X/Z=12,5/12,5 (Ziel-Y=97); nach Verlassen und erneutem Betreten führt dasselbe Portal zurück nach Overworld 100,5/81/100,5. Exakte 1:8-Abbildung |
| End | Position `[8200.5, 100, 8200.5]` bleibt über mehrere Ticks unverändert, statt an den Overworld-Grenzen zu falten |

Für die folgenden Kantenprüfungen wurde im Zuschauermodus nahe an die positive Grenze teleportiert und anschließend mit **W** tatsächlich über die Kante geflogen. Die Werte stammen aus `data get entity OrbisTester Pos` auf dem Server. Die Client-Ansicht darf kontinuierliche, über die Grenze hinausgehende Koordinaten anzeigen; die Engine übersetzt diese auf die kanonischen Serverkoordinaten.

| Dimension / Richtung | Start X / Z | Serverposition nach Bewegung X / Z, gerundet |
| --- | ---: | ---: |
| Overworld X | 8190,5 / 0,5 | −8166,05 / 0,5 |
| Overworld Z | 0,5 / 8190,5 | 0,5 / −8168,94 |
| Overworld Ecke | 8190,5 / 8190,5 | −8173,71 / −8173,71 |
| Nether X | 1022,5 / 0,5 | −995,75 / 0,5 |
| Nether Z | 0,5 / 1022,5 | 0,5 / −995,88 |
| Nether Ecke | 1022,5 / 1022,5 | −1014,37 / −1014,37 |

Die Kantenprüfungen liefen mit SHA-256 `69a7a89c4b6e70e689120bcdb54fcf4967fe408643ef5362461d40e970204ba6`. Danach wurden ausschließlich englische Ersatztexte für Vanilla-Clients ergänzt; Engine und Minimap blieben gleich. Die finale JAR wurde erneut gebaut, auf Server und Client installiert und für erneuten Serverstart, beide Beitritte, Textprüfung und die finalen Oberwelt-/Nether-Aufnahmen verwendet.

## Screenshots

Unveränderte Bildschirmaufnahmen der tatsächlich laufenden Clients. Für die helle Nether-Aufnahme wurde im Spiel Nachtsicht aktiviert. Der Zuschauermodus dient der Kamerapositionierung; er ist eine normale Minecraft-Funktion und kein Orbis-Debug-Feature.

- [Orbis-Client mit Minimap in der Oberwelt](minimap-overworld.png)
- [Minimap im Nether, Gelände-Schnitt Y=32](minimap-nether.png)
- [Minimap im End](minimap-end.png)
- [M: Minimap ausgeblendet](minimap-off.png)
- [F1: HUD ausgeblendet](hud-hidden.png)
- [Vanilla-Client auf demselben Server, ohne Minimap](vanilla-client.png)
- [Auszüge aus dem Server-Testprotokoll](server-evidence.txt)

## Finale Datei

`orbis-0.1.0-alpha.7.jar`

```text
SHA-256: 01e58b56731622d381506e90e7d850fba51b4f7ea2307701024ebb0c4d361229
Source-SHA-256: 0886e1ea02b8c6cefce8e0999672ad81a58eedc0341c74111831bd2bdbfbaf7d
```

Dieser Datei-Hash wurde für Build-Ausgabe, lokalen Server und lokalen Orbis-Client identisch bestätigt. Das Windows-Modrinth-Profil des Nutzers ist in dieser Cloud-Umgebung nicht verfügbar; installiert wurde ausschließlich in die hier angelegten Testprofile.

## Grenzen

Dies ist ein funktionaler Alpha-Test, kein Langzeit-/Lasttest. Bei gleichzeitiger Weltgenerierung, zwei Software-Renderern und Buildprozessen erschienen `Can't keep up`-Meldungen und einmal `moved too quickly`; der Client blieb verbunden und die gemessene Position lag auf der gegenüberliegenden Seite. Daraus lässt sich keine belastbare Spielerzahl oder Tickrate für einen echten Server ableiten.

Nicht abgedeckt: Langzeitbetrieb, kombinierter Einsatz mit Sodium/Lithium/Distant Horizons oder anderen Mods, alle Entitäts-/Redstone-Situationen an Nähten, End-Gateways, vollständige Struktur-/Biomverfügbarkeit, große Presets im Livebetrieb und bestehende Weltkonvertierung. Dimensionswechsel per Teleport belegen allein keine korrekte Portalplatzierung. Microsoft-/Realms-Dienste und Audio waren in der Testumgebung nicht verfügbar; lokale Multiplayer-Verbindungen und Rendering funktionierten.
