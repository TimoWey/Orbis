# Orbis

Endliche Minecraft-Welten mit nahtlosen Übergängen.

**Status: Planung. Noch kein spielbarer Mod, kein Upstream-Code importiert, keine Spieltests ausgeführt.**

## So funktioniert Orbis

![Animation: Quadrat wird zum Torus; der Spieler läuft zweimal in X-Richtung zum Start zurück.](docs/media/orbis-torus.gif)

**Quadrat → Zylinder → Torus.** Gegenüberliegende Kanten sind direkte Nachbarn. Wer immer in +X läuft, erreicht nach einer Weltbreite wieder denselben Ort; in Z gilt dasselbe. Es entstehen keine neuen Kopien der Welt.

Der Torus veranschaulicht die Verbindungen. **Im Spiel bleiben Gelände und Schwerkraft normal; Y/Höhe wird nicht umgebrochen.** Die Animation ist ein Erklärmodell, keine Spielaufnahme.

[Video zum Abspielen/Pausieren](docs/media/orbis-torus.mp4) · [Animation und Renderer](docs/media/README.md)

| Festgelegt | Ziel |
| --- | --- |
| Grundlage | Toroidal World als eigenständige Weiterentwicklung |
| Plattform | Fabric, neueste stabile Minecraft-Java-Version zum Implementierungsstart |
| Spielmodus | Singleplayer zuerst |
| Welt | Overworld und Nether periodisch entlang X/Z |
| Nether | Exakt 1:8 zur Overworld |
| End | Vanilla-Verhalten ohne Orbis-Begrenzung |
| Pflichtmods | Distant Horizons, Sodium, Lithium |
| Bestehende Welten | Konvertierung auf einer Kopie mit angepassten Randstreifen |
| Neue Welten | Biomprüfung mit verständlichem Ergebnis/Warnhinweis |
| Grössen | Fünf Stufen plus Erde; fünf konkrete Grössen noch offen |

## Dokumentation

- [Requirements](docs/REQUIREMENTS.md)
- [Features](docs/FEATURES.md)
- [Mod-Kompatibilität](docs/COMPATIBILITY.md)
- [Grössen und Biomprüfung](docs/WORLD_SIZES.md)
- [Konvertierung](docs/CONVERSION.md)
- [Architektur](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
- [Entscheidungen](docs/DECISIONS.md)
- [Quellen und Lizenz](docs/SOURCES_AND_LICENSE.md)
- [Preset-Entwurf](spec/world-presets.json)

Stand: 2026-10-05. „Geplant“ und „Upstream dokumentiert“ bedeuten nicht „in Orbis getestet“.

