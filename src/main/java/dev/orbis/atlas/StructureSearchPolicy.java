package dev.orbis.atlas;

/** Full counting is opt-in; each existence pass has a fixed budget of actual layout queries. */
record StructureSearchPolicy(boolean counting, long limit) {
    static final int QUICK_CHECKS = 256;
    StructureSearchPolicy {
        if (limit < 0) throw new IllegalArgumentException("Invalid structure search limit");
    }
    static StructureSearchPolicy quick(long checks) {
        return new StructureSearchPolicy(false, Math.min(checks, Long.MAX_VALUE - QUICK_CHECKS) + QUICK_CHECKS);
    }
    boolean needs(java.util.Set<String> types, java.util.Set<String> found) {
        return counting || !types.stream().allMatch(id -> found.contains(id)
                && (!id.equals("minecraft:stronghold") || found.contains(StructureAudit.PORTAL)));
    }
    boolean allows(long checks, boolean allFound) { return counting || !allFound && checks < limit; }
}
