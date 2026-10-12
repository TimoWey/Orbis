package dev.orbis.atlas;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasResourceFingerprintTest {
    private static AtlasCache.ResourceContent resource(String contents) {
        return () -> new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
    }

    @Test void hashesEffectiveContentsIndependentlyOfEnumerationOrder() throws Exception {
        var first = new LinkedHashMap<String, AtlasCache.ResourceContent>();
        first.put("pack:a", resource("one")); first.put("pack:b", resource("two"));
        var reversed = new LinkedHashMap<String, AtlasCache.ResourceContent>();
        reversed.put("pack:b", resource("two")); reversed.put("pack:a", resource("one"));
        String fingerprint = AtlasCache.hashResources(first);
        assertEquals(fingerprint, AtlasCache.hashResources(reversed));
        reversed.put("pack:a", resource("edited inside the same pack"));
        assertNotEquals(fingerprint, AtlasCache.hashResources(reversed));
        assertNotEquals(AtlasCache.hashResources(Map.of("a", resource("bc"))),
                AtlasCache.hashResources(Map.of("ab", resource("c"))), "Resource names and contents need unambiguous framing");
    }

    @Test void dimensionsShareOneCaptureAndReadUntilReload() throws Exception {
        var cache = new AtlasCache.ResourceFingerprint();
        var generation = new Object();
        var captures = new AtomicInteger();
        var reads = new AtomicInteger();
        java.util.function.Supplier<Map<String, AtlasCache.ResourceContent>> capture = () -> {
            captures.incrementAndGet();
            return Map.of("pack:a", () -> { reads.incrementAndGet(); return resource("one").open(); });
        };
        var first = cache.get(generation, capture);
        assertSame(first, cache.get(generation, capture));
        first.get(5, TimeUnit.SECONDS);
        assertEquals(1, captures.get()); assertEquals(1, reads.get());
        cache.invalidate();
        cache.get(generation, capture).get(5, TimeUnit.SECONDS);
        assertEquals(2, captures.get()); assertEquals(2, reads.get());
    }

    @Test void reloadRejectsAnOlderFingerprintStillBeingRead() throws Exception {
        var cache = new AtlasCache.ResourceFingerprint();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pending = cache.get(new Object(), () -> Map.of("pack:a", () -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timed out");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            return resource("old").open();
        }));
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            cache.invalidate();
        } finally { release.countDown(); }
        assertThrows(CompletionException.class, pending::join);
        assertEquals(AtlasCache.hashResources(Map.of("pack:a", resource("new"))),
                cache.get(new Object(), () -> Map.of("pack:a", resource("new"))).get(5, TimeUnit.SECONDS));
    }
}
