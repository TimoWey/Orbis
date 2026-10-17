package dev.orbis.engine;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyWorldGenTest {
    private static CompoundTag axis(int min, int max) {
        var axis = new CompoundTag();
        axis.putInt("min_chunk", min);
        axis.putInt("max_chunk", max);
        return axis;
    }

    private static CompoundTag legacy(CompoundTag x, CompoundTag z) {
        var wrapping = new CompoundTag();
        wrapping.put("x", x);
        wrapping.put("z", z);
        var generator = new CompoundTag();
        generator.putString("type", LegacyWorldGen.LEGACY_TOROIDAL);
        generator.putString("settings", "minecraft:overworld");
        generator.put("wrapping", wrapping);
        generator.putBoolean("orbis_enabled", true);
        var stem = new CompoundTag();
        stem.putString("type", "minecraft:overworld");
        stem.put("generator", generator);
        return stem;
    }

    private static CompoundTag settings(String dimension, CompoundTag stem) {
        var dimensions = new CompoundTag();
        dimensions.put(dimension, stem);
        var data = new CompoundTag();
        data.put("dimensions", dimensions);
        data.putLong("seed", 42);
        return data;
    }

    private static CompoundTag generator(CompoundTag data, String dimension) {
        return data.getCompoundOrEmpty("dimensions").getCompoundOrEmpty(dimension).getCompoundOrEmpty("generator");
    }

    @Test void alpha11PresetsBecomeOrbisTori() {
        var data = settings("minecraft:overworld", legacy(axis(-38272, 38272), axis(-38272, 38272)));
        data.getCompoundOrEmpty("dimensions").put("minecraft:the_nether", legacy(axis(-4784, 4784), axis(-4784, 4784)));
        data.getCompoundOrEmpty("dimensions").put("minecraft:the_end", legacy(new CompoundTag(), new CompoundTag()));

        assertTrue(LegacyWorldGen.migrate(data));

        var overworld = generator(data, "minecraft:overworld");
        assertEquals("orbis:torus", overworld.getStringOr("type", ""));
        assertEquals(76544, overworld.getIntOr("width_chunks", 0));
        assertEquals("minecraft:overworld", overworld.getStringOr("settings", ""));
        assertFalse(overworld.contains("wrapping"));
        assertFalse(overworld.contains("orbis_enabled"));
        assertEquals(9568, generator(data, "minecraft:the_nether").getIntOr("width_chunks", 0));
        assertEquals("minecraft:noise", generator(data, "minecraft:the_end").getStringOr("type", ""));
        assertEquals(42, data.getLongOr("seed", 0));
        assertFalse(LegacyWorldGen.migrate(data), "a migrated world needs nothing more");
    }

    @Test void currentWorldsAreLeftAlone() {
        var generator = new CompoundTag();
        generator.putString("type", "orbis:torus");
        generator.putInt("width_chunks", 1024);
        var stem = new CompoundTag();
        stem.put("generator", generator);
        var data = settings("minecraft:overworld", stem);
        var before = data.copy();
        assertFalse(LegacyWorldGen.migrate(data));
        assertEquals(before, data);
    }

    @Test void stampsOnOtherGeneratorsAreDropped() {
        var generator = new CompoundTag();
        generator.putString("type", "minecraft:noise");
        generator.put("toroidal_world:wrapping", new CompoundTag());
        var stem = new CompoundTag();
        stem.put("generator", generator);
        var data = settings("minecraft:overworld", stem);
        assertTrue(LegacyWorldGen.migrate(data));
        assertFalse(generator(data, "minecraft:overworld").contains("toroidal_world:wrapping"));
    }

    @Test void shapesThisEngineCannotCarryStopTheLoad() {
        assertThrows(IllegalStateException.class, () -> LegacyWorldGen.migrate(
                settings("minecraft:overworld", legacy(axis(-512, 512), new CompoundTag()))));
        assertThrows(IllegalStateException.class, () -> LegacyWorldGen.migrate(
                settings("minecraft:overworld", legacy(axis(-512, 512), axis(-256, 256)))));
        assertThrows(IllegalStateException.class, () -> LegacyWorldGen.migrate(
                settings("minecraft:overworld", legacy(axis(0, 1024), axis(0, 1024)))));
        var flat = legacy(axis(-512, 512), axis(-512, 512));
        flat.getCompoundOrEmpty("generator").putString("type", "toroidal_world:toroidal_flat");
        assertThrows(IllegalStateException.class, () -> LegacyWorldGen.migrate(settings("minecraft:overworld", flat)));
    }
}
