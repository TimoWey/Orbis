package dev.orbis.atlas;

import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real save comparison. Timings exclude disk reads and initial registry/JIT setup. */
@EnabledIfEnvironmentVariable(named="ORBIS_SAVED_BENCHMARK", matches=".+")
class AtlasSavedSurfaceBenchmark {
    @Test void compareOriginalDecoderWithSurfaceOnlyDecoder() throws Exception {
        var factory = AtlasSavedSurfaceTest.fullFactory();
        for (boolean nether : new boolean[]{false,true}) {
            String dimension = nether ? "the_nether" : "overworld";
            Path regions = Path.of(System.getenv("ORBIS_SAVED_BENCHMARK"), "dimensions", "minecraft", dimension, "region");
            var height = LevelHeightAccessor.create(nether ? 0 : -64, nether ? 256 : 384);
            var tags = new ArrayList<CompoundTag>();
            try (var storage = new RegionFileStorage(new RegionStorageInfo("benchmark", nether ? Level.NETHER : Level.OVERWORLD,"chunk"),regions,false);
                    var files = Files.list(regions)) {
                for (var path : files.sorted().toList()) {
                    for (var pos : AtlasSavedTerrain.readHeader(path)) {
                        var tag = storage.read(pos);
                        if (tag != null && SerializableChunkData.getChunkStatusFromTag(tag).isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.FULL)) tags.add(tag);
                        if (tags.size() >= 512) break;
                    }
                    if (tags.size() >= 512) break;
                }
            }
            assertTrue(tags.size() >= 100);
            for (var tag : tags) {
                var old = SerializableChunkData.parse(height,factory,tag);
                var reference = AtlasSavedTerrainReference.sample(old.chunkPos(),height,nether,old.sectionData());
                var optimized = new AtlasSavedSurface(height,factory,tag).sample(old.chunkPos(),nether);
                assertTrue(reference.sameTerrain(optimized), "Changed pixels in " + dimension + " " + old.chunkPos());
            }
            for (int round=0; round<4; round++) {
                // Alternate order to reduce warm-up/GC bias. Round zero is warm-up.
                double[] millis = new double[2]; long checksum = 0;
                for (int order=0; order<2; order++) {
                    int mode = (round+order)%2; long start = System.nanoTime();
                    for (var tag : tags) {
                        dev.orbis.atlas.AtlasSnapshot.Cell cell;
                        if (mode == 0) {
                            var data = SerializableChunkData.parse(height,factory,tag);
                            cell = AtlasSavedTerrainReference.sample(data.chunkPos(),height,nether,data.sectionData());
                        } else cell = new AtlasSavedSurface(height,factory,tag).sample(new ChunkPos(tag.getIntOr("xPos",0),tag.getIntOr("zPos",0)),nether);
                        checksum += cell.terrainPixel(0);
                    }
                    millis[mode] = (System.nanoTime()-start)/1e6;
                }
                System.out.printf(Locale.ROOT,"SAVED_SURFACE dimension=%s chunks=%d round=%d old=%.2fms new=%.2fms speedup=%.2fx checksum=%d%n",dimension,tags.size(),round,millis[0],millis[1],millis[0]/millis[1],checksum);
            }
        }
    }
}
