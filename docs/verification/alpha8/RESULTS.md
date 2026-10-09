# Alpha.8 — aktueller Atlas, Multiplayer und Admin-Checks

Getestet am 9. Oktober 2026 mit echten Minecraft-26.3-Clients und einem dedizierten Fabric-Server. Alle PNGs sind unveränderte Aufnahmen der laufenden Minecraft-Fenster.

## Ausgangspunkt und Build

Der gesuchte Commit ist **`7a8f3e3af190f9313a39a0c50740c218a40c9178`**, „Speed up minimap scrolling and simplify unexplored maps“, vom 8. Oktober 2026, 20:18:39 +02:00, auf **`codex/orbis-alpha3-atlas`**. Gegenüber dem zuvor verwendeten `cf9e178` fehlte außerdem `5a97628`, „Speed up atlas terrain caching and improve map navigation“.

Diese zwei Commits bringen Originalfarben in einem persistenten Gelände-Archiv für den lokalen Entwickler-Atlas, optionale Save-/Distant-Horizons-Imports, asynchrone Karten-Hintergründe, eine gepufferte Minimap-Textur mit GPU-Scrolling, einen verbesserten Richtungsmarker, zentrierte Navigation und vereinfachtes symbolisches unbekanntes Gelände. Wegpunkte wurden entfernt. Die neuen Rendering-Helfer werden jetzt zwischen normalem und Debug-Build geteilt; der normale Multiplayer-Client sammelt ausschließlich empfangenes Gelände. Vollständiger Save-/DH-Import bleibt bei der Entwickleroberfläche.

Implementierung: **`4c81ffc104d2805a612a22e8c1301d26e0b561ff`**, Branch `codex/orbis-server-release`, [Draft-PR #3](https://github.com/TimoWey/Orbis/pull/3). Aktueller Atlas-Branch wurde in diesen Review-Branch gemergt; `main` wurde nicht verändert.

Normales JAR: `orbis-0.1.0-alpha.8.jar`.

```
SHA256 69d96ca14cbb533934004e6bc0557c3ca59692f28157721243f9f3d81b41a5fd
Source c428c4106f68df4aad46102cce029d2064f9ff15cacfd3b3776427e5eca0b4b9
```

Exakt dieses finale JAR wurde in Client und Server installiert; Dateihashes wurden verglichen. Alte JARs liegen außerhalb von `mods` im Backup. Das Windows-Modrinth-Profil des Nutzers ist in dieser Linux-Umgebung nicht erreichbar; installiert wurde in den lokalen Testprofilen.

## Automatisierte Prüfungen

- Normal: **92 bestanden**, zwei optionale Benchmarks übersprungen.
- Debug: **143 bestanden**, fünf optionale Benchmarks übersprungen.
- Separate Distant-Horizons-Kompatibilitätssuite: **3 bestanden**.
- Normal/Debug: `gradlew build`, `verifyDistribution`, `verifyPresets`, separate Python-Verifikation der jeweiligen JAR/Sources erfolgreich.
- Regressionen umfassen Kartenprojektion, Spielerposition, Renderer-Cache, Biome-Suche, Strukturtraversierung und gespeicherte Einstellungen einschließlich Zoom-Neustart und fehlerhafter Werte.
- Beide [GitHub-Actions-Varianten](https://github.com/TimoWey/Orbis/actions/runs/37934110771) für den Implementierungs-Commit erfolgreich.
- Normales JAR enthält weder `DeveloperClient`, `DeveloperScreen`, `DeveloperSurvey` noch Entwickler-Ressourcen/Entrypoint. Gemeinsame Renderer und der autorisierte Server-Check sind reguläre Produktionsklassen.

Zahlen und Provenienz: [build-results.json](build-results.json).

## Tatsächliche Server- und GUI-Tests

Umgebung: Debian 13, Java 25.0.4.1, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, 4 CPU-Kerne, Xvfb 1280×800, GUI-Skalierung 2, Mesa llvmpipe (Software-Rendering). Server: Tiny, Seed `8675309`, 3 GB Heap, Sichtweite 6, Simulation 4. Nur Loopback `127.0.0.1:25565`; Offline-Testidentitäten, keine Prüfung öffentlicher Authentifizierung.

| Prüfung | Beobachtung |
| --- | --- |
| Server und Client neu gestartet | Gespeicherte Welt korrekt geladen, Orbis-Client tritt bei |
| Vanilla-Client ohne Fabric/Orbis | Gleichzeitig verbunden; `list` nennt beide Testspieler |
| Nicht-Operator `/orbis check` | Befehl wird abgewiesen; kein Job startet |
| Operator ruft `/orbis check` im Chat auf | Startmeldung, Hintergrundarbeit, Ergebnis im Chat und Serverlog |
| Zweiter Check während laufendem Job | Abgewiesen, Hinweis auf Status/Abbruch |
| `/orbis check status` | Fortschritt während der Arbeit, vollständiges Ergebnis danach |
| `/orbis check structures` + `cancel` | Start und sauberer Abbruch; danach kein aktiver Job |
| `/orbis check biomes` | Separate Kategorie gestartet und für beide Dimensionen abgeschlossen |
| Atlas/Globus | Mittige Darstellung in Oberwelt/Nether; End-Karte, Globus im End deaktiviert |
| Zoom-Speicherung | Kartenbreite von 512 auf 327,68 Blöcke geändert; nach Schließen/Öffnen und vollständigem Prozessneustart unverändert |
| Minimap-Einstellungen | Größe L und Zoom 2× nach Prozessneustart unverändert; Konfigurationsdatei bytegleich zur Datei vor Neustart |
| Weitere Einstellungen | Karte/Globus je Dimension getrennt, Ecke umgestellt, Minimap ein/aus, F1 blendet Minimap aus |

Der finale Gesamtlauf ging von **13:05:12 bis 13:07:14 UTC**. Ergebnis für diese konkrete Testwelt:

| Dimension | Biome gefunden | Strukturziele gefunden |
| --- | ---: | ---: |
| Oberwelt | **56/56** | **43/48** |
| Nether | **5/5** | **4/4** |

Die Strukturziele enthalten in der Oberwelt zusätzlich den Stronghold-Portalraum. Unbestätigt blieben `minecraft:abandoned_camp_cherry_grove`, `minecraft:abandoned_camp_pale_garden`, `minecraft:abandoned_camp_windswept_forest`, `minecraft:buried_treasure`, `minecraft:mineshaft_mesa`. **Das ist kein Beweis ihrer Abwesenheit.** Strukturen werden anhand des Generators vorhergesagt, nicht als tatsächlich noch vorhandene Blöcke verifiziert. Der erste Testlauf vor den letzten rein visuellen Änderungen lieferte dasselbe Ergebnis.

## Screenshots und Grenzen

- [Atlas nach vollständigem Neustart, gespeicherter Zoom](atlas-zoom-after-restart.png)
- [Mittiger Globus](globe-overworld.png)
- [Admin-Check im Chat](admin-check-result.png)
- [Nicht-Operator abgewiesen](non-operator-denied.png)
- [Nether-Karte](atlas-nether.png), [Nether-Globus](globe-nether.png), [End-Karte](atlas-end.png)
- [Minimap oben links](minimap-top-left.png), [Minimap ausgeschaltet](minimap-off.png), [F1](hud-hidden.png)
- [Vanilla-Client](vanilla-client.png)

[Finaler Serverauszug](server-final-pass.txt), [erster Serverauszug](server-first-pass.txt). Die Ablehnung ohne Operator wurde im ersten Lauf aufgenommen; die Berechtigungslogik blieb zum finalen JAR unverändert.

Dies sind Funktionsprüfungen, keine Last-/Langzeitfreigabe. Während der Tests mit zwei Software-Renderern traten „Can't keep up“-Meldungen auf, auch beim Start/Dimensionswechsel. Online-Authentifizierung, größere Spielerzahlen und zusätzliche Modpacks wurden nicht geprüft. Die Wrap-/Portal-Regressionen aus [Alpha.7](../alpha7/RESULTS.md) wurden nicht vollständig wiederholt; die aktuelle Prüfung bestätigt erneut die Dimensionsgrößen und Dimensionswechsel.

Spieler-Geländeverlauf bleibt auf 8.192 Chunks je Dimension innerhalb der Verbindung begrenzt; er wird beim Disconnect gelöscht. **Einstellungen bleiben gespeichert.** Der unbekannte Globus-Hintergrund ist symbolisch und zeigt keine gescannten Biome. Installation, Befehle und genaue Suchbudgets: [Server-Anleitung](../../SERVER.md).
