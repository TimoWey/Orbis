# Alpha.9 — Karten-Ladepfad wiederhergestellt

Geprüft am 9. Oktober 2026. Normaler Spieler-Build: `orbis-0.1.0-alpha.9.jar`, Implementierungsstand `56572bdde5ad0cfc73dc95f3bbe25b4be7a1b72b`.

```
SHA256 f8814e6ab95e3a23c469bd783885748bad4b2f379c9546e56d66797d4bddf926
Source 8287545e51e25db7ba96183f0b4cb821ecfdb931b405e20643428be372831f79
```

## Ursache und Änderung

Verglichen wurde mit `7a8f3e3af190f9313a39a0c50740c218a40c9178` auf `codex/orbis-alpha3-atlas` — „Speed up minimap scrolling and simplify unexplored maps“. Der normale Alpha.8-Adapter erfasste nur vier Chunk-Koordinaten pro Tick in Zeilenreihenfolge, mit Radiuslimit 16; auch fehlende Chunks verbrauchten diese Slots. Außerdem fehlten dort der persistente Gelände-Cache und die asynchronen Importe gespeicherter Welt-/Distant-Horizons-Daten. Das war eine Regression gegenüber dem Entwickler-Atlas.

Alpha.9 priorisiert tatsächlich geladene Chunks nach Spielernähe, bis zu 32 pro Tick mit einem weichen 2-ms-Budget. Unveränderte Gelände-Snapshots werden wiederverwendet. Im Einzelspieler sind persistentes Archiv und Hintergrundimport zurück, ohne automatische Entwickler-Surveys. Auf entfernten Servern werden nur bereits empfangene Chunks erfasst; dort bleibt die Gelände-Historie verbindungsbezogen und auf 8.192 Chunks pro Dimension begrenzt. Es gibt keinen Zugriff auf den Server-Save oder einen übertragenen Welt-Seed.

Sieben zentrale Renderer-/Import-Helfer stimmen nach Paket-/Typumbenennungen mit dem Referenzcommit überein: [Vergleich](upstream-comparison.json). Die einfache Spieleroberfläche bleibt erhalten: keine Plus-/Minus-Buttons, Mausrad-Zoom, gespeicherte Einstellungen und zwei Grautöne für den symbolischen unbekannten Hintergrund. Erkundetes Gelände behält seine Farben.

## Automatisierte Prüfung

Normaler Build: 132 Tests bestanden, drei optionale Benchmarks übersprungen; zusätzliche Distant-Horizons-Suite: drei bestanden. Debug-Build: 146 bestanden, fünf übersprungen; zusätzliche DH-Suite: drei bestanden. Beide Gradle-Builds inklusive Distribution-/Preset-Prüfungen sowie getrennte Python-JAR-/Sources-Provenienzprüfungen erfolgreich. [Ergebnisse](build-results.json).

Ein deterministischer Warteschlangen-Test mit einem bereits geladenen 33×33-Chunk-Fenster zeigt: Der Spieler-Chunk kommt im alten Zeilenscan bei Tick 137, mit der neuen Queue bei Tick 1. Das zentrale 9×9-Fenster ist bei Tick 171 beziehungsweise Tick 4 fertig. Weitere Fälle prüfen Zeitbudget, Neusortierung nach Teleport, Unload-Erfassung und Snapshot-Wiederverwendung. Das sind modellierte Erfassungs-Ticks, **keine FPS-Messung oder Garantie identischer Ladezeiten auf anderer Hardware**.

## Echter Minecraft-Client

Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, normales Alpha.9-JAR ohne Distant Horizons. Die Prüfsummen der installierten Client-/Server-JARs stimmen mit dem Download überein. Testwelt: Tiny, Seed 8675309. Der Client läuft mit Mesa-Software-Rendering in einer virtuellen Anzeige; diese Umgebung eignet sich zur Funktionsprüfung, nicht für einen repräsentativen FPS-Vergleich.

Für Einzelspieler wurde eine Kopie des bestehenden Server-Saves verwendet. Der erste Lauf importierte gespeichertes Gelände in 26 Archivdateien. Beim erneuten Start mit dem finalen JAR wurde derselbe Cache geöffnet: alle 26 Dateien blieben erhalten, 24 unverändert; zwei wurden durch aktuelle Gelände-Erfassung aktualisiert. [Cache-Nachweis](cache-reuse.json). Das Log meldet den Cache eine Sekunde nach dem Spieler-Login als bereit. Der Atlas zeigt anschließend das bekannte Gelände: [Screenshot nach Neustart](local-warm-join.png). Das ist ein Funktionsnachweis und keine Messung der Zeit bis zum fertigen Kartenbild.

Beim ersten Testlauf wurde zusätzlich ein Fehler beim Schließen von Karten-Texturen im Disconnect-Callback entdeckt. Der finale Build verschiebt die Bereinigung auf den Clientthread. Das Verlassen der lokalen Welt wurde damit ohne den vorherigen Renderthread-Fehler geprüft.

Anschließend trat derselbe Client einem separaten Fabric-Server auf `127.0.0.1:25565` bei, ebenfalls mit dem finalen normalen Alpha.9-JAR. Die Karte füllte sich aus empfangenen Chunks; Atlas, Globus und HUD wurden im Spiel geöffnet und geprüft: [Server-Atlas](server-atlas.png), [zweifarbiger Globus](server-globe.png), [Minimap](server-minimap.png). Es wurde keine lokale Einzelspieler-Karte auf den Server übertragen. Der Testserver verwendet nur für diesen Loopback-Test Offline-Authentifizierung.

Client und Server wurden anschließend sauber über ihre Menüs beziehungsweise `stop` beendet. Auch das Trennen vom dedizierten Server erzeugte keinen Renderthread-Fehler. Relevante Logauszüge: [Client](client-lifecycle.txt), [Server](server-lifecycle.txt).

Die Umgebung protokolliert nicht verfügbare Auth-/Realms-Dienste, fehlendes Audio, eine Minecraft-Meldung zu einem leeren Pfad beim Laden des kopierten Saves und zeitweise Tick-Verzögerungen unter Software-Rendering. Diese Einschränkungen werden nicht als bestandener Lasttest gewertet.

Die vollständigen Admin-Biome-/Strukturprüfungen, Vanilla-Beitritt und UI-Einstellungen wurden bereits in [Alpha.8](../alpha8/RESULTS.md) geprüft. Für diesen Ladepfad-Fix wird daraus keine erneute vollständige Weltprüfung behauptet.

Ein Versions-/Modpackwechsel kann den alten Cache absichtlich invalidieren. Der erste Start von Alpha.9 kann daher erneut gespeichertes Gelände importieren; weitere Starts derselben Konfiguration verwenden das neue Archiv. Die Windows-Modrinth-Installation ist in diesem Linux-Workspace nicht erreichbar. Der Nutzer erhält die versionierte normale JAR direkt; alte Orbis-/Debug-JARs vorher aus `mods` sichern und entfernen.
