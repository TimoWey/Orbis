package dev.orbis.engine.noise;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/** Positional randomness that repeats every {@code period} units on X and Z. */
public record FoldedRandomFactory(PositionalRandomFactory delegate, int period) implements PositionalRandomFactory {
    public static PositionalRandomFactory of(PositionalRandomFactory delegate, int period) {
        return delegate instanceof FoldedRandomFactory ? delegate : new FoldedRandomFactory(delegate, period);
    }

    @Override public RandomSource at(int x, int y, int z) {
        return delegate.at(Math.floorMod(x, period), y, Math.floorMod(z, period));
    }

    @Override public RandomSource fromHashOf(String name) { return delegate.fromHashOf(name); }

    @Override public RandomSource fromSeed(long seed) { return delegate.fromSeed(seed); }

    @Override public void parityConfigString(StringBuilder sb) { delegate.parityConfigString(sb); }
}
