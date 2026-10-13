package dev.orbis.engine;

import org.jspecify.annotations.Nullable;

/** The torus a light engine works on, shared with the engine subclasses' mixins. */
public interface LightTorus {
    @Nullable Torus orbis$torus();
}
