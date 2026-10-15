package dev.orbis.engine.dh;

import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The client's levels carry no torus: in singleplayer it is the integrated server's level of the same dimension, on a
 * server the one it announced.
 */
final class DhTorusClient {
    private DhTorusClient() {}

    static @Nullable Torus of(Level level) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) return Tori.of(server.getLevel(level.dimension()));
        return dev.orbis.atlas.PlayerAtlasClient.announcedTorus(level.dimension().identifier().toString());
    }
}
