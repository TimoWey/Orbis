package dev.orbis.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** One immutable save identity shared by predictions and markers; published by the server thread. */
final class AtlasWorldIdentity {
    private static final Map<MinecraftServer, String> IDENTITIES = new IdentityHashMap<>();

    static synchronized String prepare(MinecraftServer server, long seed) throws IOException {
        String identity = IDENTITIES.get(server);
        if (identity == null) {
            identity = resolve(server.getWorldPath(LevelResource.ROOT), seed);
            IDENTITIES.put(server, identity);
        }
        return identity;
    }

    /** Client callers only read the published value; they never inspect server world data. */
    static synchronized String get(MinecraftServer server) { return IDENTITIES.get(server); }

    static synchronized void clear(MinecraftServer server) { IDENTITIES.remove(server); }

    static String resolve(Path root, long seed) throws IOException {
        Path real = root.toRealPath();
        return real + "|" + Files.readAttributes(real, BasicFileAttributes.class).creationTime() + "|" + seed;
    }

    static String waypointKey(String identity, String dimension) {
        return identity == null ? null : "v2|" + identity + "|" + dimension;
    }

    private AtlasWorldIdentity() { }
}
