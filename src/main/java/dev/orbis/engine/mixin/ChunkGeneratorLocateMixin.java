package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Structure search ({@code /locate}, explorer maps, eyes of ender) looks across the seam. Structures are placed on
 * the grid of folded chunk positions, so the search visits the grid cells at their folded positions; the nearest
 * find is the nearest the short way round.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateMixin {
    private static final String RANDOM_SPREAD = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/world/level/LevelReader;"
            + "Lnet/minecraft/world/level/StructureManager;IIIZJLnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;)"
            + "Lcom/mojang/datafixers/util/Pair;";
    private static final String RINGS = "getNearestGeneratedStructure(Ljava/util/Set;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/levelgen/structure/placement/ConcentricRingsStructurePlacement;)"
            + "Lcom/mojang/datafixers/util/Pair;";

    /**
     * One ring of the search for a spread-out structure. Structures generate where a folded chunk is its own grid
     * cell's pick, and the cells next to the seam are cut short, so stepping across it by whole cells would skip
     * some. Instead every cell within reach is visited at its folded position, and a pick counts for the ring its
     * short-way distance falls in; within the ring the nearest one is tried first.
     */
    @WrapMethod(method = RANDOM_SPREAD)
    private static @Nullable Pair<BlockPos, Holder<Structure>> orbis$searchFoldedCells(Set<Holder<Structure>> structures,
            LevelReader level, StructureManager structureManager, int originX, int originZ, int radius, boolean createReference,
            long seed, RandomSpreadStructurePlacement config, Operation<Pair<BlockPos, Holder<Structure>>> original) {
        Torus torus = level instanceof Level real ? Tori.of(real) : null;
        if (torus == null) return original.call(structures, level, structureManager, originX, originZ, radius, createReference, seed, config);
        int spacing = config.spacing();
        int[] cellsX = orbis$cellsInReach(torus, originX, radius, spacing), cellsZ = orbis$cellsInReach(torus, originZ, radius, spacing);
        List<ChunkPos> picks = new ArrayList<>();
        for (int cellX : cellsX) {
            for (int cellZ : cellsZ) {
                ChunkPos pick = config.getPotentialStructureChunk(seed, cellX * spacing, cellZ * spacing);
                if (!torus.containsChunk(pick.x()) || !torus.containsChunk(pick.z())) continue;
                int dx = Math.abs(torus.deltaChunks(originX, pick.x())), dz = Math.abs(torus.deltaChunks(originZ, pick.z()));
                if (Math.max(dx, dz) / spacing == radius) picks.add(pick);
            }
        }
        picks.sort(Comparator.comparingLong(pick -> {
            long dx = torus.deltaChunks(originX, pick.x()), dz = torus.deltaChunks(originZ, pick.z());
            return dx * dx + dz * dz;
        }));
        for (ChunkPos pick : picks) {
            Pair<BlockPos, Holder<Structure>> found = getStructureGeneratingAt(structures, level, structureManager, createReference, config, pick);
            if (found != null) return found;
        }
        return null;
    }

    /** The grid cells, along one axis, that hold folded chunks within reach of the given ring. */
    private static int[] orbis$cellsInReach(Torus torus, int origin, int radius, int spacing) {
        int reach = Math.min((radius + 1) * spacing, torus.widthChunks() / 2);
        IntSet cells = new IntOpenHashSet();
        for (int chunk = origin - reach; chunk <= origin + reach; chunk++) cells.add(Math.floorDiv(torus.foldChunk(chunk), spacing));
        return cells.toIntArray();
    }

    @Shadow
    private static @Nullable Pair<BlockPos, Holder<Structure>> getStructureGeneratingAt(Set<Holder<Structure>> structures, LevelReader level,
            StructureManager structureManager, boolean createReference, StructurePlacement config, ChunkPos chunkTarget) {
        throw new AssertionError();
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
