# Alpha.11 — Untersuchung und schnellerer erster Kartenaufbau

Geprüft am 9. Oktober 2026. Die [ausführliche Analyse](ANALYSIS.md) vergleicht Orbis mit dem öffentlich einsehbaren VoxelMap-Code anhand fest verlinkter Quellstellen. Es wurde kein fremder Code übernommen.

## Ergebnis

VoxelMap hält den laufenden Kartenaufbau klein: sichtbarer Kartenausschnitt, Änderungen einzelner Chunks, Wiederverwendung vorhandener Bilddaten und getrennte Speicherung. Bei Orbis wurden vier konkrete Probleme behoben:

- Erste Erfassung und regelmäßige Auffrischung konkurrierten in derselben Warteschlange. Im langsamen Testszenario blieb Alpha.10 bei 200 von 1.089 Chunks hängen. Alpha.11 erfasst alle, reserviert aber weiter einen Anteil für Bauänderungen. Noch nicht verfügbare Chunks werden erneut versucht.
- Gespeicherte Chunks wurden vollständig einschließlich unbenötigter Biome, Lichtdaten und unterirdischer Abschnitte dekodiert. Der neue Leser nutzt Oberflächenhöhen und dekodiert die benötigten Blockpaletten erst bei Zugriff. Im Nether bleibt es beim bisherigen Y=32-Schnitt.
- Aktuelle lokale Farben konnten von älteren Archivfarben überdeckt werden. Live-Daten haben jetzt auch im normalen Einzelspieler-Atlas Vorrang.
- Unveränderte Live-Kacheln wurden erneut zur Speicherung geschickt. Das passiert jetzt nur bei neuen oder geänderten Farben.

Das Besitzer-Thread-Budget bleibt bei maximal 32 Erfassungen und einem weichen 2-ms-Limit pro Tick. Es werden keine zusätzlichen Welt-Chunks angefordert oder generiert.

## Messungen

**Dekodierung:** 512 echte Overworld- und 512 echte Nether-Chunks wurden gegen den Alpha.10-Leser geprüft: **262.144 identische Farbwerte**. Median der drei Messrunden nach Aufwärmen:

| Nur Dekodierung und Farbentnahme, jeweils 512 Chunks | Alpha.10 | Alpha.11 | Verhältnis der Medianzeiten |
| --- | ---: | ---: | ---: |
| Overworld | 392,89 ms | 41,34 ms | 9,5× |
| Nether | 111,27 ms | 10,44 ms | 10,7× |

Die [Rohmessungen](saved-surface-benchmark.xml) enthalten alle Runden. Datei-I/O, Archivschreiben und Darstellung gehören nicht zu diesen isolierten Zeiten. Alle Farbwerte wurden vor den Messrunden verglichen; nicht nur der zusätzlich ausgegebene Prüfsummenwert.

**Minecraft-Kaltstart:** Normale Builds, dieselbe lokale Testwelt und unveränderte Position (-13,5 / 87 / 15,5), Sichtweite sechs Chunks, jeweils leeres Orbis-Archiv. Keine gleichzeitigen Gradle-Builds. Minecraft 26.3, Java 25, Loader 0.19.5, Fabric API 0.161.0+26.3, Mesa/llvmpipe und 10-FPS-Limit. Die Welt enthält 1.246 für diesen Vergleich erfasste Overworld-Kacheln; die Weltbreite beträgt 16.384 Blöcke.

| Erstmals beobachtete gültige Archivkacheln | Alpha.10 | Alpha.11 |
| --- | ---: | ---: |
| mindestens 512 | 17,951 s | 8,436 s |
| mindestens 1.000 | 28,354 s | 18,330 s |
| alle 1.246 | 31,255 s | 23,328 s |

Damit war der vollständige Archivaufbau in diesem Lauf ungefähr **25 % kürzer**. Der Spielerbereich ist früher sichtbar als das komplette Archiv. Gemessen werden vorhandene Kacheldatensätze, keine FPS und kein Zeitpunkt der GPU-Präsentation. Der Zähler prüft die Gültigkeitsmarkierung der Archivslots; die CRC-/Pixelkorrektheit wird getrennt automatisiert getestet.

[Vergleichsdaten](fill-comparison.json), [Alpha.10-Zeitreihe](alpha10-cold-fill.json), [Alpha.11-Zeitreihe](alpha11-cold-fill.json), [Messskript](measure_cold.py). Nominal wird alle 250 ms gezählt; die Screenshot-Aufnahme unterbricht die Zählung teilweise für mehrere Sekunden. Schwellenwerte sind deshalb beobachtete obere Zeitgrenzen. Dateinamen der Screenshots bezeichnen geplante Aufnahmepunkte, keine garantierten Millisekunden. Software-Rendering und zeitweise Server-Tick-Verzögerungen begrenzen die Übertragbarkeit; es ist je ein Vergleichslauf, keine statistische Lastmessung.

## Build und Tests

Normaler Spieler-Build: `orbis-0.1.0-alpha.11.jar`.

```
Commit 102af17afb95e55435865d7dff08ae27b41b3537
SHA256 d19e9025c54ccb83bb45f733f67aff4ecbc1d60119735ff92ba4b6923acba208
Source 5f123dd137ce4ea40a7a796add8c8a969534ece3d620be5026d246fe3e15867c
```

Normal: 144 bestandene Tests einschließlich des aktivierten Realwelt-Decodiervergleichs, drei optionale Benchmarks übersprungen, zusätzlich drei DH-Tests bestanden. Debug: 157 bestanden, sechs optionale Tests/Benchmarks übersprungen, zusätzlich drei DH-Tests bestanden. Beide vollständigen Gradle-Builds sowie die unabhängigen JAR-/Sources-/Provenienzprüfungen sind erfolgreich. [Build-Ergebnisse](build-results.json).

Die Regressionstests prüfen unter anderem langsame Erstaufnahme, fehlende Chunks, fortlaufende Aktualisierung während der Erstaufnahme, unveränderliche Snapshots, unterdrückte redundante Schreibvorgänge, negative Höhen, transparente Blöcke, fehlende/fehlerhafte Heightmaps, den Nether-Schnitt und Vorrang von Live-Farben in Karte und Globus. Der ursprüngliche Fehlschlag ist ausdrücklich als [Vorher-Test](queue-before.xml) archiviert; der [Nachher-Test](queue-after.xml) ist erfolgreich.

## Tatsächlich im Spiel geprüft

Der normale finale Build wurde mit SHA256-Abgleich in das isolierte Minecraft-Testprofil kopiert. Alte JARs und Kartenarchive liegen separat in Sicherungsverzeichnissen. Das Windows-/Modrinth-Profil des Nutzers ist hier nicht eingebunden; der versionierte Build wird als Datei bereitgestellt.

- [Karte vor der Bauänderung](atlas-before-edit.png) und [danach](atlas-after-edit.png): Eine 7×7-Fläche aus rotem Beton wurde in der Testwelt auf Y=200 platziert. Ohne erneutes Einloggen erscheinen **144 neue rote Bildschirmpixel** im erwarteten Kartenausschnitt, RGB (153,51,51), bei (648..659, 403..414). [Pixelauswertung](construction-pixels.json). Die Testkopie hatte zunächst Befehle deaktiviert; für diesen Test wurden sie nach sauberem Verlassen der Welt in deren eigener `level.dat` aktiviert.
- [Globus zentriert](globe-centered.png), [nach Ziehen](globe-dragged.png) und [erneut zentriert](globe-recentered.png): Die Rotation verändert 110.472 Pixel im Kartenausschnitt. Nach Zentrieren sind alle Pixel im 576×576-Kartenausschnitt wieder identisch zur Ausgangslage, einschließlich des eigenen Markers. [Pixelauswertung](globe-pixels.json). Die einfache Oberfläche ohne Plus-/Minusknöpfe ist vorhanden.
- [Minimap nach der Bauänderung](minimap-after-edit.png): Der HUD zeigt weiterhin aktuelle Geländefarben.
- Erneuter Start mit vorhandenem Archiv: Bereits am ersten Messpunkt sind alle 1.246 Kacheln vorhanden. Das betrifft den Cache; Minecraft zeigt währenddessen noch seinen eigenen Ladebildschirm. [Warmstart-Zeitreihe](alpha11-warm-fill.json), [späterer HUD-Screenshot](alpha11-warm-30s.png). Daraus wird keine Ladezeit für den gesamten Minecraft-Start abgeleitet.

Die Testläufe meldeten unter Software-Rendering zeitweise Tick-Verzögerungen sowie nicht erreichbare Auth-/Realms-Dienste und fehlendes Audio. Es wurde kein Fehler des neuen Gelände-Importers beobachtet. Der Testclient wurde anschließend über die Menüs sauber beendet.

## Grenzen

Die exakte minutenlange Situation aus der Nutzerwelt ist hier nicht reproduziert; Welt/Modpack und Hardware liegen nicht vor. Der Vergleich belegt die behobene Warteschlangen-Starvation, identische Farben und einen schnelleren Import in dieser Testwelt. Er belegt keinen zehnfach schnelleren gesamten Kartenaufbau.

Weitere Kosten bleiben insbesondere Minecraft-Datei-I/O, einzelne Archivzugriffe, Chunk-Verfügbarkeit und die globale Kartenprojektion. Die Versionsprüfung des persistenten Caches bleibt unverändert: Nach einem Versions-/Modpackwechsel kann einmalig ein Neuaufbau nötig sein. Auf fremden Servern stehen nur übertragene Gelände-Chunks zur Verfügung; das lokale Save-Import-Verfahren gilt dort nicht.

Admin- und Mehrspielerfunktionen wurden in dieser Änderung nicht erweitert; deren tatsächliche Serverprüfungen stehen in [Alpha.10](../alpha10/RESULTS.md) und den dort verlinkten früheren Protokollen. Diese Tests werden hier nicht als erneut ausgeführter Server-Lasttest ausgegeben.
