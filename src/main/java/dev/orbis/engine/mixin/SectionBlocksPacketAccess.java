package dev.orbis.engine.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public interface SectionBlocksPacketAccess {
    @Accessor("sectionPos") SectionPos orbis$sectionPos();

    @Mutable @Accessor("sectionPos") void orbis$setSectionPos(SectionPos pos);
}
