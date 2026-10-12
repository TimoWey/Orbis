package dev.orbis.engine.noise;

import dev.orbis.Orbis;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.GradientNoise;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NoiseStack;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.synth.SmearedPerlinNoise;

/**
 * Turns vanilla noise into noise whose lattice repeats once per world width.
 *
 * <p>Each input axis of a noise gets the period it has in that noise's own input space ({@code 0} for an
 * axis that is not horizontal). A Perlin octave rounds that period to a whole number of lattice cells
 * (at least one) and wraps its lattice indices, keeping vanilla permutations, offsets and gradients.
 */
public final class PeriodicNoises {
    private PeriodicNoises() {}

    /**
     * @param periodA input-space period of the noise's first axis, or 0
     * @param periodB input-space period of the second axis, or 0
     * @param periodC input-space period of the third axis, or 0
     */
    public static Noise periodic(Noise source, double periodA, double periodB, double periodC) {
        if (periodA == 0 && periodB == 0 && periodC == 0) return source;
        if (source instanceof NoiseStack stack) {
            var layers = new Layer[stack.layers.length];
            for (int i = 0; i < layers.length; i++) {
                var layer = stack.layers[i];
                double f = layer.frequency();
                layers[i] = new Layer(periodic(layer.noise(), periodA * f, periodB * f, periodC * f), f, layer.amplitude());
            }
            return new Stack(layers, stack.range());
        }
        if (source instanceof SmearedPerlinNoise smeared) return new Perlin(smeared, smeared.fudgeYScale, periodA, periodB, periodC);
        if (source.getClass() == PerlinNoise.class) return new Perlin((PerlinNoise) source, 0, periodA, periodB, periodC);
        if (source instanceof Periodic) return source;
        Orbis.LOGGER.warn("Orbis engine: {} cannot be made periodic; it will show a seam", source.getClass().getName());
        return source;
    }

    /** Offset noises sample block coordinates times 0.25; {@code shift_b} samples (z, x, 0). */
    public static Noise forShift(DensityFunction.CompileContext context, Noise noise, boolean transposed) {
        Torus torus = Tori.of(context);
        if (torus == null) return noise;
        double period = torus.widthBlocks() * ShiftNoiseFunction.COORDINATE_FACTOR;
        return transposed ? periodic(noise, period, period, 0) : periodic(noise, period, 0, period);
    }

    /** Marker for noise that already repeats. */
    public interface Periodic extends Noise {}

    private record Layer(Noise noise, double frequency, float amplitude) {}

    private record Stack(Layer[] layers, Interval range) implements Periodic {
        @Override public float get(double x, double y, double z) {
            float value = 0;
            for (Layer layer : layers) {
                double f = layer.frequency;
                value += layer.amplitude * layer.noise.get(x * f, y * f, z * f);
            }
            return value;
        }

        @Override public float get(double x, double y) {
            float value = 0;
            for (Layer layer : layers) {
                double f = layer.frequency;
                value += layer.amplitude * layer.noise.get(x * f, y * f);
            }
            return value;
        }

        @Override public void addToVolume(net.minecraft.world.level.levelgen.densityfunction.DensityBuffer buffer,
                                          net.minecraft.world.level.levelgen.densityfunction.DensityVolume volume,
                                          double xzScale, double yScale, float amplitude) {
            for (Layer layer : layers) {
                double f = layer.frequency;
                layer.noise.addToVolume(buffer, volume, xzScale * f, yScale * f, amplitude * layer.amplitude);
            }
        }
    }

    /** One (optionally smeared) Perlin octave on a lattice that wraps on the periodic axes. */
    static final class Perlin implements Periodic {
        private final byte[] perms;
        private final double offsetX, offsetY, offsetZ, fudgeYScale;
        private final Axis a, b, c;
        private final Interval range;

        Perlin(PerlinNoise source, double fudgeYScale, double periodA, double periodB, double periodC) {
            perms = source.perms;
            offsetX = source.offsetX; offsetY = source.offsetY; offsetZ = source.offsetZ;
            this.fudgeYScale = fudgeYScale;
            a = Axis.of(periodA); b = Axis.of(periodB); c = Axis.of(periodC);
            range = source.range();
        }

        @Override public Interval range() { return range; }

        @Override public float get(double x, double y) { return get(x, 0, y); }

        @Override public float get(double x, double y, double z) {
            double u = a.map(x) + offsetX, v = b.map(y) + offsetY, w = c.map(z) + offsetZ;
            int fu = Mth.floor(u), fv = Mth.floor(v), fw = Mth.floor(w);
            float ru = (float) (u - fu), rw = (float) (w - fw);
            double rv = v - fv;
            float dotV = (float) rv;
            // Smeared octaves (old blended terrain noise) flatten Y below a moving floor, as vanilla does.
            if (fudgeYScale != 0) dotV = (float) (rv - fudge(y, rv));
            int a0 = a.wrap(fu), a1 = a.wrap(fu + 1), b0 = b.wrap(fv), b1 = b.wrap(fv + 1);
            int c0 = c.wrap(fw), c1 = c.wrap(fw + 1);
            int x0 = permute(a0), x1 = permute(a1);
            int xy00 = permute(x0 + b0), xy01 = permute(x0 + b1), xy10 = permute(x1 + b0), xy11 = permute(x1 + b1);
            float d000 = grad(permute(xy00 + c0), ru, dotV, rw);
            float d100 = grad(permute(xy10 + c0), ru - 1, dotV, rw);
            float d010 = grad(permute(xy01 + c0), ru, dotV - 1, rw);
            float d110 = grad(permute(xy11 + c0), ru - 1, dotV - 1, rw);
            float d001 = grad(permute(xy00 + c1), ru, dotV, rw - 1);
            float d101 = grad(permute(xy10 + c1), ru - 1, dotV, rw - 1);
            float d011 = grad(permute(xy01 + c1), ru, dotV - 1, rw - 1);
            float d111 = grad(permute(xy11 + c1), ru - 1, dotV - 1, rw - 1);
            return Mth.lerp3(Mth.smoothstep(ru), Mth.smoothstep((float) rv), Mth.smoothstep(rw),
                    d000, d100, d010, d110, d001, d101, d011, d111);
        }

        private double fudge(double originalY, double relativeY) {
            double limit = originalY >= 0 && originalY < relativeY ? originalY : relativeY;
            return Mth.floor(limit / fudgeYScale + 1.0E-7F) * fudgeYScale;
        }

        private int permute(int i) { return perms[i & 0xFF] & 0xFF; }

        private static float grad(int hash, float x, float y, float z) {
            return switch (hash & 15) {
                case 0, 12 -> x + y;
                case 1, 14 -> -x + y;
                case 2 -> x - y;
                case 3 -> -x - y;
                case 4 -> x + z;
                case 5 -> -x + z;
                case 6 -> x - z;
                case 7 -> -x - z;
                case 8 -> y + z;
                case 9, 13 -> -y + z;
                case 10 -> y - z;
                default -> -y - z;
            };
        }
    }

    /** Maps an input coordinate onto a lattice of {@code cells} cells per period; 0 cells = not periodic. */
    record Axis(double period, int cells, double scale) {
        static final Axis FREE = new Axis(0, 0, 1);

        static Axis of(double period) {
            if (!(period > 0)) return FREE;
            int cells = (int) Math.max(1, Math.round(period));
            return new Axis(period, cells, cells / period);
        }

        /** Fold first so the scaled value stays small even for Earth-sized worlds. */
        double map(double value) {
            if (cells == 0) return GradientNoise.wrap(value);
            return (value - Math.floor(value / period) * period) * scale;
        }

        int wrap(int index) { return cells == 0 ? index : Math.floorMod(index, cells); }
    }
}
