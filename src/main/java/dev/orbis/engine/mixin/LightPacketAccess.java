package dev.orbis.engine.mixin;

import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLightUpdatePacket.class)
public interface LightPacketAccess {
    @Mutable @Accessor("x") void orbis$setX(int x);

    @Mutable @Accessor("z") void orbis$setZ(int z);
}
