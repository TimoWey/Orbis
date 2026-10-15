package dev.orbis.engine.mixin;

/** Shared injection target descriptors. */
final class Targets {
    static final String CREATE_NOISE = "Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction$CompileContext;"
            + "createNoiseSampler(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/levelgen/synth/Noise;";

    private Targets() {}
}
