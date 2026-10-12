# Alpha.8 — vereinfachte Oberfläche, 9. Oktober 2026

Dieses UI-Update ersetzt das zuvor angebotene Alpha.8-JAR. Die Versionsnummer bleibt Alpha.8; Build-Provenienz und Prüfsumme unterscheiden die aktualisierte Datei.

- `9935e4b`: Plus-/Minus-Buttons aus dem Spieler-Atlas entfernt. Mausrad und Tastaturkürzel zoomen weiterhin.
- `0e65fb98f9566679f4ab5d93801bd429ac80c726`: Unerkundeter Hintergrund verwendet genau zwei neutrale Grautöne: Wasser `#5c5c5c`, Land `#ababab`. Keine getrennten Wald-/Trockenlandfarben. Die Darstellung bleibt symbolisch und seedunabhängig; bekannte Gelände-Pixel behalten ihre Originalfarben.

Aktuelles normales JAR: `orbis-0.1.0-alpha.8.jar`.

```
SHA256 05745ca2bd9c1c3e78e361f7b4f74555e1e7a2d7d8542719ce9d40e55e0195ce
Source 51cb11a98b795352f29cc12f8b5c4ed9bb65316096d11d292353e8580ee827c7
```

Normaler Build: 98 Tests bestanden, zwei optionale Benchmarks übersprungen. Debug: 143 bestanden, fünf Benchmarks übersprungen; zusätzliche DH-Suite: drei bestanden. Beide Gradle-Builds, Distribution-/Preset-Prüfungen und separate Python-JAR/Sources-Verifikation erfolgreich. Die bestehende Hintergrund-Regression läuft nun auch in der normalen Suite und prüft zwei Grautöne, Zoomstufen, unveränderte erkundete Farben und Unabhängigkeit von Biome-Samples. [Maschinenlesbare Ergebnisse](ui-build-results.json).

Das normale JAR wurde mit Hashvergleich in die lokalen Server-/Client-Testprofile installiert, die Vorgänger wurden gesichert. Im echten Minecraft-Client auf dem dedizierten Server geprüft: zentrierter zweifarbiger Globus, Karte ohne Zoom-Buttons und Mausrad-Zoom von 1 auf 1,25 sowie zurück. Der geänderte Globuszoom wurde in `orbis-atlas.properties` gespeichert. Client und Server anschließend sauber beendet. Das Windows-Modrinth-Profil des Nutzers ist hier weiterhin nicht zugänglich.

- [Aktueller Globus ohne Zoom-Buttons](globe-two-tone.png)
- [Aktuelle Karte ohne Zoom-Buttons](atlas-no-zoom-buttons.png)

Die früheren Server-Checks und Screenshots sind im [ursprünglichen Alpha.8-Protokoll](RESULTS.md) dokumentiert; sie zeigen teilweise noch die alte Oberfläche. Für diesen rein visuellen Nachtrag wurden die Welt-Checks nicht erneut vollständig ausgeführt.
