package dev.orbis.atlas;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual pinned DH leaf format, not a locally invented representation. */
class AtlasDistantTerrainTest {
    @SuppressWarnings("unchecked")
    private static <T> T wrapper(Class<T> type, String id, Object wrapped) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> switch (method.getName()) {
            case "getWrappedMcObject" -> wrapped;
            case "getSerialString", "toString" -> id;
            case "hashCode" -> id.hashCode();
            case "equals" -> proxy == args[0];
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
    }
    @Test void fullResolutionDhLeavesImportWithoutAWorldAndMissingColumnsStayUnknown() throws Exception {
        try (var source = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte) 6, -1, 2))) {
            var biome = wrapper(IBiomeWrapper.class, "minecraft:plains", null);
            int gold = source.mapping.addIfNotPresentAndGetId(biome, wrapper(IBlockStateWrapper.class,
                    "minecraft:gold_block", Blocks.GOLD_BLOCK.defaultBlockState()));
            int stone = source.mapping.addIfNotPresentAndGetId(biome, wrapper(IBlockStateWrapper.class,
                    "minecraft:stone", Blocks.STONE.defaultBlockState()));
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                long point = FullDataPointUtil.encode((x + z) % 2 == 0 ? gold : stone, 1, 130, (byte) 0, (byte) 15);
                source.setSingleColumn(new LongArrayList(new long[] {point}), x, z,
                        EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
            }
            var tiles = new ArrayList<AtlasSnapshot.Cell>();
            AtlasDistantTerrain.sample(source, -64, 319, false, tiles::add);
            assertEquals(1, tiles.size()); // Other 15 chunks have missing columns, not fabricated colors.
            var tile = tiles.getFirst();
            assertEquals(-4, tile.x()); assertEquals(8, tile.z());
            assertEquals(256, tile.terrainSize());
            int expectedGold = 0xff000000 | Blocks.GOLD_BLOCK.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            int expectedStone = 0xff000000 | Blocks.STONE.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                assertEquals((x + z) % 2 == 0 ? expectedGold : expectedStone, tile.colorAt(x, z));
            source.columnGenerationSteps.set(FullDataSourceV2.relativePosToIndex(0, 0), EDhApiWorldGenerationStep.DOWN_SAMPLED.value);
            tiles.clear();
            AtlasDistantTerrain.sample(source, -64, 319, false, tiles::add);
            assertTrue(tiles.isEmpty()); // An expanded coarse parent must not pretend to contain original blocks.
        }
    }
    @Test void dhNetherSliceIncludesMinimumHeightOffsetAndExcludesRoof() throws Exception {
        try (var source = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte) 6, 0, 0))) {
            var biome = wrapper(IBiomeWrapper.class, "minecraft:nether_wastes", null);
            int roof = source.mapping.addIfNotPresentAndGetId(biome, wrapper(IBlockStateWrapper.class,
                    "minecraft:bedrock", Blocks.BEDROCK.defaultBlockState()));
            int lava = source.mapping.addIfNotPresentAndGetId(biome, wrapper(IBlockStateWrapper.class,
                    "minecraft:lava", Blocks.LAVA.defaultBlockState()));
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                source.setSingleColumn(new LongArrayList(new long[] {
                        FullDataPointUtil.encode(roof, 1, 191, (byte) 0, (byte) 0),
                        FullDataPointUtil.encode(lava, 1, 96, (byte) 0, (byte) 0)}), x, z,
                        EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
            var tiles = new ArrayList<AtlasSnapshot.Cell>();
            AtlasDistantTerrain.sample(source, -64, 255, true, tiles::add);
            assertEquals(1, tiles.size());
            int expected = 0xff000000 | Blocks.LAVA.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            assertEquals(expected, tiles.getFirst().terrainPixel(0));
        }
    }
    @Test void coarseDhParentsAreNeverPresentedAsDetailedTerrain() {
        try (var source = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte) 7, 0, 0))) {
            var tiles = new ArrayList<AtlasSnapshot.Cell>();
            AtlasDistantTerrain.sample(source, -64, 319, false, tiles::add);
            assertTrue(tiles.isEmpty());
        }
    }
}
