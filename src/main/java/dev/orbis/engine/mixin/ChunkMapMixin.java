package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusChunkGenerator;
import dev.orbis.engine.TorusHolder;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow @Final private ServerLevel level;

    /** The level's torus is attached here: the chunk map is the first thing that needs it. */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;"
            + "create(Lnet/minecraft/core/HolderGetter;JLnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;)"
            + "Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState orbis$periodicNoise(HolderGetter<NormalNoise> noises, long seed, NoiseGeneratorSettings settings,
                                            Operation<RandomState> original, @Local(argsOnly = true) ChunkGenerator generator) {
        if (!(generator instanceof TorusChunkGenerator torus)) return original.call(noises, seed, settings);
        ((TorusHolder) level).orbis$setTorus(torus.torus());
        return Tori.creatingRandomState(torus.torus(), () -> original.call(noises, seed, settings));
    }

    @Shadow public abstract net.minecraft.server.level.DistanceManager getDistanceManager();
    @Shadow private TicketStorage ticketStorage;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void orbis$foldTickets(CallbackInfo callback) {
        Torus torus = Tori.of(level);
        if (torus == null) return;
        ((TorusHolder) getDistanceManager()).orbis$setTorus(torus);
        ((TorusHolder) ticketStorage).orbis$setTorus(torus);
        var trackers = (DistanceManagerAccessor) getDistanceManager();
        ((TorusHolder) trackers.orbis$loading()).orbis$setTorus(torus);
        ((TorusHolder) trackers.orbis$simulation()).orbis$setTorus(torus);
        ((TorusHolder) trackers.orbis$naturalSpawn()).orbis$setTorus(torus);
        ((TorusHolder) trackers.orbis$playerTickets()).orbis$setTorus(torus);
    }

    @ModifyVariable(method = "acquireGeneration", at = @At("HEAD"), argsOnly = true)
    private long orbis$foldGeneration(long chunk) { return Fold.chunk(Tori.of(level), chunk); }

    @WrapOperation(method = "getChunkRangeFuture", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;pack(II)J"))
    private long orbis$foldRange(int x, int z, Operation<Long> original) {
        return Fold.chunk(Tori.of(level), (long) original.call(x, z));
    }

    /**
     * A generation task's neighbour cache is indexed around the task's centre. A neighbour across the seam has
     * its folded position outside that index, so its own step gets the same holders re-indexed around it.
     */
    @ModifyVariable(method = "applyStep", at = @At("HEAD"), argsOnly = true)
    private StaticCache2D<GenerationChunkHolder> orbis$recentre(StaticCache2D<GenerationChunkHolder> cache,
                                                                @Local(argsOnly = true) GenerationChunkHolder holder) {
        Torus torus = Tori.of(level);
        ChunkPos pos = holder.getPos();
        if (torus == null || cache.contains(pos.x(), pos.z())) return cache;
        var bounds = (StaticCache2DAccessor) cache;
        int minX = bounds.orbis$minX(), minZ = bounds.orbis$minZ();
        int centreX = minX + bounds.orbis$sizeX() / 2, centreZ = minZ + bounds.orbis$sizeZ() / 2;
        int imageX = torus.nearestChunk(pos.x(), centreX), imageZ = torus.nearestChunk(pos.z(), centreZ);
        int radius = Math.min(Math.min(imageX - minX, minX + bounds.orbis$sizeX() - 1 - imageX),
                Math.min(imageZ - minZ, minZ + bounds.orbis$sizeZ() - 1 - imageZ));
        int shiftX = imageX - pos.x(), shiftZ = imageZ - pos.z();
        return StaticCache2D.create(pos.x(), pos.z(), radius, (x, z) -> cache.get(x + shiftX, z + shiftZ));
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTrackingView;"
            + "of(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/server/level/ChunkTrackingView;"))
    private ChunkTrackingView orbis$torusView(ChunkPos centre, int radius, Operation<ChunkTrackingView> original) {
        ChunkTrackingView view = original.call(centre, radius);
        if (view instanceof TorusHolder holder) holder.orbis$setTorus(Tori.of(level));
        return view;
    }

    /** Spawning and random ticks follow the nearest image of each player. */
    @WrapOperation(method = "playerIsCloseEnoughForSpawning", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ChunkMap;euclideanDistanceSquared(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/phys/Vec3;)D"))
    private double orbis$spawnDistance(ChunkPos chunk, Vec3 player, Operation<Double> original) {
        Vec3 centre = new Vec3(chunk.getMiddleBlockX(), player.y, chunk.getMiddleBlockZ());
        return original.call(chunk, Fold.nearest(Tori.of(level), player, centre));
    }
}
