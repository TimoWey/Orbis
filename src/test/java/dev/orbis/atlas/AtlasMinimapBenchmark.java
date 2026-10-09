package dev.orbis.atlas;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Reproducible camera trace; no GPU, world generation or wall-time assertions. */
@EnabledIfEnvironmentVariable(named = "ORBIS_MINIMAP_BENCHMARK", matches = "1")
class AtlasMinimapBenchmark {
    @TempDir Path directory;
    @Test void compareScreenReprojectionWithPaddedTextureScrolling() throws Exception {
        var archive = new AtlasTileArchive(directory);
        for (int z = -24; z < 24; z++) for (int x = -24; x < 24; x++) {
            int[] pixels = new int[256];
            for (int i = 0; i < 256; i++) pixels[i] = 0xff000000 | (x & 255) << 16 | (z & 255) << 8 | i;
            archive.write(new AtlasSnapshot.Cell(x, z, pixels[0], pixels), false);
        }
        archive.close(); archive = new AtlasTileArchive(directory);
        var data = AtlasFixtures.archive(AtlasFixtures.world(Map.of(), List.of(), Set.of()), archive);
        // Warm archive/JIT for both paths. Screen output is 512px over 256 world blocks.
        for (int i = 0; i < 30; i++) {
            var view = new AtlasMapRaster.View(256, 256, i, 0, 1, false, 0, 0, false, false);
            AtlasTerrainBackdrop.render(archive, data, view, 512, 512, () -> false);
        }
        for (int round = 0; round < 3; round++) {
            var jobs = new ArrayDeque<Runnable>();
            var raster = new AtlasMapRaster(512, 512);
            try (var cache = new AtlasMinimapCache(jobs::add)) {
                double oldCenter = Double.NaN;
                long oldCpu = 0, updateCpu = 0, workerCpu = 0;
                int oldRenders = 0, uploads = 0, prepared = 0;
                AtlasMinimapCache.Frame previous = null;
                for (int frame = 0; frame < 1024; frame++) {
                    double x = frame < 512 ? -128 + frame * .5 : 128 - (frame - 512) * .5;
                    double quantized = Math.floor(x / 4) * 4;
                    if (quantized != oldCenter) {
                        var view = new AtlasMapRaster.View(256, 256, quantized, 0, 1, false, 0, 0, false, false);
                        long start = System.nanoTime();
                        raster.render(data, view, AtlasTerrainBackdrop.render(archive, data, view, 512, 512, () -> false));
                        oldCpu += System.nanoTime() - start; oldRenders++; oldCenter = quantized;
                    }
                    long start = System.nanoTime();
                    var ready = cache.update(data, x, 0, 256);
                    updateCpu += System.nanoTime() - start;
                    if (ready != null && ready != previous) { uploads++; previous = ready; }
                    start = System.nanoTime();
                    while (!jobs.isEmpty()) { jobs.remove().run(); prepared++; }
                    workerCpu += System.nanoTime() - start;
                }
                assertTrue(uploads < oldRenders / 4);
                System.out.printf(Locale.ROOT,
                        "MINIMAP round=%d frames=1024 oldProjection=%.3fms newClientUpdate=%.3fms newWorker=%.3fms oldImages=%d newImages=%d prepared=%d%n",
                        round, oldCpu / 1e6, updateCpu / 1e6, workerCpu / 1e6, oldRenders, uploads, prepared);
            }
        }
        archive.close();
    }
}
