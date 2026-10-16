package dev.orbis.atlas;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.util.SimpleBitStorage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasSavedSurfaceTest {
    static PalettedContainerFactory blocksFactory() {
        var strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        return new PalettedContainerFactory(strategy, Blocks.AIR.defaultBlockState(),
                PalettedContainer.codecRW(BlockState.CODEC, strategy, Blocks.AIR.defaultBlockState()), null, null, null);
    }
    static PalettedContainerFactory fullFactory() {
        var lookup = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.BIOME);
        var ids = new IdMapper<Holder<Biome>>();
        Map<String, Holder<Biome>> names = new HashMap<>();
        lookup.listElements().forEach(b -> { ids.add(b); names.put(b.key().identifier().toString(), b); });
        var strategy = Strategy.createForBiomes(ids);
        var defaultBiome = names.get("minecraft:plains");
        var codec = com.mojang.serialization.Codec.STRING.xmap(names::get, b -> b.unwrapKey().orElseThrow().identifier().toString());
        var blocks = blocksFactory();
        return new PalettedContainerFactory(blocks.blockStatesStrategy(), blocks.defaultBlockState(),
                blocks.blockStatesContainerCodec(), strategy, defaultBiome, PalettedContainer.codecRO(codec, strategy, defaultBiome));
    }
    static CompoundTag tag(PalettedContainerFactory factory, int sectionY, PalettedContainer<BlockState> blocks) {
        var tag = new CompoundTag(); var sections = new ListTag(); var section = new CompoundTag();
        section.putByte("Y", (byte)sectionY);
        section.put("block_states", factory.blockStatesContainerCodec().encodeStart(NbtOps.INSTANCE, blocks).getOrThrow());
        sections.add(section); tag.put("sections", sections);
        return tag;
    }
    @Test void heightsPreserveEveryPixelIncludingTransparentBlocksAndNegativeY() {
        var factory = blocksFactory(); var blocks = factory.createForBlockStates();
        for (int z=0; z<16; z++) for (int x=0; x<16; x++) {
            blocks.set(x, 3, z, ((x+z)%2 == 0 ? Blocks.GOLD_BLOCK : Blocks.DIAMOND_BLOCK).defaultBlockState());
            blocks.set(x, 4, z, Blocks.GLASS.defaultBlockState());
        }
        var tag = tag(factory, -2, blocks);
        var heights = new SimpleBitStorage(9, 256); heights.fill(37); // highest block -28; first air -27; minY -64
        var maps = new CompoundTag(); maps.putLongArray("WORLD_SURFACE", heights.getRaw()); tag.put("Heightmaps", maps);
        var levelHeight = LevelHeightAccessor.create(-64, 384); var position = new ChunkPos(-1,2);
        var reference = AtlasSavedTerrainReference.sample(position, levelHeight, false,
                List.of(new SerializableChunkData.SectionData(-2, new LevelChunkSection(blocks, null), null, null)));
        for (boolean useHeightmap : new boolean[]{true,false}) {
            if (!useHeightmap) tag.remove("Heightmaps");
            assertTrue(reference.sameTerrain(new AtlasSavedSurface(levelHeight, factory, tag).sample(position, false)));
        }
        maps.putLongArray("WORLD_SURFACE", new long[]{1}); tag.put("Heightmaps", maps);
        assertTrue(reference.sameTerrain(new AtlasSavedSurface(levelHeight, factory, tag).sample(position, false)));
    }
    @Test void netherDecodesOnlySliceAndDoesNotDependOnUnrelatedSectionsOrBiomes() {
        var factory = blocksFactory(); var blocks = factory.createForBlockStates();
        blocks.set(5,0,7,Blocks.LAVA.defaultBlockState());
        var tag = tag(factory,2,blocks);
        var invalid = new CompoundTag(); invalid.putByte("Y", (byte)7);
        invalid.put("block_states", new CompoundTag()); tag.getListOrEmpty("sections").add(invalid);
        var tile = new AtlasSavedSurface(LevelHeightAccessor.create(0,256),factory,tag).sample(new ChunkPos(0,0),true);
        assertEquals(0xff000000 | Blocks.LAVA.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).col, tile.colorAt(5,7));
        assertEquals(AtlasTerrain.EMPTY_SLICE_COLOR,tile.colorAt(0,0));
    }
    @Test void savedTilesCarryReliefAndWaterDepthShading() {
        var factory = blocksFactory(); var blocks = factory.createForBlockStates();
        for (int z=0; z<16; z++) for (int x=0; x<16; x++) {
            int top = x < 8 ? (z < 8 ? 4 : 6) : 2; // West: a terrace two blocks up at z=8. East: a basin.
            for (int y=0; y<=top; y++) blocks.set(x, y, z, Blocks.STONE.defaultBlockState());
            if (x >= 8) for (int y=top+1; y<=top+(z < 8 ? 1 : 12); y++) blocks.set(x, Math.min(y, 15), z, Blocks.WATER.defaultBlockState());
        }
        var tile = new AtlasSavedSurface(LevelHeightAccessor.create(0,256),factory,tag(factory,0,blocks)).sample(new ChunkPos(0,0),false);
        int stone = 0xff000000 | Blocks.STONE.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).col;
        int water = 0xff000000 | Blocks.WATER.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).col;
        assertEquals(stone, tile.colorAt(2,3));
        assertEquals(AtlasShading.shade(stone, AtlasShading.HIGH), tile.colorAt(2,8));
        assertEquals(AtlasShading.shade(water, AtlasShading.HIGH), tile.colorAt(10,3));
        assertEquals(AtlasShading.shade(water, AtlasShading.LOW), tile.colorAt(10,12));
    }
}
