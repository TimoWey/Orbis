package dev.orbis;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import com.toroidalworld.api.v1.gen.GenerationHooks;
import com.toroidalworld.api.v1.option.WorldOptions;
import com.toroidalworld.api.v1.ToroidalWorldApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Orbis implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("orbis");
    private static final Set<RandomState> WORLDS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static Component biomeWarning() {
        return Component.translatableWithFallback("message.orbis.biome_warning",
                "Orbis: Complete biome and structure coverage is not guaranteed for any size. "
                + "Existing-world conversion is not available in this alpha.");
    }

    @Override public void onInitialize() {
        new com.toroidalworld.ToroidalWorldFabric().onInitialize();
        WorldOptions.register(OrbisShape.ENABLED);
        OrbisShape.MODULE.register();
        GenerationHooks.atRandomState("orbis:identify", (state, shape, options, seaLevel) -> {
            if (options.get(OrbisShape.ENABLED)) WORLDS.add(state);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (WORLDS.contains(server.overworld().getChunkSource().randomState())) {
                handler.player.sendSystemMessage(biomeWarning());
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(Commands.literal("orbis").executes(context -> {
                var server = context.getSource().getServer();
                if (!WORLDS.contains(server.overworld().getChunkSource().randomState())) {
                    context.getSource().sendSuccess(() -> Component.translatableWithFallback("message.orbis.not_orbis",
                            "This is not an Orbis world. Create a new world with World Shape: Orbis."), false);
                    return 0;
                }
                var shape = ToroidalWorldApi.shapeOf(server.overworld()).orElseThrow();
                int width = shape.widthChunks(Direction.Axis.X);
                context.getSource().sendSuccess(() -> Component.translatableWithFallback("message.orbis.dimensions",
                        "Orbis | Overworld: %1$s × %1$s chunks | Nether: %2$s × %2$s | End: unwrapped", width, width / 8), false);
                context.getSource().sendSuccess(Orbis::biomeWarning, false);
                return 1;
            })));
        dev.orbis.atlas.OrbisChecks.register();
        dev.orbis.atlas.AtlasPlayersServer.register();
        LOGGER.info("Orbis standalone alpha initialized. Complete biome and structure coverage is not guaranteed; existing-world conversion is unavailable.");
    }
}
