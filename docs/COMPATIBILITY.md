# Mod-Kompatibilität

Stand: 2026-10-05. **Noch keine Orbis-Kompatibilität getestet.**

| Mod | Priorität | Upstream / Recherche | Orbis |
| --- | --- | --- | --- |
| Distant Horizons | Pflicht, besonders wichtig | Upstream dokumentiert Unterstützung | Ungetestet |
| Sodium | Pflicht | Upstream dokumentiert Unterstützung | Ungetestet |
| Lithium | Pflicht | Upstream dokumentiert Unterstützung | Ungetestet |
| FerriteCore | Kandidat | Speicheroptimierung; Integration offen | Ungetestet |
| ImmediatelyFast | Kandidat | Renderingoptimierung; Integration offen | Ungetestet |
| ScalableLux | Kandidat | Upstream dokumentiert Unterstützung | Ungetestet |
| Iris | Zusatz-Ziel | Upstream dokumentiert Unterstützung | Ungetestet |
| C2ME | Optional prüfen | Unterstützung gelistet; offener Fehlerbericht #55 | Ungetestet / Risiko |

## Abnahme

- Konkrete Minecraft-, Java-, Fabric- und Mod-Versionen protokollieren.
- Pflichtkombination gemeinsam testen, nicht nur Mods einzeln starten.
- Nähte/Ecken, grosse Sichtweiten, Generierung, Speichern/Neuladen und Konvertierung prüfen.
- DH: LOD über Naht, Detailübergänge, Blockänderungen und Cache nach Konvertierung.
- Kleine Welten: keine doppelte Simulation durch wiederholte Darstellung.
- Leistung: Frametime, Tickzeit, Speicher und Generierungszeit auf dokumentierter Hardware messen.

Upstream nennt für DH eine Reichweite bis zur halben Weltbreite. Kleine Nether-Welten benötigen deshalb eine ausdrückliche Sichtweitenentscheidung. Beliebige Sichtweite ist nicht zugesichert.

[Quellen](SOURCES_AND_LICENSE.md)
