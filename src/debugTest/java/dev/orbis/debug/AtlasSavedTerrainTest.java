package dev.orbis.debug;

import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasSavedTerrainTest {
    @TempDir Path directory;
    private static LevelChunkSection section() {
        return new LevelChunkSection(new PalettedContainer<BlockState>(Blocks.AIR.defaultBlockState(),
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)), null);
    }
    private static int color(Block block) {
        return 0xff000000 | block.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
    }
    @Test void readsOnlyOccupiedRegionHeaderEntriesIncludingNegativeCoordinates() throws Exception {
        Path region = directory.resolve("r.-2.3.mca");
        try (var file = new RandomAccessFile(region.toFile(), "rw")) {
            file.setLength(8192); file.writeInt(0x201);
            file.seek(1023 * 4); file.writeInt(0x301);
        }
        assertEquals(List.of(new ChunkPos(-64, 96), new ChunkPos(-33, 127)), AtlasSavedTerrain.readHeader(region));
        Files.write(directory.resolve("r.0.0.mca"), new byte[40]);
        assertTrue(AtlasSavedTerrain.readHeader(directory.resolve("r.0.0.mca")).isEmpty());
    }
    @Test void detachedSavedPalettesKeepEverySurfaceBlockWithoutLoadingAWorld() {
        var section = section();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            section.setBlockState(x, 3, z, ((x + z) % 2 == 0 ? Blocks.GOLD_BLOCK : Blocks.DIAMOND_BLOCK).defaultBlockState());
        var tile = AtlasSavedTerrain.sample(new ChunkPos(-1, 2), LevelHeightAccessor.create(-64, 384), false,
                List.of(new SerializableChunkData.SectionData(4, section, null, null)));
        assertEquals(256, tile.terrainSize());
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            assertEquals(color((x + z) % 2 == 0 ? Blocks.GOLD_BLOCK : Blocks.DIAMOND_BLOCK), tile.colorAt(x, z));
    }
    @Test void savedNetherUsesTheSameSliceAndDoesNotPaintItsRoof() {
        var slice = section();
        slice.setBlockState(5, 0, 7, Blocks.LAVA.defaultBlockState());
        var roof = section();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) roof.setBlockState(x, 15, z, Blocks.BEDROCK.defaultBlockState());
        var tile = AtlasSavedTerrain.sample(new ChunkPos(0, 0), LevelHeightAccessor.create(0, 256), true,
                List.of(new SerializableChunkData.SectionData(2, slice, null, null),
                        new SerializableChunkData.SectionData(7, roof, null, null)));
        assertEquals(color(Blocks.LAVA), tile.colorAt(5, 7));
        assertEquals(DebugTerrain.EMPTY_SLICE_COLOR, tile.colorAt(0, 0));
    }
    @Test void optionalImporterCanLoadWithoutDistantHorizons() throws Exception {
        assertNotNull(Class.forName("dev.orbis.debug.AtlasTerrainImport"));
    }
}
