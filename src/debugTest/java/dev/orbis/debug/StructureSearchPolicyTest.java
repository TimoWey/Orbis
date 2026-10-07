package dev.orbis.debug;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureSearchPolicyTest {
    @Test void eachExistencePassIsBoundedAndStopsWhenEverythingIsFound() {
        var first = StructureSearchPolicy.quick(0);
        assertTrue(first.allows(255, false)); assertFalse(first.allows(256, false));
        assertFalse(first.allows(1, true));
        var next = StructureSearchPolicy.quick(256);
        assertTrue(next.allows(511, false)); assertFalse(next.allows(512, false));
    }
    @Test void fullCountingRequiresExplicitModeAndContinuesAfterFirstFind() {
        var quick = StructureSearchPolicy.quick(0);
        var full = new StructureSearchPolicy(true, 0);
        assertFalse(quick.allows(1000000, false)); assertTrue(full.allows(1000000, true));
        assertFalse(quick.needs(Set.of("village"), Set.of("village")));
        assertTrue(full.needs(Set.of("village"), Set.of("village")));
    }
    @Test void foundStrongholdMustNotSkipSearchForMissingPortalRoom() {
        var quick = StructureSearchPolicy.quick(0);
        assertTrue(quick.needs(Set.of("minecraft:stronghold"), Set.of("minecraft:stronghold")));
        assertFalse(quick.needs(Set.of("minecraft:stronghold"), Set.of("minecraft:stronghold", StructureAudit.PORTAL)));
        assertTrue(quick.needs(Set.of("village", "outpost"), Set.of("village")));
    }
}
