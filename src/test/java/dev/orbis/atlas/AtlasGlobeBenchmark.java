package dev.orbis.atlas;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Globe raster cost per picture size; no wall-time assertions. */
@EnabledIfEnvironmentVariable(named = "ORBIS_GLOBE_BENCHMARK", matches = "1")
class AtlasGlobeBenchmark {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void archivedGlobeCostBySize() throws Exception {
        var archive = new AtlasTileArchive(directory);
        for (int z = -64; z < 64; z++) for (int x = -64; x < 64; x++) {
            int[] pixels = new int[256];
            for (int i = 0; i < 256; i++) pixels[i] = 0xff000000 | (x & 255) << 16 | (z & 255) << 8 | i;
            archive.write(new AtlasSnapshot.Cell(x, z, pixels[0], pixels), false);
        }
        var data = AtlasFixtures.archive(AtlasFixtures.world(Map.of(), List.of(), Set.of()), archive);
        for (int size : new int[] {768, 1440, 768, 1440}) {
            AtlasTerrainBackdrop.render(archive, data, new AtlasMapRaster.View(400, 400, 0, 0, 4, true, 0, 0, false, false), size, size, () -> false);
            long start = System.nanoTime();
            for (int i = 0; i < 10; i++) AtlasTerrainBackdrop.render(archive, data,
                    new AtlasMapRaster.View(400, 400, 0, 0, 4, true, i * .01, i * .005, false, false), size, size, () -> false);
            System.out.printf(Locale.ROOT, "BACKDROP size=%d %.2f ms/picture%n", size, (System.nanoTime() - start) / 10e6);
        }
        archive.close();
    }
    @Test void globeCostBySize() {
        var chunks = new ArrayList<AtlasSnapshot.Cell>();
        for (int z = -64; z < 64; z++) for (int x = -64; x < 64; x++) {
            int[] pixels = new int[256];
            for (int i = 0; i < 256; i++) pixels[i] = 0xff000000 | (x & 255) << 16 | (z & 255) << 8 | i;
            chunks.add(new AtlasSnapshot.Cell(x, z, pixels[0], pixels));
        }
        var data = AtlasFixtures.world(Map.of(), chunks, Set.of());
        for (int size : new int[] {768, 1024, 1440, 768, 1024, 1440}) {
            var raster = new AtlasMapRaster(size, size, true);
            var view = new AtlasMapRaster.View(400, 400, 0, 0, 4, true, 0, 0, false, false);
            raster.render(data, view);
            long start = System.nanoTime();
            for (int i = 0; i < 20; i++) raster.render(data, new AtlasMapRaster.View(400, 400, 0, 0, 4, true, i * .01, i * .005, false, false));
            System.out.printf(Locale.ROOT, "GLOBE size=%d %.2f ms/picture%n", size, (System.nanoTime() - start) / 20e6);
        }
    }
}
