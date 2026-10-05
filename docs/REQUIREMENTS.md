# Requirements

Stand: 2026-10-05. Alle Funktionen sind noch **geplant**.

| ID | Priorität | Requirement | Abnahme |
| --- | --- | --- | --- |
| R01 | Pflicht | Name Orbis; Basis Toroidal World | Eigene Mod-ID, Herkunft und Lizenzhinweise |
| R02 | Pflicht | Fabric, neueste stabile Java-Edition zum Projektstart | Konkrete Tool-/Spielversionen festgeschrieben |
| R03 | Pflicht | Singleplayer zuerst | Erstellen, Spielen, Speichern, Neuladen |
| R04 | Pflicht | Overworld/Nether nahtlos in X und Z | Alle Kanten und Ecken korrekt; Y unverändert |
| R05 | Pflicht | Nether exakt 1:8 | Gekoppelte Grenzen und korrekte Portalabbildung |
| R06 | Pflicht | End behält Vanilla-Verhalten | Keine Orbis-Grenze; Portale/Gateways funktionieren |
| R07 | Pflicht | Fünf Grössenstufen plus Erde | Finale Werte nach Biomuntersuchung |
| R08 | Pflicht | Sehr klein/Klein dürfen Biome vermissen | Verständlicher Hinweis |
| R09 | Ziel | Normal enthält alle natürlich generierenden Overworld-/Nether-Biome | Beide Dimensionen geprüft; Varianten/Höhlen eingeschlossen |
| R10 | Pflicht | Biomprüfung bei neuer Welt mit transparentem Ergebnis | Vollständig / unvollständig / nicht vollständig geprüft unterscheiden |
| R11 | Pflicht | Distant Horizons + Sodium + Lithium | Gemeinsam mit festen Versionen getestet |
| R12 | Pflicht | Konvertierung bestehender Welten | Original unverändert; neue Weltkopie mit Vorschau |
| R13 | Pflicht | Randchunks gezielt regenerieren und anschliessen | Schutzbereiche erhalten; keine abrupten neuen Geländeübergänge |
| R14 | Pflicht | Inventare, Entities und Weltdaten erhalten | Datenintegrität nach Konvertierung und Neustart |
| R15 | Pflicht | Konsistente Simulation über die Naht | Keine Duplikation; Licht, Flüssigkeiten, Redstone, Mobs, Fahrzeuge geprüft |
| R16 | Pflicht | Bedarfsgesteuerte Generierung | Keine komplette Vorgenerierung; Erde ohne globale Flächenarrays |
| R17 | Pflicht | Korrekte DH-Fernsicht und Caches | Gelände/LOD stimmen an Naht und nach Konvertierung überein |
| R18 | Pflicht | Keine automatische Seedänderung bestehender Welten | Fehlende Biome melden und Lösungen anbieten |

**Offen:** Finalgrössen; Mindestfläche je Biom; gewünschte Trefferquote (Vorschlag 99 %); genaue Quellversion der bestehenden Welt; Leistungsbudgets.

**Nicht Version 1:** dedizierter Multiplayer, gekrümmte Donut-Grafik, geografische Erdkarte, pauschale Kompatibilität mit allen Mods.
