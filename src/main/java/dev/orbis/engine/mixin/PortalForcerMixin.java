package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * A portal links to the nearest existing portal, measured across the seam: the search square covers the folded
 * chunks around the exit, and distances are taken to each portal's image nearest to the exit.
 */
@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {
    @Shadow @Final protected ServerLevel level;

    @WrapMethod(method = "findClosestPortalPosition")
    private Optional<BlockPos> orbis$nearestPortal(BlockPos exit, boolean toNether, WorldBorder border,
                                                   Operation<Optional<BlockPos>> original) {
        Torus torus = Tori.of(level);
        if (torus == null) return original.call(exit, toNether, border);
        int radius = toNether ? 16 : 128;
        PoiManager poi = level.getPoiManager();
        int centreX = SectionPos.blockToSectionCoord(exit.getX()), centreZ = SectionPos.blockToSectionCoord(exit.getZ());
        int chunks = (radius >> 4) + 1;
        Set<Long> seen = new HashSet<>();
        Stream.Builder<PoiRecord> portals = Stream.builder();
        for (int x = centreX - chunks; x <= centreX + chunks; x++) {
            for (int z = centreZ - chunks; z <= centreZ + chunks; z++) {
                long chunk = Fold.chunk(torus, ChunkPos.pack(x, z));
                if (!seen.add(chunk)) continue;
                ChunkPos folded = ChunkPos.unpack(chunk);
                poi.ensureLoadedAndValid(level, folded.getMiddleBlockPosition(exit.getY()), 0);
                poi.getInChunk(type -> type.is(PoiTypes.NETHER_PORTAL), folded, PoiManager.Occupancy.ANY).forEach(portals);
            }
        }
        Comparator<BlockPos> nearest = Comparator.<BlockPos>comparingDouble(pos -> nearestImage(torus, pos, exit).distSqr(exit))
                .thenComparingInt(BlockPos::getY);
        return portals.build()
                .map(PoiRecord::getPos)
                .filter(pos -> {
                    BlockPos image = nearestImage(torus, pos, exit);
                    return Math.abs(image.getX() - exit.getX()) <= radius && Math.abs(image.getZ() - exit.getZ()) <= radius;
                })
                .filter(border::isWithinBounds)
                .filter(pos -> level.getBlockState(pos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                .min(nearest);
    }

    private static BlockPos nearestImage(Torus torus, BlockPos pos, BlockPos to) {
        return Fold.nearest(torus, pos, to.getX() + 0.5, to.getZ() + 0.5);
    }
}
