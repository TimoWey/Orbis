package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.orbis.engine.net.ClientPackets;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Every packet a server sends to a player passes here once, so this is where it enters the client's frame. */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow public abstract @Nullable PacketListener getPacketListener();

    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
    private void orbis$toClientFrame(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush,
                                     Operation<Void> original) {
        if (getPacketListener() instanceof ServerGamePacketListenerImpl game) packet = ClientPackets.toClient(game, packet);
        original.call(packet, listener, flush);
    }
}
