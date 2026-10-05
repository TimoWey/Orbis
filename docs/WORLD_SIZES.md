# Weltgrössen und Biomabdeckung

## Aktueller Stand

Die fünf Grössenwerte sind **wieder offen**. Die frühere Folge 32/64/128/256/512 Chunks ist keine verbindliche Vorgabe mehr. Erde bleibt eine zusätzliche Option.

16 × 16 Chunks ist die dokumentierte **Nether-Mindestgrösse** des Upstreams. Bei festem 1:8 ergibt sich mindestens **128 × 128 Overworld-Chunks = 2048 × 2048 Blöcke**. Das garantiert keine Biomvielfalt.

## Berechnete Kandidaten — keine gemessenen Erfolgsquoten

| Overworld: Chunks je Achse | Overworld: Blöcke je Achse | Nether: Chunks je Achse | Nether: Blöcke je Achse | Verwendung |
| ---: | ---: | ---: | ---: | --- |
| 128 | 2048 | 16 | 256 | Untergrenze / Sehr-klein-Kandidat |
| 256 | 4096 | 32 | 512 | Klein-Kandidat |
| 512 | 8192 | 64 | 1024 | Normal-Messkandidat |
| 1024 | 16384 | 128 | 2048 | Normal-Messkandidat |
| 2048 | 32768 | 256 | 4096 | Normal-Messkandidat |
| 4096 | 65536 | 512 | 8192 | Normal-Messkandidat |

Keine Zuordnung final. Breite ist kein Radius. Nether-Fläche = Overworld-Fläche / 64. Für eine quadratische Welt mit C Chunks je Achse: Gesamtzahl = C².

## Wie wir eine geeignete Normalgrösse bestimmen

- Tatsächlichen Toroidal-/Orbis-Generator verwenden; Vanilla-Seedkarten genügen nicht.
- Minecraft-/Modversion sowie Biomkompression explizit festlegen. Upstream bietet off/auto/strong/custom; die ausgelieferten Presets setzen off.
- Alle natürlich generierenden Biome der gewählten Version erfassen, inklusive Varianten und Höhlen. Nutzbare Mindestfläche noch definieren.
- Zufällige Seeds prüfen; Erfolg nur bei vollständigem Nachweis in **beiden** Dimensionen.
- Grobe Suche beschleunigt die Prüfung, beweist bei fehlendem Treffer aber keine Abwesenheit. Fundorte anhand Terrain/Höhlen bestätigen.
- Neue Welt zusätzlich individuell prüfen; bei bestehender Welt Seed erhalten.

**Noch keine Seed-Simulation durchgeführt.** Wegen seltener, korrelierter Biome lässt sich aus Chunk-Anzahl allein keine seriöse 99-%-Grösse berechnen. Biomkompression verbessert Klimavielfalt, garantiert aber nicht jedes Biom.

## Berechneter statistischer Prüfaufwand

Vorschlag: mindestens 99 % gemeinsame Abdeckung. Für eine vorher festgelegte Konfiguration und eine unabhängige Validierungsstichprobe ohne Fehlschlag gilt die einseitige exakte 95-%-Untergrenze p = 0.05^(1/n).

| Ziel-Untergrenze | Nötige Prüfwelten ohne Fehlschlag |
| ---: | ---: |
| 95 % | 59 |
| 99 % | 299 |
| 99.9 % | 2995 |

Dies ist Versuchsplanung, kein Orbis-Messergebnis und keine Garantie für jede Welt. Konfiguration vor der finalen Stichprobe festlegen; Fehler nicht aus der Stichprobe entfernen. Getrennte 99 % pro Dimension reichen nicht für 99 % gemeinsam.

## Erde

Aus 510 Millionen km² Erdoberfläche inklusive Ozeanen und 1 Block = 1 m:

- Ideal: 1'992'187'500'000 Chunks; quadratische Breite ca. 22'583'179.581 Blöcke.
- Gerundet: **1'411'456 Chunks = 22'583'296 Blöcke je Achse**.
- Overworld gesamt: **1'992'208'039'936 Chunks**.
- Nether: **176'432 Chunks je Achse**.
- Flächenabweichung zur Annahme: ca. +0.001031 %.

Nur Flächengleichheit, keine Erdkarte. Bedarfsgesteuert erzeugen; 64-Bit-Flächenarithmetik und grosse Koordinaten prüfen.

[Rechnung](../analysis/biome_sizing.py) · [Quellen](SOURCES_AND_LICENSE.md)
