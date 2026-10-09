# Alpha.10 — Originalzeiger, Mitspieler und Maussteuerung

Geprüft am 9. Oktober 2026. Normales JAR: `orbis-0.1.0-alpha.10.jar`, Implementierungsstand `cc9490fe76cc37d87d6da6ae331f5d1fbce683f2`.

```
SHA256 a27bd39d48b67583789947478853329fb10bfbfe335bea364daf4a613656483d
Source 5d6e4fb254ab34082ac99f3a866f1404235918378565b1a6f6648dd9fa8f3f10
```

## Verhalten

Der eigene Marker kommt direkt aus Minecrafts `player`-Sprite im Karten-Textur-Atlas, Mitspieler verwenden das originale grüne `frame`-Sprite. Beide werden in Originalgröße 8×8 GUI-Pixeln ohne Farbänderung und mit 16 Richtungen dargestellt. Resourcepacks werden berücksichtigt. Es wird kein Minecraft-Bild in Orbis kopiert oder nachgezeichnet.

Der Server sendet alle zehn Ticks eine vollständige Positionsliste pro Dimension an Clients mit dem neuen Orbis-Kanal. Die Liste enthält auch Spieler ohne Client-Mod und außerhalb der Entity-Sichtweite. Unsichtbare Spieler/Zuschauer bleiben ausgeblendet. Es werden keine zusätzlichen Gelände-Chunks angefordert. Abmeldungen und Dimensionswechsel ersetzen die Liste; Fernpositionen verfallen nach drei Sekunden ohne Aktualisierung. Ohne neuen Server-Build zeigt der Client nur bereits geladene Mitspieler. Im Atlas erscheinen Namen beim Darüberfahren; der HUD bleibt ohne Namensschilder.

Der gemeldete Drag-Fehler hatte eine konkrete Ursache: Der normale Spieler-Atlas verglich die Maustaste noch mit `0`, während Minecraft 26.3 mit SDL für die linke Taste `InputConstants.MOUSE_BUTTON_LEFT == 1` verwendet. Press/Drag/Release verwenden jetzt diese Konstante. Ziehen beendet die Spieler-Verfolgung; Zentrieren/Home aktiviert sie wieder. Ein bereits zentrierter Atlas ändert sich dabei erwartungsgemäß nicht. Die Gelände-Ladepfade aus Alpha.9 wurden nicht geändert.

## Automatisierte Prüfung

Normal: 137 Tests bestanden, drei optionale Benchmarks übersprungen, zusätzlich drei DH-Tests bestanden. Debug: 151 bestanden, fünf übersprungen, zusätzlich drei DH-Tests bestanden. Beide Gradle-Builds und die getrennten JAR-/Sources-Provenienzprüfungen waren erfolgreich. Neue Tests prüfen Paket-Roundtrip mit entfernten Positionen, Größen-/Zahlenvalidierung, Entfernung abgemeldeter Spieler, Dimensionsfilter, eigenen Marker, Timeout und Disconnect. Die Marker-Tests prüfen Vanilla-Sprite-Referenzen, alle 16 Richtungen und Karten-/Globus-Projektion. [Build-Ergebnisse](build-results.json).

## Tatsächlich im Spiel geprüft

Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3. Der normale finale Build wurde per Hashvergleich auf dem dedizierten Testserver und dem Karten-Client installiert. Ein zweiter echter Minecraft-Client (`VanillaTester`) lief ohne Fabric/Orbis. Tiny-Welt, Seed 8675309, Sichtweite sechs Chunks, Loopback-Server mit Offline-Authentifizierung nur für diesen Test. Virtuelle Anzeigen mit Mesa-Software-Rendering; das ist kein FPS-/Lasttest.

- [Zwei Spieler im Atlas](nearby-atlas.png): eigener weißer Zeiger und grüner Marker bei 40 Blöcken Abstand. Die deckenden Pixel beider Zeiger wurden im Screenshot gegen die 8×8-Original-PNGs aus Minecraft verglichen: **120/120 beziehungsweise 108/108 Bildschirmpixel identisch**, GUI-Skalierung 2. [Eigener Marker](player-pixels.json), [Mitspieler](other-pixels.json), [Prüfskript](check_cursor.py).
- [Flache Karte nach Ziehen](flat-dragged.png): nach 120×40 Bildschirmpixeln Mausbewegung steht der eigene Marker bei (760,440), statt (640,400). [Zentrieren](flat-recentered.png) setzt ihn wieder exakt in die Mitte. Beide Positionen wurden anhand der Originalpixel geprüft.
- [Globus nach Ziehen](globe-dragged.png): deutliche Rotation gegenüber [der Ausgangslage](globe-centered.png), über 100.000 geänderte Pixel im Ausschnitt. [Zentrieren](globe-recentered.png) bringt den Originalmarker wieder auf (640,400). Mausrad-Zoom und Wechsel zwischen Karte/Globus funktionieren weiterhin.
- [Entfernter Spieler mit Namen](distant-player.png): `VanillaTester` wurde von X=150,5 nach X=610,5 teleportiert, der Karten-Spieler blieb bei X=110,5. Der grüne Marker erscheint **500 Blöcke entfernt**, deutlich außerhalb der Sichtweite, über noch unbekanntem Gelände. Der Name erscheint beim Darüberfahren. Auch hier stimmen die Marker-Pixel mit der Originaltextur überein.
- [Dimensionswechsel](other-dimension.png): Nach Transfer des zweiten Spielers in den Nether verschwindet sein Overworld-Marker. Nach Rückkehr wird er wieder angezeigt, auch in der [Minimap](nearby-minimap.png). Nach Abmeldung verschwindet er erneut: [Spieler abgemeldet](player-left.png).

[Maschinenlesbare Laufzeitergebnisse](runtime-results.json). Die Umgebung meldete nicht erreichbare Auth-/Realms-Dienste, fehlendes Audio und unter gleichzeitigen Builds/Software-Rendering zeitweise Tick-Verzögerungen. Der erste interne Marker-Test deckte eine falsche Atlas-ID auf; diese wurde vor dem hier geprüften finalen Build auf Minecrafts Karten-Textur-Sheet korrigiert. Der finale Client-Lauf zeigte keinen Orbis-Render-/Netzwerkfehler. Beide Clients und der Server wurden anschließend sauber beendet.

Die bestehenden Admin-Checks und Weltfunktionen sind im [Alpha.8-Protokoll](../alpha8/RESULTS.md), der restaurierte Ladepfad im [Alpha.9-Protokoll](../alpha9/RESULTS.md) dokumentiert. Diese Prüfungen wurden hier nicht als vollständige Weltprüfung wiederholt. Installation und Funktionsgrenzen: [Server-/Spieleranleitung](../../SERVER.md).
