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
    public static final String BIOME_WARNING = "Orbis: Biome coverage has NOT been checked in either dimension. "
            + "Any size, including Medium, may lack biomes. Existing-world conversion is not available in this alpha.";

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
                handler.player.sendSystemMessage(Component.literal(BIOME_WARNING));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(Commands.literal("orbis").executes(context -> {
                var server = context.getSource().getServer();
                if (!WORLDS.contains(server.overworld().getChunkSource().randomState())) {
                    context.getSource().sendSuccess(() -> Component.literal("This is not an Orbis world. Create a new world with World Shape: Orbis."), false);
                    return 0;
                }
                var shape = ToroidalWorldApi.shapeOf(server.overworld()).orElseThrow();
                int width = shape.widthChunks(Direction.Axis.X);
                context.getSource().sendSuccess(() -> Component.literal("Orbis | Overworld: " + width + " x " + width
                        + " chunks | Nether: " + width / 8 + " x " + width / 8 + " | End: unwrapped"), false);
                context.getSource().sendSuccess(() -> Component.literal(BIOME_WARNING), false);
                return 1;
            })));
        LOGGER.info("Orbis standalone alpha initialized. Biome coverage and conversion are not implemented.");
    }
}
