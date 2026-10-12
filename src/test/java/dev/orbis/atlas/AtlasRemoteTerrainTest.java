package dev.orbis.atlas;

import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasRemoteTerrainTest {
    private static final String WORLD = "a".repeat(64);
    @TempDir Path base;
    private static AtlasSnapshot.Cell tile(int x, int z, int color) {
        int[] pixels = new int[256];
        for (int i = 0; i < 256; i++) pixels[i] = color + i;
        return new AtlasSnapshot.Cell(x, z, color, pixels);
    }
    private Path directory(String world, String dimension) {
        return AtlasRemoteTerrain.directory(base, world, dimension, -8192, -8192, 16384);
    }

    @Test void exploredTerrainSurvivesReconnect() throws Exception {
        var first = new AtlasRemoteTerrain(directory(WORLD, "minecraft:overworld"));
        first.capture(tile(-40, 12, 0xff123400));
        first.tick();
        first.completion().get(10, TimeUnit.SECONDS);
        // Captured after the last tick: the close itself must flush it.
        first.capture(tile(300, -300, 0xff567800));
        first.close();
        first.completion().get(10, TimeUnit.SECONDS);
        assertTrue(first.archive().closed());

        var second = new AtlasRemoteTerrain(directory(WORLD, "minecraft:overworld"));
        try (var reader = second.archive().reader()) {
            assertEquals(0xff123400, reader.get(-40, 12).terrainPixel(0));
            assertEquals(0xff567800 + 255, reader.get(300, -300).terrainPixel(255));
            assertNull(reader.get(0, 0));
        }
        second.close();
    }

    @Test void worldsDimensionsAndBoundsNeverShareAnArchive() {
        var overworld = directory(WORLD, "minecraft:overworld");
        assertNotEquals(overworld, directory("b".repeat(64), "minecraft:overworld"));
        assertNotEquals(overworld, directory(WORLD, "minecraft:the_nether"));
        assertNotEquals(overworld, AtlasRemoteTerrain.directory(base, WORLD, "minecraft:overworld", -1024, -1024, 2048));
        assertTrue(overworld.startsWith(base.resolve("servers")));
    }

    @Test void worldIdIsAnOpaqueHashAndRejectsAnythingElse() {
        var original = new AtlasWorldPayload(AtlasCache.hash("/srv/world|2026-10-12T00:00:00Z|8675309"));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AtlasWorldPayload.CODEC.encode(buf, original);
            assertEquals(original, AtlasWorldPayload.CODEC.decode(buf));
            assertFalse(buf.isReadable());
        } finally { buf.release(); }
        for (String invalid : new String[]{"", "../../x", "g".repeat(64), "a".repeat(63)})
            assertThrows(IllegalArgumentException.class, () -> new AtlasWorldPayload(invalid));
        assertThrows(IllegalArgumentException.class, () -> new AtlasWorldPayload(null));
    }
}
