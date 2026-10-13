package dev.orbis.engine.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("level") ServerLevel orbis$level();

    /** Every holder the chunk map is tracking, keyed by chunk; on a torus every key must be a folded position. */
    @Accessor("updatingChunkMap") Long2ObjectLinkedOpenHashMap<ChunkHolder> orbis$updatingChunks();
}
