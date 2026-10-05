# Architekturentwurf

Noch nicht implementiert.

| Baustein | Aufgabe |
| --- | --- |
| Upstream-Basis | Geprüften Toroidal-World-Commit übernehmen; Herkunft/Lizenz erhalten |
| Weltkonfiguration | Bounds, Grössenprofil, Generator-/Schemasversion persistent speichern |
| Koordinaten | Eindeutige kanonische Chunks, korrekte Normalisierung negativer Werte |
| Darstellung | Periodische Ansichten derselben Daten; keine doppelte Simulation |
| Distanzen | Kürzeste periodische Wege, passende Pfadfindung und Interaktionen |
| Generierung | Periodisches Noise-Gitter; Features, Strukturen, Höhlen und Flüssigkeiten mitprüfen |
| Dimensionen | Nether-Bounds = Overworld-Bounds / 8; End explizit ausschliessen |
| Biomprüfung | Tatsächlichen Generator abfragen; Nachweis, Abwesenheit und Unsicherheit trennen |
| Konverter | Bestandsmaske, Schutzbereiche, deterministische Übergänge und vollständige Datenmigration |
| Distant Horizons | Passende LOD-Koordinaten und Cache-Behandlung |

Singleplayer nutzt einen integrierten Server; Änderungen an Serverlogik bleiben erforderlich.

Periodische Generierung erfüllt sinngemäss N(x+Lx,y,z)=N(x,y,z) und N(x,y,z+Lz)=N(x,y,z). Gleicher Seed mit verändertem Generator garantiert kein gleiches Gelände.

Kein Startalgorithmus darf alle theoretischen Chunks durchlaufen. Gesamtflächen/-anzahlen mindestens als 64-Bit-Werte behandeln.
