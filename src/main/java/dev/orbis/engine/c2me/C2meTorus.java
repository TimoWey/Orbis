package dev.orbis.engine.c2me;

import dev.orbis.engine.Torus;
import org.jspecify.annotations.Nullable;

/** C2ME's chunk scheduler, once Orbis knows which world it schedules for. */
public interface C2meTorus {
    @Nullable Torus orbis$torus();
}
