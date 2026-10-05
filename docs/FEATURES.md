# Features

Alle Features sind **geplant**, noch nicht implementiert.

| Feature | Verhalten |
| --- | --- |
| Nahtlose Welt | Weiterlaufen/-fliegen führt auf die andere Seite; gleiche Weltinhalte |
| Grössenauswahl | Sehr klein, Klein, Mittel/Normal, Gross, Sehr gross, Erde |
| Biomprüfung | Overworld und Nether vor erstem Spielstart untersuchen |
| Biombericht | Gefundene, fehlende und unbestätigte Biome pro Dimension anzeigen |
| Bestehende Welt übernehmen | Vorschau, Schutzgebiete, neue Weltkopie, angepasste Randstreifen |
| Performance-Mods | Distant Horizons, Sodium und Lithium als Pflichtkombination |
| Unverändertes End | Gewohnte Erkundung, Endkampf, Portale und Gateways |

## Biomprüfung: vorgeschlagener Ablauf

1. Grösse und Seed wählen; Generatorparameter stehen fest.
2. „Biome werden geprüft …“ mit Fortschritt und Abbruchmöglichkeit.
3. Ergebnis pro Dimension anzeigen:

| Status | Meldung |
| --- | --- |
| Vollständig nachgewiesen | „Alle erforderlichen Biome wurden gefunden.“ |
| Nachweislich unvollständig | „In dieser Welt fehlen folgende Biome: …“ |
| Unvollständige Prüfung | „Die Prüfung konnte nicht alle Biome bestätigen: …“ |

Bei unvollständiger Abdeckung: **Trotzdem starten**, **Grösse ändern**, bei neuen Welten zusätzlich **anderen Seed prüfen**. Fehlende Rastertreffer allein dürfen nicht als bewiesene Abwesenheit erscheinen.

Vorgeschlagener Hinweis: „Diese Welt ist begrenzt. Einige Biome und Strukturen können fehlen. Du kannst trotzdem spielen oder die Einstellungen ändern.“

Normal soll vollständige Abdeckung anstreben; der Hinweis verhindert ein falsches Garantieversprechen. Eine Garantie oder erzwungene Seed-Suche ist noch nicht beschlossen. Biome und Strukturen getrennt bewerten.
