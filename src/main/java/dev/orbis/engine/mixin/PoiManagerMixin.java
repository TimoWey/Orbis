package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Pair;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Points of interest are found the short way round: the search visits the chunks around the centre past the seam
 * (their sections fold, see {@link SectionStorageMixin}), and measures and orders what it finds by the short
 * distance. A found point keeps its folded position; paths to it take the nearest image.
 */
@Mixin(PoiManager.class)
public abstract class PoiManagerMixin extends SectionStorage<Object, Object> implements TorusHolder {
    @SuppressWarnings("DataFlowIssue")
    private PoiManagerMixin() { super(null, null, null, null, null, null, null, null); }

    @Shadow public abstract Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> predicate, ChunkPos chunkPos, PoiManager.Occupancy occupancy);

    @Shadow public abstract Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy);

    @Shadow public abstract Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy);

    @Shadow public abstract Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter,
                                                                                      BlockPos center, int radius, PoiManager.Occupancy occupancy);

    @Override
    public @Nullable Torus orbis$torus() { return levelHeightAccessor instanceof Level level ? Tori.of(level) : null; }

    @Override
    public void orbis$setTorus(@Nullable Torus torus) {}

    /** How far a section is from a village, asked for a section past the seam, is the folded section's answer. */
    @ModifyVariable(method = "sectionsToVillage", at = @At("HEAD"), argsOnly = true)
    private SectionPos orbis$foldVillageSection(SectionPos section) { return Fold.section(orbis$torus(), section); }

    private static double orbis$distSqr(Torus torus, BlockPos pos, BlockPos center) {
        double dx = torus.delta(center.getX(), pos.getX()), dy = pos.getY() - center.getY(), dz = torus.delta(center.getZ(), pos.getZ());
        return dx * dx + dy * dy + dz * dz;
    }

    @WrapMethod(method = "getInSquare")
    private Stream<PoiRecord> orbis$square(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy,
                                           Operation<Stream<PoiRecord>> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(predicate, center, radius, occupancy);
        // In a world narrower than the search, two chunks of the square are the same chunk.
        return ChunkPos.rangeClosed(ChunkPos.containing(center), Math.floorDiv(radius, 16) + 1)
                .flatMap(chunk -> getInChunk(predicate, chunk, occupancy))
                .distinct()
                .filter(record -> Math.abs(torus.delta(center.getX(), record.getPos().getX())) <= radius
                        && Math.abs(torus.delta(center.getZ(), record.getPos().getZ())) <= radius);
    }

    @WrapMethod(method = "getInRange")
    private Stream<PoiRecord> orbis$range(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy,
                                          Operation<Stream<PoiRecord>> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(predicate, center, radius, occupancy);
        return getInSquare(predicate, center, radius, occupancy).filter(record -> orbis$distSqr(torus, record.getPos(), center) <= (double) radius * radius);
    }

    @WrapMethod(method = "findAllClosestFirstWithType")
    private Stream<Pair<Holder<PoiType>, BlockPos>> orbis$closestFirst(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center,
                                                                      int radius, PoiManager.Occupancy occupancy,
                                                                      Operation<Stream<Pair<Holder<PoiType>, BlockPos>>> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(predicate, filter, center, radius, occupancy);
        return findAllWithType(predicate, filter, center, radius, occupancy)
                .sorted(Comparator.comparingDouble(pair -> orbis$distSqr(torus, pair.getSecond(), center)));
    }

    @WrapMethod(method = "findClosest(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;")
    private Optional<BlockPos> orbis$closest(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy occupancy,
                                             Operation<Optional<BlockPos>> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(predicate, center, radius, occupancy);
        return getInRange(predicate, center, radius, occupancy).map(PoiRecord::getPos).min(Comparator.comparingDouble(pos -> orbis$distSqr(torus, pos, center)));
    }

    @WrapMethod(method = "findClosest(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;")
    private Optional<BlockPos> orbis$closestFiltered(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius,
                                                     PoiManager.Occupancy occupancy, Operation<Optional<BlockPos>> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(predicate, filter, center, radius, occupancy);
        return getInRange(predicate, center, radius, occupancy).map(PoiRecord::getPos).filter(filter)
                .min(Comparator.comparingDouble(pos -> orbis$distSqr(torus, pos, center)));
    }

    @WrapMethod(method = "findClosestWithType")
    private Optional<Pair<Holder<PoiType>, BlockPos>> orbis$closestWithType(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius,
                                                                           PoiManager.Occupancy occupancy,
                                                                           Operation<Optional<Pair<Holder<PoiType>, BlockPos>>> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(predicate, center, radius, occupancy);
        return getInRange(predicate, center, radius, occupancy).min(Comparator.comparingDouble(record -> orbis$distSqr(torus, record.getPos(), center)))
                .map(record -> Pair.of(record.getPoiType(), record.getPos()));
    }
}
