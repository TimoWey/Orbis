package dev.orbis.atlas;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The wrap of one dimension, so the client map folds what it receives; {@code width} 0 means it does not wrap. */
public record AtlasShapePayload(Identifier dimension, int minBlock, int width) implements CustomPacketPayload {
    public static final Type<AtlasShapePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("orbis", "atlas_shape_v1"));
    public static final StreamCodec<FriendlyByteBuf, AtlasShapePayload> CODEC = CustomPacketPayload.codec(
            AtlasShapePayload::write, AtlasShapePayload::read);

    public AtlasShapePayload {
        if (dimension == null || width < 0 || width % 16 != 0 || minBlock % 16 != 0)
            throw new IllegalArgumentException("Invalid atlas shape");
    }
    static AtlasShapePayload of(Identifier dimension, AtlasShape shape) {
        return new AtlasShapePayload(dimension, shape.wrapped() ? shape.minX() : 0, shape.wrapped() ? shape.span() : 0);
    }
    AtlasShape shape() { return width == 0 ? AtlasShape.OPEN : new AtlasShape(true, minBlock, minBlock, width); }
    private void write(FriendlyByteBuf buf) { buf.writeIdentifier(dimension); buf.writeInt(minBlock); buf.writeVarInt(width); }
    private static AtlasShapePayload read(FriendlyByteBuf buf) {
        return new AtlasShapePayload(buf.readIdentifier(), buf.readInt(), buf.readVarInt());
    }
    @Override public Type<AtlasShapePayload> type() { return TYPE; }
}
