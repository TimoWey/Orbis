package dev.orbis.atlas;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Worker-owned reader of saved block palettes. Never installs chunks or requests generation. */
final class AtlasSavedTerrain implements AutoCloseable {
    private final net.minecraft.server.level.ChunkMap storage;
    private final Path regions;
    private final LevelHeightAccessor height;
    private final PalettedContainerFactory factory;
    private final boolean ceiling;
    private record Stamp(java.nio.file.attribute.FileTime modified, long size) {}
    private final Map<Path, Stamp> scanned = new HashMap<>();
    private Path currentRegion;
    private Stamp currentStamp;
    private boolean currentFailed;
    private DirectoryStream<Path> directory;
    private Iterator<Path> files = Collections.emptyIterator();
    private Iterator<ChunkPos> positions = Collections.emptyIterator();

    AtlasSavedTerrain(ServerLevel level) {
        storage = level.getChunkSource().chunkMap;
        regions = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("region");
        height = LevelHeightAccessor.create(level.getMinY(), level.getHeight());
        factory = PalettedContainerFactory.create(level.registryAccess());
        ceiling = level.dimensionType().hasCeiling();
    }

    void startScan() throws IOException {
        close();
        if (Files.isDirectory(regions)) {
            directory = Files.newDirectoryStream(regions, "r.*.*.mca");
            files = directory.iterator();
        }
    }

    /** Enumerate only saved chunks around the HUD first; never load or generate world chunks. */
    List<ChunkPos> nearby(int x, int z, int radius) throws IOException {
        return nearby(regions, x, z, radius);
    }

    static List<ChunkPos> nearby(Path regions, int x, int z, int radius) throws IOException {
        var result = new ArrayList<ChunkPos>();
        for (int rz = (z - radius) >> 5; rz <= (z + radius) >> 5; rz++) {
            for (int rx = (x - radius) >> 5; rx <= (x + radius) >> 5; rx++) {
                Path path = regions.resolve("r." + rx + "." + rz + ".mca");
                if (Files.isRegularFile(path)) for (var position : readHeader(path))
                    if (Math.abs(position.x() - x) <= radius && Math.abs(position.z() - z) <= radius) result.add(position);
            }
        }
        result.sort(Comparator.comparingLong(position -> AtlasTerrainQueue.distance(position.pack(), x, z)));
        return result;
    }

    ChunkPos next() throws IOException {
        while (!positions.hasNext()) {
            if (!files.hasNext()) {
                if (currentRegion != null && !currentFailed) scanned.put(currentRegion, currentStamp);
                close(); return null;
            }
            if (currentRegion != null && !currentFailed) scanned.put(currentRegion, currentStamp);
            currentRegion = null;
            Path path = files.next();
            var stamp = new Stamp(Files.getLastModifiedTime(path), Files.size(path));
            if (stamp.equals(scanned.get(path))) continue;
            currentRegion = path; currentStamp = stamp; currentFailed = false;
            positions = readHeader(path).iterator();
        }
        return positions.next();
    }

    // Region headers only enumerate real stored chunks, including negative coordinates.
    static List<ChunkPos> readHeader(Path path) throws IOException {
        String[] parts = path.getFileName().toString().split("\\.");
        if (parts.length != 4 || !parts[0].equals("r") || !parts[3].equals("mca")) return List.of();
        int rx, rz;
        try { rx = Integer.parseInt(parts[1]); rz = Integer.parseInt(parts[2]); }
        catch (NumberFormatException ignored) { return List.of(); }
        if (Files.size(path) < 8192) return List.of(); // Region creation can race this read.
        var result = new ArrayList<ChunkPos>();
        try (var input = new DataInputStream(Files.newInputStream(path))) {
            for (int i = 0; i < 1024; i++) {
                int location = input.readInt();
                if ((location >>> 8) >= 2 && (location & 255) != 0)
                    result.add(new ChunkPos(Math.addExact(Math.multiplyExact(rx, 32), i & 31),
                            Math.addExact(Math.multiplyExact(rz, 32), i >> 5)));
            }
        }
        return result;
    }

    void retryCurrentRegion() { currentFailed = true; }

    AtlasSnapshot.Cell read(ChunkPos position) {
        var tag = storage.read(position).join().orElse(null);
        if (tag == null || !SerializableChunkData.getChunkStatusFromTag(tag).isOrAfter(ChunkStatus.FULL)) return null;
        if (tag.getIntOr("xPos", Integer.MIN_VALUE) != position.x()
                || tag.getIntOr("zPos", Integer.MIN_VALUE) != position.z()) return null;
        return new AtlasSavedSurface(height, factory, tag).sample(position, ceiling);
    }

    public void close() throws IOException {
        if (directory != null) directory.close();
        directory = null; currentRegion = null; currentStamp = null;
        files = Collections.emptyIterator();
        positions = Collections.emptyIterator();
    }
}
