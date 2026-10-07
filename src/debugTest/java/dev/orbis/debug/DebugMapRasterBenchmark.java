package dev.orbis.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** CPU-only drag/rotation workload. Excludes GPU upload and makes no FPS assertions. */
@EnabledIfEnvironmentVariable(named = "ORBIS_RASTER_BENCHMARK", matches = "1")
class DebugMapRasterBenchmark {
    @Test void reportFullHistoryFlatDragAndGlobeRotation() {
        Map<Integer, Integer> preview = new HashMap<>();
        for (int i = 0; i < DebugSurvey.GRID * DebugSurvey.GRID; i++)
            preview.put(i, 0xff204060 | (i & 31));
        List<DebugSnapshot.Cell> chunks = new ArrayList<>();
        for (int z = -32; z < 32; z++) for (int x = -64; x < 64; x++) {
            int[] pixels = new int[256];
            for (int i = 0; i < pixels.length; i++) pixels[i] = 0xff408020 | ((x + z + i) & 31);
            chunks.add(new DebugSnapshot.Cell(x, z, 0xff408020, pixels));
        }
        var data = AtlasFixtures.world(Map.copyOf(preview), List.copyOf(chunks), Set.of());
        for (boolean globe : new boolean[] {false, true}) {
            var raster = new DebugMapRaster(768, 512);
            for (int frame = 0; frame < 12; frame++) raster.render(data, view(globe, frame));
            long[] times = new long[40];
            long checksum = 0;
            for (int frame = 0; frame < times.length; frame++) {
                long start = System.nanoTime();
                int[] image = raster.render(data, view(globe, frame));
                times[frame] = System.nanoTime() - start;
                checksum += image[256 * 768 + 384];
            }
            assertNotEquals(0, checksum);
            Arrays.sort(times);
            System.out.printf(Locale.ROOT,
                    "RASTER_BENCH mode=%s pixels=768x512 chunks=%d frames=%d median_cpu_ms=%.3f p95_cpu_ms=%.3f checksum=%d%n",
                    globe ? "globe-rotation" : "flat-drag", chunks.size(), times.length,
                    times[times.length / 2] / 1e6, times[(int) (times.length * .95) - 1] / 1e6, checksum);
        }
    }
    private static DebugMapRaster.View view(boolean globe, int frame) {
        return new DebugMapRaster.View(384, 256, frame * 4, frame * 2, globe ? 1 : .5,
                globe, frame * .02, frame * .01, false, false);
    }
}
