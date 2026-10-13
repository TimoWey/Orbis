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
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddTransientBlockPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
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
            case ClientboundAddEntityPacket add -> {
                Vec3 at = frame.position(torus, player, new Vec3(add.getX(), add.getY(), add.getZ()));
                return new ClientboundAddEntityPacket(add.getId(), add.getUUID(), at.x, at.y, at.z, add.getXRot(), add.getYRot(),
                        add.getType(), add.getData(), add.getMovement(), add.getYHeadRot());
            }
            case ClientboundTeleportEntityPacket teleport -> {
                return new ClientboundTeleportEntityPacket(teleport.id(), change(frame, torus, player, teleport.change(),
                        teleport.relatives()), teleport.relatives(), teleport.onGround());
            }
            case ClientboundEntityPositionSyncPacket sync -> {
                PositionPath path = switch (sync.position()) {
                    case PositionPath.Linear(Vec3 end) -> PositionPath.of(frame.position(torus, player, end));
                    case PositionPath.Stepped(Vec3 ignored, List<PositionStep> steps) -> PositionPath.stepped(steps.stream()
                            .map(step -> new PositionStep(frame.position(torus, player, step.position()), step.tickOffset()))
                            .toList());
                };
                return new ClientboundEntityPositionSyncPacket(sync.id(), path, sync.yRot(), sync.xRot(), sync.onGround());
            }
            case ClientboundMoveVehiclePacket vehicle -> {
                PositionAndRotation to = vehicle.movingTo();
                return new ClientboundMoveVehiclePacket(PositionAndRotation.of(frame.position(torus, player, to.position()),
                        to.yRot(), to.xRot()));
            }
            case ClientboundMoveMinecartPacket minecart -> {
                return new ClientboundMoveMinecartPacket(minecart.entityId(), minecart.lerpSteps().stream()
                        .map(step -> new NewMinecartBehavior.MinecartStep(frame.position(torus, player, step.position()),
                                step.movement(), step.yRot(), step.xRot(), step.weight()))
                        .toList());
            }
            case ClientboundSetEntityDataPacket data -> {
                List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(data.packedItems().size());
                boolean changed = false;
                for (SynchedEntityData.DataValue<?> value : data.packedItems()) {
                    SynchedEntityData.DataValue<?> moved = dataValue(frame, torus, player, value);
                    changed |= moved != value;
                    values.add(moved);
                }
                return changed ? new ClientboundSetEntityDataPacket(data.id(), values) : data;
            }
            case ClientboundDamageEventPacket damage -> {
                if (damage.sourcePosition().isEmpty()) return damage;
                return new ClientboundDamageEventPacket(damage.entityId(), damage.sourceType(), damage.sourceCauseId(),
                        damage.sourceDirectId(), damage.sourcePosition().map(at -> frame.position(torus, player, at)));
            }
            case ClientboundSoundPacket sound -> {
                Vec3 at = frame.position(torus, player, new Vec3(sound.getX(), sound.getY(), sound.getZ()));
                return new ClientboundSoundPacket(sound.getSound(), sound.getSource(), at.x, at.y, at.z,
                        sound.getVolume(), sound.getPitch(), sound.getSeed());
            }
            case ClientboundLevelParticlesPacket particles -> {
                Vec3 at = frame.position(torus, player, new Vec3(particles.x(), particles.y(), particles.z()));
                return new ClientboundLevelParticlesPacket(particles.particle(), particles.overrideLimiter(), particles.alwaysShow(),
                        at.x, at.y, at.z, particles.xDist(), particles.yDist(), particles.zDist(),
                        particles.xMaxSpeed(), particles.yMaxSpeed(), particles.zMaxSpeed(), particles.count(),
                        particles.randomizationType());
            }
            case ClientboundExplodePacket explosion -> {
                return new ClientboundExplodePacket(frame.position(torus, player, explosion.center()), explosion.radius(),
                        explosion.blockCount(), explosion.playerKnockback(), explosion.explosionParticle(),
                        explosion.explosionSound(), explosion.blockParticles(), explosion.playSound());
            }
            case ClientboundLevelEventPacket event -> {
                return new ClientboundLevelEventPacket(event.getType(), frame.block(torus, player, event.getPos()),
                        event.getData(), event.isGlobalEvent());
            }
            case ClientboundBlockEventPacket event -> {
                return new ClientboundBlockEventPacket(frame.block(torus, player, event.getPos()), event.getBlock(),
                        event.getB0(), event.getB1());
            }
            case ClientboundBlockDestructionPacket destruction -> {
                return new ClientboundBlockDestructionPacket(destruction.getId(), frame.block(torus, player, destruction.getPos()),
                        destruction.getProgress());
            }
            case ClientboundAddTransientBlockPacket transientBlock -> {
                return new ClientboundAddTransientBlockPacket(frame.block(torus, player, transientBlock.getPos()),
                        transientBlock.getBlockState());
            }
            case ClientboundOpenSignEditorPacket sign -> {
                return new ClientboundOpenSignEditorPacket(frame.block(torus, player, sign.pos()), sign.slot());
            }
            case ClientboundChunksBiomesPacket biomes -> {
                return new ClientboundChunksBiomesPacket(biomes.chunkBiomeData().stream()
                        .map(chunk -> new ClientboundChunksBiomesPacket.ChunkBiomeData(frame.chunk(torus, player, chunk.pos()),
                                chunk.buffer()))
                        .toList());
            }
            default -> {
                return packet;
            }
        }
    }

    private static PositionMoveRotation change(ClientFrame frame, Torus torus, ServerPlayer player, PositionMoveRotation change,
                                               Set<Relative> relatives) {
        Vec3 at = change.position();
        double x = relatives.contains(Relative.X) ? at.x : frame.x(torus, player, at.x);
        double z = relatives.contains(Relative.Z) ? at.z : frame.z(torus, player, at.z);
        return new PositionMoveRotation(new Vec3(x, at.y, z), change.deltaMovement(), change.yRot(), change.xRot());
    }

    /** Synced block positions (a sleeper's bed, a crystal's beam target) point at the image nearest to the client. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SynchedEntityData.DataValue<?> dataValue(ClientFrame frame, Torus torus, ServerPlayer player,
                                                            SynchedEntityData.DataValue<?> value) {
        Object moved = switch (value.value()) {
            case BlockPos pos -> frame.block(torus, player, pos);
            case Optional<?> optional when optional.orElse(null) instanceof BlockPos pos -> Optional.of(frame.block(torus, player, pos));
            default -> value.value();
        };
        return moved == value.value() ? value : new SynchedEntityData.DataValue(value.id(), value.serializer(), moved);
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
