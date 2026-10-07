package dev.orbis.debug;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasCacheTest {
    @TempDir Path directory;
    private AtlasCache.Data fixture(int cursor) {
        var observed = new DebugSnapshot.Place("minecraft:plains", -12, 72, 17);
        var predicted = new DebugSnapshot.Place("mod:grove", 64, 32, -80);
        var audit = new StructureAudit.Saved(Map.of("minecraft:village_plains", observed), Set.of("minecraft:village_plains"),
                List.of(new StructureAudit.Progress(891, 2, 3, false), new StructureAudit.Progress(9, 0, 0, true)), 71, 1, true,
                new StructureCounts.Saved(Map.of("minecraft:village_plains", Set.of(12L, 13L)),
                        Map.of("minecraft:village_plains", Set.of(13L, 14L)), false), new StructureSearchPolicy(true, 0));
        List<Integer> pixels = new ArrayList<>();
        for (int i = 0; i < 256; i++) pixels.add(0xff123400 | i);
        return new AtlasCache.Data(new AtlasSearch.Saved(cursor, AtlasSearch.MAX, cursor < AtlasSearch.MAX,
                317, AtlasSearch.MAP_MAX, true), false, Map.of(0, 0xff123456, 1, 0xff345678),
                Map.of("minecraft:plains", 5, "mod:grove", 3), Set.of("minecraft:plains"),
                Map.of("minecraft:plains", observed, "mod:grove", predicted), List.of(observed),
                List.of(new DebugSnapshot.Cell(-1, 1, 0xff888888, pixels)), audit);
    }

    @Test void partialSurveyRestoresExactNextQueryTerrainAndEvidence() throws Exception {
        Path file = AtlasCache.fileFor(directory, "tiny-world-A", "minecraft:overworld");
        var original = fixture(1733); // midway through a four-height cell
        AtlasCache.write(file, "generator-and-datapacks-v1", original);
        var restored = AtlasCache.read(file, "generator-and-datapacks-v1");
        assertNotNull(restored);
        assertEquals(1733, restored.search().cursor());
        assertTrue(restored.search().running());
        assertEquals(original.search(), restored.search());
        assertEquals(original.colors(), restored.colors());
        assertEquals(original.hits(), restored.hits());
        assertEquals(original.observed(), restored.observed());
        assertFalse(restored.observed().contains("mod:grove"));
        assertEquals(original.locations(), restored.locations());
        assertEquals(original.audit(), restored.audit());
        assertEquals(original.chunks().getFirst().terrain(), restored.chunks().getFirst().terrain());
        assertEquals(0xff1234ff, restored.chunks().getFirst().colorAt(-1, 31));

    }

    @Test void completedCacheDoesNotTriggerAutomaticFullResample() throws Exception {
        Path file = directory.resolve("complete.gz");
        AtlasCache.write(file, "tiny", fixture(AtlasSearch.MAX));
        var restored = AtlasCache.read(file, "tiny");
        assertNotNull(restored);
        assertFalse(restored.search().running());
        var search = new AtlasSearch(-1024, -1024, 2048, -64, 319, new int[]{63, -48, 32, 128}, true);
        search.restore(restored.search());
        assertFalse(search.save().running());
        assertTrue(search.save().mapping(), "Independent explicit map refinement can continue");
        search.resample(); assertTrue(search.save().running()); assertEquals(0, search.save().cursor());
    }

    @Test void completedQuickPassAndPausedModesDoNotTurnIntoFullSearchesAfterRestore() throws Exception {
        var old = fixture(513);
        var a = old.audit();
        var audit = new StructureAudit.Saved(a.found(), a.observed(), a.tasks(), 71, a.nextTask(), false,
                a.counts(), new StructureSearchPolicy(false, 256));
        for (var progress : List.of(new AtlasSearch.Saved(1024, 1024, false, 256, 256, false),
                new AtlasSearch.Saved(513, 1024, false, 100, 256, false))) {
            Path file = directory.resolve("quick.gz");
            AtlasCache.write(file, "quick", new AtlasCache.Data(progress, old.limited(), old.colors(), old.hits(),
                    old.observed(), old.locations(), old.places(), old.chunks(), audit));
            var restored = AtlasCache.read(file, "quick");
            assertNotNull(restored); assertEquals(progress, restored.search()); assertEquals(audit, restored.audit());
            var search = new AtlasSearch(-8192, -8192, 16384, -64, 319, new int[]{63, -48, 32, 128}, true);
            search.restore(restored.search());
            assertFalse(search.save().running()); assertFalse(search.save().mapping());
            assertFalse(restored.audit().policy().counting()); assertEquals(256, restored.audit().policy().limit());
        }
    }

    @Test void worldsDimensionsAndGeneratorChangesCannotReuseEachOthersData() throws Exception {
        Path first = AtlasCache.fileFor(directory, "world-A", "minecraft:overworld");
        Path otherWorld = AtlasCache.fileFor(directory, "world-B", "minecraft:overworld");
        Path nether = AtlasCache.fileFor(directory, "world-A", "minecraft:the_nether");
        assertNotEquals(first, otherWorld); assertNotEquals(first, nether);
        AtlasCache.write(first, "generator-v1", fixture(100));
        assertNull(AtlasCache.read(otherWorld, "generator-v1"));
        assertNull(AtlasCache.read(nether, "generator-v1"));
        assertNull(AtlasCache.read(first, "changed-datapack-contents"));
        assertNotNull(AtlasCache.read(first, "generator-v1"));
    }

    @Test void damagedTruncatedAndUnknownVersionFilesAreDisposable() throws Exception {
        Path file = directory.resolve("broken.gz");
        AtlasCache.write(file, "v1", fixture(123));
        byte[] valid = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(valid, valid.length - 6));
        assertNull(AtlasCache.read(file, "v1"));
        Files.write(file, new byte[]{1, 2, 3});
        assertNull(AtlasCache.read(file, "v1"));
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(0x4f524241); out.writeInt(AtlasCache.VERSION + 1); out.writeUTF("v1");
        }
        assertNull(AtlasCache.read(file, "v1"));
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(0x4f524241); out.writeInt(1); out.writeUTF("v1");
        }
        assertNull(AtlasCache.read(file, "v1"), "Old roof pixels and first-find-only structure results cannot be reused");
        AtlasCache.write(file, "v2", fixture(456));
        assertEquals(456, AtlasCache.read(file, "v2").search().cursor());
    }

    @Test void oversizedCountsAndFailedWritesDoNotDamageOtherCaches() throws Exception {
        Path file = directory.resolve("counts.gz");
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(0x4f524241); out.writeInt(AtlasCache.VERSION); out.writeUTF("v1");
            out.writeInt(DebugSurvey.TOTAL + 1);
        }
        assertNull(AtlasCache.read(file, "v1"));
        Path valid = directory.resolve("valid.gz");
        AtlasCache.write(valid, "v1", fixture(456));
        Path blocked = directory.resolve("not-a-directory"); Files.writeString(blocked, "keep");
        assertThrows(IOException.class, () -> AtlasCache.write(blocked.resolve("cache.gz"), "v1", fixture(999)));
        assertEquals("keep", Files.readString(blocked));
        assertEquals(456, AtlasCache.read(valid, "v1").search().cursor());
        try (var files = Files.list(directory)) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".tmp"))); }
    }

    @Test void updatedChunkReplacesOldPersistedPixels() throws Exception {
        Path file = directory.resolve("update.gz");
        AtlasCache.write(file, "v1", fixture(456));
        var before = fixture(789);
        var changed = new AtlasCache.Data(before.search(), before.limited(), before.colors(), before.hits(),
                before.observed(), before.locations(), before.places(), List.of(new DebugSnapshot.Cell(-1, 1, 0xffabcdef)), before.audit());
        AtlasCache.write(file, "v1", changed);
        var restored = AtlasCache.read(file, "v1");
        assertEquals(1, restored.chunks().size());
        assertEquals(0xffabcdef, restored.chunks().getFirst().colorAt(-1, 31));
        assertEquals(789, restored.search().cursor());
    }
}
