# Orbis 0.1.0-alpha.21 (first public alpha)

Minecraft 26.3 · Fabric Loader 0.19.5 · Fabric API 0.161.0+26.3 · Java 25. Client and server; every player needs Orbis on an Orbis world.

This is an early alpha. Back up your worlds; a new world is best for testing.

## What's in it
- A finite square world without a wall: the Overworld and the Nether continue across both edges. Mobs, villages, projectiles, redstone and portals work across the seam.
- World sizes from Tiny (16,384 blocks per side) to Earth, with a slider, presets and an exact width.
- The Nether is always exactly one eighth of the Overworld. The End stays normal.
- Minimap and atlas (key **M**): flat map (continued past the edges, or the world as one square) and a 3D globe, smooth zoom, explored terrain kept across restarts and on servers.
- Server ready: one JAR for server and players, `orbis:tiny` … `orbis:earth` level types, `/orbis check` for biomes and structures.
- Worlds from earlier Orbis alphas open again.

## Tested with
- Sodium 0.9.2, Lithium 0.26.2 (Orbis turns off Lithium's POI optimization), Distant Horizons 3.3.4 (far terrain continues past the edges), C2ME 0.4.2.
- Chunky pre-generation works. Don't combine Distant Horizons with Chunky: creating a world hangs.
