package dev.orbis.atlas;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "ORBIS_ARCHIVE_BENCHMARK", matches = "1")
class AtlasArchiveBenchmark {
    @TempDir Path directory;
    @Test void changingViewsOfStoredTerrain() throws Exception {
        var archive = new AtlasTileArchive(directory);
        for (int z = -64; z < 64; z++) for (int x = -64; x < 64; x++) {
            int[] pixels = new int[256];
            Arrays.fill(pixels, 0xff208040 | (x & 15));
            archive.write(new AtlasSnapshot.Cell(x, z, pixels[0], pixels), false);
        }
        archive.close();
        archive = new AtlasTileArchive(directory);
        var data = AtlasFixtures.world(Map.of(), List.of(), Set.of());
        for (boolean globe : new boolean[]{false, true}) {
            double[] times = new double[12];
            for (int frame = 0; frame < times.length; frame++) {
                var view = new AtlasMapRaster.View(512, 512, frame * 4, 0, globe ? 1 : .25,
                        globe, frame * .005, .1, false, false);
                long start = System.nanoTime();
                AtlasTerrainBackdrop.render(archive, data, view, 512, 512, () -> false);
                times[frame] = (System.nanoTime() - start) / 1e6;
            }
            double cold = times[0];
            Arrays.sort(times);
            System.out.printf(Locale.ROOT, "ARCHIVE %s first=%.2f median=%.2f max=%.2f ms%n",
                    globe ? "globe" : "flat", cold, times[6], times[11]);
        }
        archive.close();
    }
}
