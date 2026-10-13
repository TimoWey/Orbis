package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Structure search ({@code /locate}, explorer maps, eyes of ender) looks across the seam. Structures are placed on
 * the grid of folded chunk positions, so a grid cell reached past the seam is read at its folded position; the
 * nearest find is the nearest the short way round.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateMixin {
    private static final String RANDOM_SPREAD = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;"
            + "Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)"
            + "Lcom/mojang/datafixers/util/Pair;";
    private static final String RINGS = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/levelgen/structure/placement/ConcentricRingsStructurePlacement;)"
            + "Lcom/mojang/datafixers/util/Pair;";

    @WrapOperation(method = RANDOM_SPREAD, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;getPotentialStructureChunk(JII)Lnet/minecraft/world/level/ChunkPos;"))
    private static ChunkPos orbis$foldedCell(RandomSpreadStructurePlacement placement, long seed, int x, int z,
                                             Operation<ChunkPos> original, @Local(argsOnly = true) LevelReader level) {
        Torus torus = level instanceof Level real ? Tori.of(real) : null;
        return torus == null ? original.call(placement, seed, x, z) : original.call(placement, seed, torus.foldChunk(x), torus.foldChunk(z));
    }

    @WrapOperation(method = "findNearestMapStructure", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private double orbis$shortWay(BlockPos from, Vec3i to, Operation<Double> original, @Local(argsOnly = true) ServerLevel level) {
        return original.call(from, orbis$nearest(Tori.of(level), to, from));
    }

    @WrapOperation(method = RINGS, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private double orbis$ringShortWay(BlockPos.MutableBlockPos ring, Vec3i from, Operation<Double> original, @Local(argsOnly = true) ServerLevel level) {
        Torus torus = Tori.of(level);
        if (torus == null) return original.call(ring, from);
        BlockPos near = Fold.nearest(torus, ring.immutable(), from.getX(), from.getZ());
        return near.distSqr(from);
    }

    private static Vec3i orbis$nearest(Torus torus, Vec3i pos, BlockPos reference) {
        return torus == null ? pos : Fold.nearest(torus, new BlockPos(pos.getX(), pos.getY(), pos.getZ()), reference.getX(), reference.getZ());
    }
}
