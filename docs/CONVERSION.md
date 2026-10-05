# Konvertierung bestehender Welten

**Geplant; Upstream unterstützt diese Funktion noch nicht.**

| Schritt | Verhalten |
| --- | --- |
| Analysieren | Quellversion, Generator, Dimensionen, vorhandene Chunks und wichtige Positionen erfassen |
| Ausschnitt wählen | Overworld und Nether gekoppelt bei 1:8; Inhalte ausserhalb anzeigen |
| Schützen | Baugebiete manuell markieren; automatische Erkennung allein ist nicht zuverlässig |
| Vorschau | Betroffene Randstreifen, Schutzkonflikte und fehlende/unbestätigte Biome anzeigen |
| Kopieren | Nur auf separater Weltkopie arbeiten |
| Regenerieren | Ausgewählte Randchunks ersetzen; Übergänge zu erhaltenem Gelände und Gegenrand inklusive Ecken |
| Daten erhalten | Inventare, Entities, Block-Entities, POIs, Ticks, Strukturen, Spieler und Fortschritt konsistent übernehmen |
| Abschliessen | End erhalten; DH-Daten aktualisieren; Ergebnis prüfen und Bericht erstellen |

## Regeln

- Seed bestehender Welt nicht stillschweigend ändern.
- Keine Modulo-Zusammenführung verschiedener alter Chunks auf dieselbe Zielposition.
- Streifenbreite einstellbar; schwieriges Gelände braucht eventuell mehr Platz.
- Übergänge zwischen alten und künftig generierten Chunks auch im Inneren behandeln.
- Ungenerierte Randbereiche bei Bedarf erzeugen; keine komplette Welt vorberechnen.
- Fehlende Biome können grössere Grenzen oder ausdrücklich gewählte Geländeänderungen erfordern.
- Ohne freien Platz können vollständige Biomvielfalt und unveränderte Bauten unvereinbar sein.

**Offen:** Quellversion/Modliste der Nutzerwelt, verschiebbarer Ausschnitt, Default-Streifenbreite, unterstützte fremde Generatoren.
