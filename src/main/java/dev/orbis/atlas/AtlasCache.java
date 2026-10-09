package dev.orbis.atlas;

import dev.orbis.Orbis;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.*;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** Disposable, bounded atlas metadata. No Java deserialization and no writes to the world save. */
final class AtlasCache {
    // v4: progressive search order changed; old cursor checkpoints must never resume in it.
    static final int VERSION = 4, CHUNK_LIMIT = 8192, ENTRY_LIMIT = 16384;
    private static final int MAGIC = 0x4f524241;
    private static final Map<MinecraftServer, ResourceFingerprint> RESOURCE_FINGERPRINTS = new IdentityHashMap<>();
    static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Orbis atlas cache"); t.setDaemon(true); return t;
    });
    record Data(AtlasSearch.Saved search, boolean limited, Map<Integer, Integer> colors,
            Map<String, Integer> hits, Set<String> observed, Map<String, AtlasSnapshot.Place> locations,
            List<AtlasSnapshot.Place> places, List<AtlasSnapshot.Cell> chunks, StructureAudit.Saved audit) {}
    record Loaded(Path file, String fingerprint, Data data, String status) {}

    static CompletableFuture<Loaded> load(ServerLevel level, int minX, int minZ, int span) {
        String dimension = level.dimension().identifier().toString();
        long seed = level.getSeed();
        String generator, identity;
        CompletableFuture<String> resourceFingerprint;
        try {
            identity = AtlasWorldIdentity.prepare(level.getServer(), level.getServer().overworld().getSeed());
            generator = ChunkGenerator.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()),
                    level.getChunkSource().getGenerator()).getOrThrow().toString();
            resourceFingerprint = resourcesFingerprint(level.getServer());
        } catch (IOException | RuntimeException e) {
            Orbis.LOGGER.warn("Atlas cache disabled: world or generator cannot be fingerprinted", e);
            return CompletableFuture.completedFuture(new Loaded(null, "", null, "Unavailable: world / generator identity"));
        }
        String mods = FabricLoader.getInstance().getAllMods().stream()
                .map(m -> m.getMetadata().getId() + "=" + m.getMetadata().getVersion().getFriendlyString()).sorted().toList().toString();
        String config = seed + "|" + dimension + "|" + minX + "|" + minZ + "|" + span + "|" + generator + "|" + mods
                + "|structures=" + level.structureManager().shouldGenerateStructures();
        Path file = fileFor(FabricLoader.getInstance().getGameDir().resolve("orbis-atlas-cache"), identity, dimension);
        return resourceFingerprint.handleAsync((resourceHash, failure) -> {
            try {
                if (failure != null) throw new CompletionException(failure);
                String fingerprint = hash(config + "|resources=" + resourceHash);
                Data data = read(file, fingerprint);
                return new Loaded(file, fingerprint, data, data == null ? "New / invalidated" : "Restored");
            } catch (Exception e) {
                Orbis.LOGGER.warn("Atlas cache unavailable; using in-memory survey", e);
                return new Loaded(null, "", null, "Unavailable; memory only");
            }
        }, IO);
    }

    /** Capture resource handles on the owner thread; workers never traverse the server or resource manager. */
    private static CompletableFuture<String> resourcesFingerprint(MinecraftServer server) {
        var resources = server.getResourceManager();
        return RESOURCE_FINGERPRINTS.computeIfAbsent(server, ignored -> new ResourceFingerprint()).get(resources, () -> {
            Map<String, ResourceContent> contents = new TreeMap<>();
            resources.listResources("", id -> true).forEach((id, resource) -> contents.put(id.toString(), resource::open));
            return Map.copyOf(contents);
        });
    }

    static void invalidateResources(MinecraftServer server) {
        ResourceFingerprint previous = RESOURCE_FINGERPRINTS.remove(server);
        if (previous != null) previous.invalidate();
    }
    static void clear(MinecraftServer server) {
        invalidateResources(server);
        AtlasWorldIdentity.clear(server);
    }

    @FunctionalInterface interface ResourceContent { InputStream open() throws IOException; }
    /** Shared once per reload. Closed pack streams fail safely; stale completions are never accepted. */
    static final class ResourceFingerprint {
        private Object generation;
        private CompletableFuture<String> value;
        private long revision;
        synchronized CompletableFuture<String> get(Object current, Supplier<Map<String, ResourceContent>> capture) {
            if (current != generation || value == null) {
                Map<String, ResourceContent> contents = capture.get();
                generation = current;
                long expected = ++revision;
                value = CompletableFuture.supplyAsync(() -> {
                    try {
                        String fingerprint = hashResources(contents);
                        synchronized (this) {
                            if (revision != expected) throw new CancellationException("Atlas resources reloaded");
                        }
                        return fingerprint;
                    } catch (IOException e) { throw new CompletionException(e); }
                }, IO);
            }
            return value;
        }
        synchronized void invalidate() { revision++; generation = null; value = null; }
    }

    static String hashResources(Map<String, ResourceContent> contents) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (String name : new TreeSet<>(contents.keySet())) {
                byte[] id = name.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(id.length).array());
                digest.update(id);
                MessageDigest content = MessageDigest.getInstance("SHA-256");
                try (InputStream in = contents.get(name).open()) {
                    int n;
                    while ((n = in.read(buffer)) != -1) content.update(buffer, 0, n);
                }
                digest.update(content.digest());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static Path fileFor(Path base, String world, String dimension) {
        return base.resolve(hash(world)).resolve(hash(dimension) + ".atlas.gz");
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static Data read(Path file, String fingerprint) {
        if (!Files.isRegularFile(file)) return null;
        // Keep the raw stream in its own resource slot: a bad gzip header can throw
        // in the constructor, before the outer stream exists (notably locks files on Windows).
        try (var raw = Files.newInputStream(file);
             var in = new DataInputStream(new BufferedInputStream(new LimitedInput(new GZIPInputStream(raw))))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || !in.readUTF().equals(fingerprint)) return null;
            var search = new AtlasSearch.Saved(in.readInt(), in.readInt(), in.readBoolean(),
                    in.readInt(), in.readInt(), in.readBoolean());
            boolean limited = in.readBoolean();
            Map<Integer, Integer> colors = new HashMap<>();
            for (int n = count(in, 256 * 256); n > 0; n--)
                colors.put(bounded(in.readInt(), 256 * 256 - 1), in.readInt());
            Map<String, Integer> hits = new HashMap<>();
            for (int n = count(in, ENTRY_LIMIT); n > 0; n--) hits.put(in.readUTF(), bounded(in.readInt(), AtlasSearch.MAX + AtlasSearch.MAP_MAX));
            Set<String> observed = strings(in);
            Map<String, AtlasSnapshot.Place> locations = places(in);
            List<AtlasSnapshot.Place> markers = new ArrayList<>(places(in).values());
            List<AtlasSnapshot.Cell> chunks = new ArrayList<>();
            for (int n = count(in, CHUNK_LIMIT); n > 0; n--) {
                int x = in.readInt(), z = in.readInt(), color = in.readInt(), size = in.readInt();
                if (size != 0 && size != 16 && size != 256) throw new IOException("Invalid tile");
                int[] pixels = new int[size];
                for (int i = 0; i < size; i++) pixels[i] = in.readInt();
                chunks.add(new AtlasSnapshot.Cell(x, z, color, pixels));
            }
            var found = places(in); var seen = strings(in);
            List<StructureAudit.Progress> tasks = new ArrayList<>();
            for (int n = count(in, ENTRY_LIMIT); n > 0; n--) {
                long position = in.readLong();
                if (position < 0) throw new IOException("Invalid task cursor");
                tasks.add(new StructureAudit.Progress(position, count(in, Integer.MAX_VALUE), count(in, Integer.MAX_VALUE), in.readBoolean()));
            }
            long checks = in.readLong(); int next = count(in, ENTRY_LIMIT); boolean running = in.readBoolean();
            var actualStarts = occurrences(in);
            var predictedStarts = occurrences(in);
            boolean countLimited = in.readBoolean();
            var policy = new StructureSearchPolicy(in.readBoolean(), in.readLong());
            if (actualStarts.values().stream().mapToInt(Set::size).sum()
                    + predictedStarts.values().stream().mapToInt(Set::size).sum() > StructureCounts.LIMIT)
                throw new IOException("Too many structure occurrences");
            if (!policy.counting() && (policy.limit() < checks || policy.limit() - checks > StructureSearchPolicy.QUICK_CHECKS))
                throw new IOException("Invalid existence-search budget");
            if (checks < 0 || in.read() != -1) throw new IOException("Invalid trailer"); // also verifies gzip CRC
            return new Data(search, limited, Map.copyOf(colors), Map.copyOf(hits), Set.copyOf(observed),
                    Map.copyOf(locations), List.copyOf(markers), List.copyOf(chunks),
                    new StructureAudit.Saved(Map.copyOf(found), Set.copyOf(seen), List.copyOf(tasks), checks, next, running,
                            new StructureCounts.Saved(actualStarts, predictedStarts, countLimited), policy));
        } catch (IOException | RuntimeException e) {
            Orbis.LOGGER.warn("Ignoring damaged atlas cache {}", file, e); return null;
        }
    }
    static void write(Path file, String fingerprint, Data data) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), "atlas-", ".tmp");
        try {
            try (var raw = Files.newOutputStream(tmp);
                 var out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(raw)))) {
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeUTF(fingerprint);
                var search = data.search();
                out.writeInt(search.cursor()); out.writeInt(search.limit()); out.writeBoolean(search.running());
                out.writeInt(search.mapCursor()); out.writeInt(search.mapLimit()); out.writeBoolean(search.mapping());
                out.writeBoolean(data.limited());
                out.writeInt(data.colors().size());
                for (var e : data.colors().entrySet()) { out.writeInt(e.getKey()); out.writeInt(e.getValue()); }
                out.writeInt(data.hits().size());
                for (var e : data.hits().entrySet()) { out.writeUTF(e.getKey()); out.writeInt(e.getValue()); }
                strings(out, data.observed()); places(out, data.locations());
                Map<String, AtlasSnapshot.Place> markers = new LinkedHashMap<>();
                for (var p : data.places()) markers.put(p.label() + ":" + p.x() + ":" + p.y() + ":" + p.z(), p);
                places(out, markers);
                out.writeInt(data.chunks().size());
                for (var c : data.chunks()) {
                    out.writeInt(c.x()); out.writeInt(c.z()); out.writeInt(c.color()); out.writeInt(c.terrainSize());
                    for (int i = 0; i < c.terrainSize(); i++) out.writeInt(c.terrainPixel(i));
                }
                var audit = data.audit(); places(out, audit.found()); strings(out, audit.observed());
                out.writeInt(audit.tasks().size());
                for (var p : audit.tasks()) { out.writeLong(p.cursor()); out.writeInt(p.extraCursor()); out.writeInt(p.ringCursor()); out.writeBoolean(p.done()); }
                out.writeLong(audit.checks()); out.writeInt(audit.nextTask()); out.writeBoolean(audit.running());
                occurrences(out, audit.counts().observed()); occurrences(out, audit.counts().predicted());
                out.writeBoolean(audit.counts().limited());
                out.writeBoolean(audit.policy().counting()); out.writeLong(audit.policy().limit());
            }
            try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    private static int bounded(int value, int max) throws IOException {
        if (value < 0 || value > max) throw new IOException("Invalid cache count"); return value;
    }
    private static Map<String, Set<Long>> occurrences(DataInputStream in) throws IOException {
        Map<String, Set<Long>> result = new HashMap<>();
        int total = 0;
        for (int n = count(in, ENTRY_LIMIT); n > 0; n--) {
            String id = in.readUTF();
            int size = count(in, StructureCounts.LIMIT);
            if ((total += size) > StructureCounts.LIMIT) throw new IOException("Too many structure occurrences");
            Set<Long> starts = new HashSet<>();
            for (int i = 0; i < size; i++) starts.add(in.readLong());
            result.put(id, Set.copyOf(starts));
        }
        return Map.copyOf(result);
    }
    private static void occurrences(DataOutputStream out, Map<String, Set<Long>> values) throws IOException {
        out.writeInt(values.size());
        for (var entry : values.entrySet()) {
            out.writeUTF(entry.getKey()); out.writeInt(entry.getValue().size());
            for (long start : entry.getValue()) out.writeLong(start);
        }
    }
    /** Bounds the decompressed stream too, including malicious string lengths / compression bombs. */
    private static final class LimitedInput extends FilterInputStream {
        private long remaining = 64L * 1024 * 1024;
        LimitedInput(InputStream in) { super(in); }
        @Override public int read() throws IOException {
            if (--remaining < 0) throw new IOException("Cache exceeds size limit");
            return in.read();
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) throw new IOException("Cache exceeds size limit");
            int n = in.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) remaining -= n;
            return n;
        }
    }
    private static int count(DataInputStream in, int max) throws IOException { return bounded(in.readInt(), max); }
    private static Set<String> strings(DataInputStream in) throws IOException {
        Set<String> result = new HashSet<>();
        for (int n = count(in, ENTRY_LIMIT); n > 0; n--) result.add(in.readUTF()); return result;
    }
    private static void strings(DataOutputStream out, Set<String> values) throws IOException {
        out.writeInt(values.size()); for (String s : values) out.writeUTF(s);
    }
    private static Map<String, AtlasSnapshot.Place> places(DataInputStream in) throws IOException {
        Map<String, AtlasSnapshot.Place> result = new LinkedHashMap<>();
        for (int n = count(in, ENTRY_LIMIT); n > 0; n--)
            result.put(in.readUTF(), new AtlasSnapshot.Place(in.readUTF(), in.readInt(), in.readInt(), in.readInt()));
        return result;
    }
    private static void places(DataOutputStream out, Map<String, AtlasSnapshot.Place> values) throws IOException {
        out.writeInt(values.size());
        for (var e : values.entrySet()) {
            var p = e.getValue(); out.writeUTF(e.getKey()); out.writeUTF(p.label()); out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z());
        }
    }
    private AtlasCache() {}
}
