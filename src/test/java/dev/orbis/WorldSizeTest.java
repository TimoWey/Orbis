package dev.orbis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class WorldSizeTest {
    @ParameterizedTest @ValueSource(ints = {128, 144, 256, 512, 1024, 8832, 76544, 625008, 1411456, 1448160})
    void centredEdgesHaveExactNetherMapping(int width) {
        WorldSize size = new WorldSize(width);
        assertEquals(size.overworldBlocks(), 8 * size.netherBlocks());
        assertEquals(-size.overworldBlocks() / 2 / 8, -size.netherBlocks() / 2);
        assertEquals(0, (size.netherChunks() / 2 * 16) % 16);
        assertEquals(size.overworldChunkCount(), 64 * size.netherChunkCount());
    }

    @ParameterizedTest @ValueSource(ints = {-16, 0, 16, 32, 64, 112, 127, 129, 136, 1448176, Integer.MAX_VALUE})
    void rejectInvalidOrMisalignedBounds(int width) {
        assertThrows(IllegalArgumentException.class, () -> new WorldSize(width));
    }

    @Test void earthDoesNotOverflowAnIntArea() {
        var earth = WorldSize.Preset.EARTH.size;
        assertEquals(2_097_167_385_600L, earth.overworldChunkCount());
        assertTrue(earth.overworldChunkCount() > Integer.MAX_VALUE);
        assertTrue(earth.overworldBlocks() / 2 < 29_999_984);
    }

    @Test void typedSizesGoDownToTheDebugMinimumButPresetsAndSliderStartAtTiny() {
        assertEquals(512, new WorldSize(512).overworldChunks());
        var debug = WorldSize.select(WorldSize.DEBUG_MIN_CHUNKS);
        assertEquals(16, debug.netherChunks());
        assertTrue(debug.debug());
        assertEquals("Debug", debug.label());
        assertTrue(WorldSize.select(1008).debug());
        assertFalse(WorldSize.Preset.TINY.size.debug());
        assertEquals(WorldSize.Preset.TINY.size, WorldSize.fromSlider(0));
        assertThrows(IllegalArgumentException.class, () -> WorldSize.select(112));
        assertThrows(IllegalArgumentException.class, () -> WorldSize.select(136));
        assertThrows(IllegalArgumentException.class, () -> WorldSize.select(1025));
        assertThrows(IllegalArgumentException.class, () -> WorldSize.select(WorldSize.EARTH_CHUNKS + 16));
        assertEquals(WorldSize.Preset.TINY.size, WorldSize.select(WorldSize.MIN_CHUNKS));
    }

    @Test void sliderIsBoundedAlignedAndMonotonic() {
        assertEquals(WorldSize.Preset.TINY.size, WorldSize.fromSlider(-1));
        assertEquals(WorldSize.Preset.EARTH.size, WorldSize.fromSlider(2));
        assertThrows(IllegalArgumentException.class, () -> WorldSize.fromSlider(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> WorldSize.fromSlider(Double.POSITIVE_INFINITY));
        int previous = WorldSize.MIN_CHUNKS;
        for (int i = 0; i <= 10000; i++) {
            int chunks = WorldSize.fromSlider(i / 10000.0).overworldChunks();
            assertTrue(chunks >= previous && chunks <= WorldSize.EARTH_CHUNKS);
            assertEquals(0, chunks % WorldSize.STEP_CHUNKS);
            previous = chunks;
        }
        assertEquals(WorldSize.EARTH_CHUNKS, previous);
    }

    @Test void exactWidthsSurviveSliderSynchronization() {
        for (int chunks = WorldSize.MIN_CHUNKS; chunks <= WorldSize.EARTH_CHUNKS; chunks += 16) {
            var size = WorldSize.select(chunks);
            assertEquals(size, WorldSize.fromSlider(size.sliderPosition()));
        }
        assertEquals("Custom", WorldSize.select(1040).label());
        assertEquals(WorldSize.Preset.NORMAL.size, WorldSize.DEFAULT);
    }

    @Test void areaTargetsAreRoundedToNearestAlignedSide() {
        double[] targets = {20_000, 1_500_000, 100_000_000, 536_870_912};
        var presets = WorldSize.Preset.values();
        for (int i = 0; i < targets.length; i++) {
            var size = presets[i + 1].size;
            double targetBlocks = Math.sqrt(targets[i] * 1_000_000);
            assertTrue(Math.abs(size.overworldBlocks() - targetBlocks) <= 128);
            assertEquals((long) size.overworldBlocks() * size.overworldBlocks(), size.areaSquareMetres());
            assertEquals(size.areaSquareMetres() / 1_000_000.0, size.areaSquareKilometres());
        }
    }
}
