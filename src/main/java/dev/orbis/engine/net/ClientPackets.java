package dev.orbis.engine.net;

import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.mixin.BlockEntityDataPacketAccess;
import dev.orbis.engine.mixin.ChunkPacketAccess;
import dev.orbis.engine.mixin.LightPacketAccess;
import dev.orbis.engine.mixin.SectionBlocksPacketAccess;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

/**
 * Moves the positions in outgoing packets into the receiving client's frame. Packets that the server may send to
 * several players are copied before they change; chunk packets are built per player and change in place.
 */
public final class ClientPackets {
    private ClientPackets() {}

    public static Packet<?> toClient(ServerGamePacketListenerImpl listener, Packet<?> packet) {
        ServerPlayer player = listener.player;
        if (player == null) return packet;
        Torus torus = Tori.of(player.level());
        if (torus == null) return packet;
        return translate(((ClientFrame.Holder) listener).orbis$frame(), torus, player, packet);
    }

    @SuppressWarnings("unchecked")
    private static Packet<?> translate(ClientFrame frame, Torus torus, ServerPlayer player, Packet<?> packet) {
        switch (packet) {
            case ClientboundBundlePacket bundle -> {
                List<Packet<? super ClientGamePacketListener>> parts = new ArrayList<>();
                boolean changed = false;
                for (Packet<? super ClientGamePacketListener> part : bundle.subPackets()) {
                    Packet<?> moved = translate(frame, torus, player, part);
                    changed |= moved != part;
                    parts.add((Packet<? super ClientGamePacketListener>) moved);
                }
                return changed ? new ClientboundBundlePacket(parts) : bundle;
            }
            case ClientboundLevelChunkWithLightPacket chunk -> {
                var access = (ChunkPacketAccess) (Object) chunk;
                access.orbis$setX(frame.chunkX(torus, player, chunk.x()));
                access.orbis$setZ(frame.chunkZ(torus, player, chunk.z()));
                return chunk;
            }
            case ClientboundLightUpdatePacket light -> {
                var copy = copy(ClientboundLightUpdatePacket.STREAM_CODEC, registryBuffer(player), light);
                var access = (LightPacketAccess) (Object) copy;
                access.orbis$setX(frame.chunkX(torus, player, light.x()));
                access.orbis$setZ(frame.chunkZ(torus, player, light.z()));
                return copy;
            }
            case ClientboundForgetLevelChunkPacket forget -> {
                return new ClientboundForgetLevelChunkPacket(frame.chunk(torus, player, forget.pos()));
            }
            case ClientboundSetChunkCacheCenterPacket centre -> {
                return new ClientboundSetChunkCacheCenterPacket(frame.chunkX(torus, player, centre.getX()),
                        frame.chunkZ(torus, player, centre.getZ()));
            }
            case ClientboundBlockUpdatePacket block -> {
                return new ClientboundBlockUpdatePacket(frame.block(torus, player, block.getPos()), block.getBlockState());
            }
            case ClientboundSectionBlocksUpdatePacket blocks -> {
                var copy = copy(ClientboundSectionBlocksUpdatePacket.STREAM_CODEC, registryBuffer(player), blocks);
                var access = (SectionBlocksPacketAccess) (Object) copy;
                access.orbis$setSectionPos(frame.section(torus, player, ((SectionBlocksPacketAccess) (Object) blocks).orbis$sectionPos()));
                return copy;
            }
            case ClientboundBlockEntityDataPacket blockEntity -> {
                var copy = copy(ClientboundBlockEntityDataPacket.STREAM_CODEC, registryBuffer(player), blockEntity);
                ((BlockEntityDataPacketAccess) (Object) copy).orbis$setPos(frame.block(torus, player, blockEntity.getPos()));
                return copy;
            }
            case ClientboundPlayerPositionPacket position -> {
                PositionMoveRotation change = position.change();
                Vec3 at = change.position();
                double x = position.relatives().contains(Relative.X) ? at.x : frame.ownX(player, at.x);
                double z = position.relatives().contains(Relative.Z) ? at.z : frame.ownZ(player, at.z);
                if (x == at.x && z == at.z) return position;
                return new ClientboundPlayerPositionPacket(position.id(), new PositionMoveRotation(new Vec3(x, at.y, z),
                        change.deltaMovement(), change.yRot(), change.xRot()), position.relatives());
            }
            default -> {
                return packet;
            }
        }
    }

    private static RegistryFriendlyByteBuf registryBuffer(ServerPlayer player) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
    }

    /** An independent copy through the packet's own codec, so a packet shared between players stays untouched. */
    private static <T> T copy(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, RegistryFriendlyByteBuf buffer, T packet) {
        try {
            codec.encode(buffer, packet);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
