package dev.orbis.engine.mixin;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLevelChunkWithLightPacket.class)
public interface ChunkPacketAccess {
    @Mutable @Accessor("x") void orbis$setX(int x);

    @Mutable @Accessor("z") void orbis$setZ(int z);
}
