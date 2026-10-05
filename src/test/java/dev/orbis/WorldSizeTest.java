package dev.orbis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class WorldSizeTest {
    @ParameterizedTest @ValueSource(ints = {128, 256, 1024, 2048, 4096, 1411456})
    void centredEdgesHaveExactNetherMapping(int width) {
        WorldSize size = new WorldSize(width);
        assertEquals(size.overworldBlocks(), 8 * size.netherBlocks());
        assertEquals(-size.overworldBlocks() / 2 / 8, -size.netherBlocks() / 2);
        assertEquals(0, (size.netherChunks() / 2 * 16) % 16);
        assertEquals(size.overworldChunkCount(), 64 * size.netherChunkCount());
    }

    @ParameterizedTest @ValueSource(ints = {-16, 0, 16, 32, 64, 127, 129, 136, 1411472, Integer.MAX_VALUE})
    void rejectInvalidOrMisalignedBounds(int width) {
        assertThrows(IllegalArgumentException.class, () -> new WorldSize(width));
    }

    @Test void earthDoesNotOverflowAnIntArea() {
        var earth = WorldSize.Preset.EARTH.size;
        assertEquals(1_992_208_039_936L, earth.overworldChunkCount());
        assertTrue(earth.overworldChunkCount() > Integer.MAX_VALUE);
        assertTrue(earth.overworldBlocks() / 2 < 29_999_984);
    }
}
