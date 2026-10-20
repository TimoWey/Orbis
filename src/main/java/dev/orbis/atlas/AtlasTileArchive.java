package dev.orbis.atlas;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import net.minecraft.world.level.ChunkPos;

/** Sparse region archive of original 16x16 colors; owned by one world/dimension session. */
final class AtlasTileArchive {
    private static final long MAGIC = 0x4f524254494c4501L;
    // Bump only when stored tiles become incompatible; mod, data pack and generator changes keep the explored map.
    private static final String LOCAL_FORMAT = "local-v1";
    // Before local-v1, singleplayer tiles lived under the full survey fingerprint (mods, generator, resources).
    private static final java.util.regex.Pattern LEGACY = java.util.regex.Pattern.compile("[0-9a-f]{64}");
    private static final int PAYLOAD = 4 + 256 * 4 + 4, SLOT = 1 + PAYLOAD;
    // Reloads can briefly have two session objects for the same fingerprint directory.
    private static final Object FILE_IO = new Object();
    private final Path directory;
    private final AtomicLong revision = new AtomicLong(1);
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> changes = new java.util.concurrent.ConcurrentHashMap<>();
    // Shared by minimap/fullscreen projections and retained when a screen is reopened.
    // At most 128 MiB of original block colors, plus cell/index overhead.
    private final LinkedHashMap<Long, AtlasSnapshot.Cell[]> regions = new LinkedHashMap<>(128, .75f, true);
    private Set<Long> existingRegions;
    private static final AtlasSnapshot.Cell[] EMPTY = new AtlasSnapshot.Cell[1024];
    private volatile boolean closed;

    AtlasTileArchive(Path directory) { this.directory = directory; }

    /** Singleplayer tiles beside the survey cache file, whose path already names the world and dimension. */
    static Path localDirectory(Path cacheFile, int minX, int minZ, int span) {
        return cacheFile.resolveSibling(cacheFile.getFileName() + ".tiles")
                .resolve(LOCAL_FORMAT + "-" + AtlasCache.hash(minX + "|" + minZ + "|" + span));
    }

    /** Adopts the newest legacy fingerprint directory once, then removes the stale ones. Worker threads only. */
    static void migrateLocal(Path directory) throws IOException {
        Path parent = directory.getParent();
        if (!Files.isDirectory(parent)) return;
        List<Path> legacy = new ArrayList<>();
        try (var paths = Files.newDirectoryStream(parent, path -> Files.isDirectory(path)
                && LEGACY.matcher(path.getFileName().toString()).matches())) {
            paths.forEach(legacy::add);
        }
        if (legacy.isEmpty()) return;
        if (!Files.exists(directory)) {
            Path newest = null; long newestTime = Long.MIN_VALUE;
            for (var path : legacy) {
                long time = lastWrite(path);
                if (newest == null || time > newestTime) { newest = path; newestTime = time; }
            }
            synchronized (FILE_IO) {
                if (!Files.exists(directory)) { Files.move(newest, directory); legacy.remove(newest); }
            }
        }
        for (var path : legacy) delete(path);
    }
    private static long lastWrite(Path directory) throws IOException {
        long latest = Files.getLastModifiedTime(directory).toMillis();
        try (var paths = Files.newDirectoryStream(directory, "r.*.*.tiles")) {
            for (var path : paths) latest = Math.max(latest, Files.getLastModifiedTime(path).toMillis());
        }
        return latest;
    }
    private static void delete(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    long revision() { return revision.get(); }
    /** Read-only, nonblocking invalidation for the few regions covered by the HUD texture. */
    long revision(int blockX, int blockZ, int size) {
        return revision(blockX, blockZ, blockX + size - 1, blockZ + size - 1);
    }
    /** Latest write in the regions overlapping the inclusive block rectangle. */
    long revision(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        long latest = 1;
        for (int rz = minBlockZ >> 9; rz <= maxBlockZ >> 9; rz++)
            for (int rx = minBlockX >> 9; rx <= maxBlockX >> 9; rx++)
                latest = Math.max(latest, changes.getOrDefault(ChunkPos.pack(rx, rz), 1L));
        return latest;
    }
    boolean closed() { return closed; }
    void close() { closed = true; synchronized (FILE_IO) { synchronized (regions) { regions.clear(); } existingRegions = null; } }
    private Path file(int x, int z) { return directory.resolve("r." + (x >> 5) + "." + (z >> 5) + ".tiles"); }
    private static long offset(int x, int z) { return 8L + (((z & 31) * 32 + (x & 31)) * (long) SLOT); }

    /** Native saved/live terrain takes priority over DH data, including across restarts. */
    synchronized void write(AtlasSnapshot.Cell cell, boolean distant) throws IOException {
        write(cell, distant ? 1 : 2);
    }
    synchronized void write(AtlasSnapshot.Cell cell, int rank) throws IOException {
        write(List.of(cell), rank);
    }
    /** Writes many tiles, opening each region file once; pregeneration delivers tiles far faster than one open per tile. */
    synchronized void write(Collection<AtlasSnapshot.Cell> cells, int rank) throws IOException {
        var byRegion = new LinkedHashMap<Long, List<AtlasSnapshot.Cell>>();
        for (var cell : cells) if (cell.terrainSize() == 256)
            byRegion.computeIfAbsent(ChunkPos.pack(cell.x() >> 5, cell.z() >> 5), key -> new ArrayList<>()).add(cell);
        synchronized (FILE_IO) {
            if (closed || byRegion.isEmpty()) return;
            Files.createDirectories(directory);
            for (var region : byRegion.values()) writeRegion(region, rank);
        }
    }
    private void writeRegion(List<AtlasSnapshot.Cell> cells, int rank) throws IOException {
        var first = cells.getFirst();
        long key = ChunkPos.pack(first.x() >> 5, first.z() >> 5);
        var written = new ArrayList<AtlasSnapshot.Cell>();
        try (var file = new RandomAccessFile(file(first.x(), first.z()).toFile(), "rw")) {
            if (file.length() == 0) file.writeLong(MAGIC);
            file.seek(0);
            if (file.readLong() != MAGIC) throw new IOException("Invalid atlas tile archive header");
            byte[] payload = new byte[PAYLOAD], previous = new byte[PAYLOAD];
            for (var cell : cells) {
                var buffer = ByteBuffer.wrap(payload);
                buffer.putInt(cell.color());
                for (int i = 0; i < 256; i++) buffer.putInt(cell.terrainPixel(i));
                var crc = new CRC32(); crc.update(payload, 0, PAYLOAD - 4);
                buffer.putInt((int) crc.getValue());
                long offset = offset(cell.x(), cell.z());
                if (file.length() >= offset + SLOT) {
                    file.seek(offset);
                    int previousRank = file.readUnsignedByte();
                    if (previousRank > rank) continue;
                    file.readFully(previous);
                    if (previousRank == rank && Arrays.equals(previous, payload)) continue;
                }
                // Publish the validity byte last, so incomplete slots are never read as valid terrain.
                file.seek(offset); file.writeByte(0); file.write(payload);
                file.seek(offset); file.writeByte(rank);
                written.add(cell);
            }
        }
        if (written.isEmpty()) return;
        if (existingRegions != null) existingRegions.add(key);
        synchronized (regions) {
            var cached = regions.get(key);
            if (cached != null) {
                // Readers keep an immutable region for the duration of one projection.
                cached = cached.clone();
                for (var cell : written) cached[(cell.z() & 31) * 32 + (cell.x() & 31)] = cell;
                regions.put(key, cached);
            }
        }
        changes.put(key, revision.incrementAndGet());
    }

    /** Rank of every slot in one region (0 = no tile), read without decoding any terrain. Worker threads only. */
    byte[] ranks(int rx, int rz) throws IOException {
        byte[] ranks = new byte[1024];
        synchronized (FILE_IO) {
            Path path = file(rx << 5, rz << 5);
            if (closed || !Files.isRegularFile(path)) return ranks;
            try (var file = new RandomAccessFile(path.toFile(), "r")) {
                if (file.length() < 8) return ranks;
                if (file.readLong() != MAGIC) return ranks;
                for (int slot = 0; slot < 1024 && 8 + (slot + 1L) * SLOT <= file.length(); slot++) {
                    file.seek(8 + slot * (long) SLOT);
                    int rank = file.readUnsignedByte();
                    ranks[slot] = (byte) (rank <= 3 ? rank : 0);
                }
            }
        }
        return ranks;
    }

    Reader reader() { return new Reader(null); }
    Reader residentReader() {
        synchronized (regions) { return new Reader(new HashMap<>(regions)); }
    }
    private AtlasSnapshot.Cell[] region(int rx, int rz) throws IOException {
        // Loaded regions need no file lock, so parallel readers do not queue behind each other.
        if (!closed) synchronized (regions) {
            var cached = regions.get(ChunkPos.pack(rx, rz));
            if (cached != null) return cached;
        }
        synchronized (FILE_IO) {
            if (closed) return EMPTY;
            if (existingRegions == null) {
                existingRegions = new HashSet<>();
                if (Files.isDirectory(directory)) try (var paths = Files.newDirectoryStream(directory, "r.*.*.tiles")) {
                    for (var path : paths) {
                        String[] parts = path.getFileName().toString().split("\\.");
                        if (parts.length == 4) try {
                            existingRegions.add(ChunkPos.pack(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                        } catch (NumberFormatException ignored) { }
                    }
                }
            }
            long key = ChunkPos.pack(rx, rz);
            synchronized (regions) {
                var cached = regions.get(key);
                if (cached != null) return cached;
            }
            if (!existingRegions.contains(key)) return EMPTY;
            var cells = new AtlasSnapshot.Cell[1024];
            if (existingRegions.contains(key)) {
                byte[] bytes;
                // A single sequential read replaces thousands of seek/read/length calls.
                try (var input = Files.newInputStream(file(rx << 5, rz << 5))) {
                    bytes = input.readNBytes(8 + 1024 * SLOT);
                }
                var buffer = ByteBuffer.wrap(bytes);
                if (bytes.length >= 8 && buffer.getLong() == MAGIC) {
                    var crc = new CRC32();
                    for (int slot = 0; slot < 1024 && 8 + (slot + 1) * SLOT <= bytes.length; slot++) {
                        int offset = 8 + slot * SLOT;
                        int rank = bytes[offset];
                        if (rank < 1 || rank > 3) continue;
                        crc.reset(); crc.update(bytes, offset + 1, PAYLOAD - 4);
                        if (buffer.getInt(offset + SLOT - 4) != (int) crc.getValue()) continue;
                        buffer.position(offset + 1);
                        int fallback = buffer.getInt();
                        int[] pixels = new int[256];
                        for (int i = 0; i < pixels.length; i++) pixels[i] = buffer.getInt();
                        cells[slot] = new AtlasSnapshot.Cell((rx << 5) + (slot & 31), (rz << 5) + (slot >> 5), fallback, pixels);
                    }
                }
            }
            synchronized (regions) {
                regions.put(key, cells);
                if (regions.size() > 128) regions.remove(regions.keySet().iterator().next());
            }
            return cells;
        }
    }
    final class Reader implements AutoCloseable {
        private final LinkedHashMap<Long, AtlasSnapshot.Cell[]> visible = new LinkedHashMap<>(128, .75f, true);
        private final Map<Long, AtlasSnapshot.Cell[]> resident;
        Reader(Map<Long, AtlasSnapshot.Cell[]> resident) { this.resident = resident; }
        private long lastKey = Long.MIN_VALUE;
        private AtlasSnapshot.Cell[] last;
        AtlasSnapshot.Cell get(int x, int z) throws IOException {
            long key = ChunkPos.pack(x >> 5, z >> 5);
            if (last == null || key != lastKey) {
                last = visible.get(key);
                if (last == null) {
                    last = resident == null ? region(x >> 5, z >> 5) : resident.getOrDefault(key, EMPTY);
                    visible.put(key, last);
                    if (visible.size() > 128) visible.remove(visible.keySet().iterator().next());
                }
                lastKey = key;
            }
            return last[(z & 31) * 32 + (x & 31)];
        }
        public void close() { visible.clear(); last = null; }
    }
}
