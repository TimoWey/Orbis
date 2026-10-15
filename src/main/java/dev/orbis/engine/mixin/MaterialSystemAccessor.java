package dev.orbis.engine.mixin;

import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MaterialSystem.class)
public interface MaterialSystemAccessor {
    @Accessor("clayBandsOffsetNoise") Noise orbis$clayBandsOffsetNoise();
    @Mutable @Accessor("clayBandsOffsetNoise") void orbis$setClayBandsOffsetNoise(Noise noise);
    @Accessor("surfaceNoise") Noise orbis$surfaceNoise();
    @Mutable @Accessor("surfaceNoise") void orbis$setSurfaceNoise(Noise noise);
    @Accessor("surfaceSecondaryNoise") Noise orbis$surfaceSecondaryNoise();
    @Mutable @Accessor("surfaceSecondaryNoise") void orbis$setSurfaceSecondaryNoise(Noise noise);
    @Accessor("badlandsSurfaceNoise") Noise orbis$badlandsSurfaceNoise();
    @Mutable @Accessor("badlandsSurfaceNoise") void orbis$setBadlandsSurfaceNoise(Noise noise);
    @Accessor("icebergSurfaceNoise") Noise orbis$icebergSurfaceNoise();
    @Mutable @Accessor("icebergSurfaceNoise") void orbis$setIcebergSurfaceNoise(Noise noise);
    @Accessor("badlandsPillarNoise") Noise orbis$badlandsPillarNoise();
    @Mutable @Accessor("badlandsPillarNoise") void orbis$setBadlandsPillarNoise(Noise noise);
    @Accessor("badlandsPillarRoofNoise") Noise orbis$badlandsPillarRoofNoise();
    @Mutable @Accessor("badlandsPillarRoofNoise") void orbis$setBadlandsPillarRoofNoise(Noise noise);
    @Accessor("icebergPillarNoise") Noise orbis$icebergPillarNoise();
    @Mutable @Accessor("icebergPillarNoise") void orbis$setIcebergPillarNoise(Noise noise);
    @Accessor("icebergPillarRoofNoise") Noise orbis$icebergPillarRoofNoise();
    @Mutable @Accessor("icebergPillarRoofNoise") void orbis$setIcebergPillarRoofNoise(Noise noise);
    @Accessor("noiseRandom") PositionalRandomFactory orbis$noiseRandom();
    @Mutable @Accessor("noiseRandom") void orbis$setNoiseRandom(PositionalRandomFactory random);
}
