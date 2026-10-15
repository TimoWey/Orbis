package dev.orbis.gametest;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.level.ChunkPos;

/** The last player ticket decisions and ticket changes on chunks near the world's edges, for a failure message. */
public final class SeamTrace {
    private record Event(long order, String thread, int x, int z, String what) {}

    private static final Deque<Event> EVENTS = new ArrayDeque<>();
    private static long order;

    private SeamTrace() {}

    public static synchronized void record(long chunk, String what) {
        ChunkPos pos = ChunkPos.unpack(chunk);
        if (Math.abs(pos.z()) > 1 || Math.abs(pos.x()) < 60) return;
        EVENTS.addLast(new Event(++order, Thread.currentThread().getName(), pos.x(), pos.z(), what));
        if (EVENTS.size() > 2000) EVENTS.removeFirst();
    }

    /** The last events on one chunk, matched by its column modulo the world's width in chunks, oldest first. */
    public static synchronized String of(ChunkPos pos, int widthChunks) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (var it = EVENTS.descendingIterator(); it.hasNext() && shown < 24; ) {
            Event e = it.next();
            if (Math.floorMod(e.x() - pos.x(), widthChunks) != 0 || e.z() != pos.z()) continue;
            out.insert(0, " | " + e.order() + " " + e.thread() + " [" + e.x() + ", " + e.z() + "] " + e.what());
            shown++;
        }
        return out.toString();
    }
}
