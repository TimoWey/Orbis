package dev.orbis.gametest;

import dev.orbis.engine.Tori;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Creates a Tiny world on the new engine and looks across the seams. Screenshots land in run/screenshots. */
public final class TorusClientGameTest implements FabricClientGameTest {
    private static final ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> TINY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("orbis", "torus_tiny"));

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    var preset = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(TINY);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
                    settings.setSeed("8675309");
                })
                .create()) {
            var server = world.getServer();
            int width = server.computeOnServer(s -> {
                var torus = Tori.of(s.overworld());
                if (torus == null) throw new AssertionError("Overworld is not on the new engine");
                return torus.widthBlocks();
            });
            if (width != 16_384) throw new AssertionError("Unexpected width " + width);
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule advance_time false");
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("orbis-spawn");
        }
    }
}
