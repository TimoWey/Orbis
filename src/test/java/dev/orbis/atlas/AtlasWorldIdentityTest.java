package dev.orbis.atlas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AtlasWorldIdentityTest {
    @TempDir Path directory;

    @Test void pathAliasesShareIdentityButDifferentSeedsAndSavesDoNot() throws Exception {
        Path save = Files.createDirectory(directory.resolve("world"));
        String identity = AtlasWorldIdentity.resolve(save, 42);
        assertEquals(identity, AtlasWorldIdentity.resolve(save.resolve("..").resolve("world"), 42));
        assertNotEquals(identity, AtlasWorldIdentity.resolve(save, 43));
        assertNotEquals(identity, AtlasWorldIdentity.resolve(Files.createDirectory(directory.resolve("other")), 42));
    }

    @Test void newCreationTimeAtTheSamePathDoesNotReuseCache() throws Exception {
        Path save = Files.createDirectory(directory.resolve("world"));
        String before = AtlasWorldIdentity.resolve(save, 42);
        FileTime old = Files.readAttributes(save, BasicFileAttributes.class).creationTime();
        FileTime replacement = FileTime.fromMillis(old.toMillis() - 60_000);
        try { Files.setAttribute(save, "basic:creationTime", replacement); }
        catch (UnsupportedOperationException unsupported) { assumeTrue(false, "Filesystem cannot change creation time"); }
        assumeTrue(!old.equals(Files.readAttributes(save, BasicFileAttributes.class).creationTime()),
                "Filesystem does not support updating creation time");
        String after = AtlasWorldIdentity.resolve(save, 42);
        assertNotEquals(AtlasCache.fileFor(directory, before, "dimension"), AtlasCache.fileFor(directory, after, "dimension"));
    }

    @Test void missingSaveCannotProduceAPartialIdentity() {
        assertThrows(java.io.IOException.class, () -> AtlasWorldIdentity.resolve(directory.resolve("missing"), 42));
    }
}
