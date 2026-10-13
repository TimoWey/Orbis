package dev.orbis.engine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundBlockEntityDataPacket.class)
public interface BlockEntityDataPacketAccess {
    @Mutable @Accessor("pos") void orbis$setPos(BlockPos pos);
}
