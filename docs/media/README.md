# Orbis – Animation

29 Sekunden: Quadrat → Zylinder → Torus → zwei Runden in +X.

- `orbis-torus.gif`: automatisch laufende README-Animation.
- `orbis-torus.mp4`: Video zum Abspielen und Pausieren.
- `render_orbis.py`: reproduzierbarer Renderer; Python, NumPy, Pillow und ffmpeg erforderlich. Aus dem Repository-Root starten: `python docs/media/render_orbis.py`.

Die Form ist ein topologisches Erklärmodell, nicht massstabsgetreu. Orbis krümmt weder Gelände noch Schwerkraft. X und Z werden periodisch verbunden; Y bleibt unverändert. Die Karte und der Positionsmarker zeigen dieselbe Position. Nach einer Weltbreite in +X ist bei gleichem Z und Y wieder der Start erreicht.

Alle Grafiken sind prozedural im Blockstil erstellt. Keine Minecraft-Texturen, keine Spielaufnahme und kein Nachweis bereits implementierter Funktionen.
