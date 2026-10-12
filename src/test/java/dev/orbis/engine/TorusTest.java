package dev.orbis.engine;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TorusTest {
    private final Torus tiny = Torus.centered(16_384);

    @Test void foldingKeepsEveryColumnOnceAndHandlesNegatives() {
        assertEquals(-8192, tiny.minBlock());
        assertEquals(1024, tiny.widthChunks());
        assertEquals(-8192, tiny.fold(8192));
        assertEquals(8191, tiny.fold(-8193));
        assertEquals(5, tiny.fold(5 + 3 * 16_384));
        assertEquals(-512, tiny.foldChunk(512));
        assertEquals(511, tiny.foldChunk(-513));
        assertEquals(-8192.25 + 16_384, tiny.fold(-8192.25), 1e-9);
        assertTrue(tiny.contains(tiny.fold(Integer.MAX_VALUE)));
        assertEquals(-8192, tiny.fold(-8192 - 1e-13), 0);
    }

    @Test void shortestDeltaCrossesTheSeam() {
        assertEquals(2, tiny.delta(8191, -8191), 1e-9);
        assertEquals(-2, tiny.delta(-8191, 8191), 1e-9);
        assertEquals(10, tiny.delta(0, 10), 1e-9);
        assertEquals(-8192, tiny.delta(0, 8192), 1e-9);
        assertEquals(8192.5, tiny.nearest(-8191.5, 8190), 1e-9);
        assertEquals(1, tiny.deltaChunks(511, -512));
        assertEquals(-1, tiny.deltaChunks(-512, 511));
        assertEquals(512, tiny.nearestChunk(-512, 511));
    }

    @Test void netherIsExactlyOneEighth() {
        for (int chunks : new int[]{1_024, 8_832, 76_544, 625_008, 1_448_160}) {
            var overworld = Torus.centered(chunks * 16);
            var nether = overworld.nether();
            assertEquals(overworld.widthBlocks(), nether.widthBlocks() * 8);
            assertEquals(overworld.minBlock(), nether.minBlock() * 8);
        }
        assertThrows(IllegalArgumentException.class, () -> new Torus(0, 24));
        assertThrows(IllegalStateException.class, () -> new Torus(0, 16 * 9).nether());
    }

    @Test void noiseRepeatsExactlyOncePerWorldWidth() {
        for (int width : new int[]{2_048, 16_384, 141_312}) {
            for (double frequency : new double[]{1 / 1024.0, 1 / 684.412, 0.01, 1.0181268882175227 / 64}) {
                var noise = new PeriodicNoise(new SplittableRandom(42), width, frequency);
                var random = new SplittableRandom(7);
                for (int i = 0; i < 200; i++) {
                    double x = random.nextDouble(-4 * width, 4 * width), y = random.nextDouble(-64, 320);
                    double z = random.nextDouble(-4 * width, 4 * width);
                    double value = noise.sample(x, y, z);
                    assertEquals(value, noise.sample(x + width, y, z), 1e-9);
                    assertEquals(value, noise.sample(x, y, z - 3L * width), 1e-9);
                    assertTrue(Math.abs(value) <= 1.2, "out of range: " + value);
                }
            }
        }
    }

    @Test void noiseIsContinuousAcrossTheSeam() {
        int width = 16_384;
        var noise = new PeriodicNoise(new SplittableRandom(3), width, 1 / 64.0);
        for (double z = 0; z < 2000; z += 37.5) {
            double before = noise.sample(width - 1e-6, 70, z), after = noise.sample(0, 70, z);
            assertEquals(after, before, 1e-5);
            assertEquals(noise.sample(z, 70, 0), noise.sample(z, 70, width - 1e-6), 1e-5);
        }
    }

    @Test void lowestOctavesStillTile() {
        assertEquals(1, PeriodicNoise.cells(2_048, 1 / 8192.0));
        assertEquals(16, PeriodicNoise.cells(16_384, 1 / 1024.0));
        var noise = new PeriodicNoise(new SplittableRandom(1), 2_048, 1 / 8192.0);
        assertEquals(noise.sample(100, 0, 100), noise.sample(100 + 2_048, 0, 100), 1e-12);
    }
}
