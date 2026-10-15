package dev.orbis.engine;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.jspecify.annotations.Nullable;

/** Where the engine finds the torus of a level or of a noise compilation. */
public final class Tori {
    private static final ThreadLocal<Torus> PENDING = new ThreadLocal<>();
    private static final Map<DensityFunction.CompileContext, Torus> COMPILERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Tori() {}

    public static void register() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath("orbis", "torus"),
                TorusChunkGenerator.CODEC);
    }

    /** The torus of an Orbis-engine level; {@code null} everywhere else, including old-engine worlds. */
    public static @Nullable Torus of(@Nullable Level level) {
        return level instanceof TorusHolder holder ? holder.orbis$torus() : null;
    }

    /** Runs {@code create} so that the random state it builds samples periodic noise. */
    public static <T> T creatingRandomState(Torus torus, Supplier<T> create) {
        Torus previous = PENDING.get();
        PENDING.set(torus);
        try {
            return create.get();
        } finally {
            PENDING.set(previous);
        }
    }

    public static @Nullable Torus pending() { return PENDING.get(); }

    public static void bind(DensityFunction.CompileContext context, Torus torus) { COMPILERS.put(context, torus); }

    public static @Nullable Torus of(DensityFunction.CompileContext context) { return COMPILERS.get(context); }
}
