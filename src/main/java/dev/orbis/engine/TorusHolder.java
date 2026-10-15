package dev.orbis.engine;

import org.jspecify.annotations.Nullable;

/** Mixed into objects that need to know the torus they act on; {@code null} means vanilla behaviour. */
public interface TorusHolder {
    @Nullable Torus orbis$torus();

    void orbis$setTorus(@Nullable Torus torus);
}
