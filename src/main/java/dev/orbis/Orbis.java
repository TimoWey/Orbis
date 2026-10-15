package dev.orbis;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Orbis implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("orbis");
    private static Component biomeWarning() {
        return Component.translatableWithFallback("message.orbis.biome_warning",
                "Orbis: Complete biome and structure coverage is not guaranteed for any size. "
                + "Existing-world conversion is not available in this alpha.");
    }

    /** One line on joining: how big the world is, that its edges meet, and which key opens the map. */
    static Component welcome(int blocks) {
        return Component.translatableWithFallback("message.orbis.welcome",
                "Orbis world · %1$s × %1$s blocks · the edges connect · map: %2$s", blocks,
                Component.keybind("key.orbis.atlas")).withStyle(net.minecraft.ChatFormatting.AQUA);
    }

    /** The Overworld's side length in blocks, or 0 when this is not an Orbis world. */
    static int widthBlocks(net.minecraft.server.MinecraftServer server) {
        var torus = dev.orbis.engine.Tori.of(server.overworld());
        return torus == null ? 0 : torus.widthBlocks();
    }

    @Override public void onInitialize() {
        dev.orbis.engine.Tori.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            int blocks = widthBlocks(server);
            if (blocks > 0) handler.player.sendSystemMessage(welcome(blocks));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(Commands.literal("orbis").executes(context -> {
                var server = context.getSource().getServer();
                var torus = dev.orbis.engine.Tori.of(server.overworld());
                if (torus == null) {
                    context.getSource().sendSuccess(() -> Component.translatableWithFallback("message.orbis.not_orbis",
                            "This is not an Orbis world. Create a new world with World Shape: Orbis."), false);
                    return 0;
                }
                int width = torus.widthChunks();
                context.getSource().sendSuccess(() -> Component.translatableWithFallback("message.orbis.dimensions",
                        "Orbis | Overworld: %1$s × %1$s chunks | Nether: %2$s × %2$s | End: unwrapped", width, width / 8), false);
                context.getSource().sendSuccess(Orbis::biomeWarning, false);
                return 1;
            })));
        OrbisRequired.register();
        dev.orbis.atlas.OrbisChecks.register();
        dev.orbis.atlas.AtlasPlayersServer.register();
        LOGGER.info("Orbis standalone alpha initialized. Complete biome and structure coverage is not guaranteed; existing-world conversion is unavailable.");
    }
}
