package dev.orbis.gametest;

import dev.orbis.OrbisClient;
import dev.orbis.OrbisRequired;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;

/**
 * A dedicated server with an Orbis world lets in a player with Orbis, who reads the short join line, and turns away a
 * player whose game has no Orbis with a message saying what to install, and a player with an Orbis of another protocol
 * with both versions named. A server with a normal world lets in a game without Orbis.
 */
public final class ModRequiredClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestDedicatedServerContext server = context.worldBuilder()
                .adjustSettings(settings -> {
                    var preset = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(SeamWorld.TINY);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
                    settings.setSeed("8675309");
                })
                .createServer(new Properties())) {
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                context.waitTicks(20);
                context.takeScreenshot("server-join-with-orbis");
            }

            // A game without Orbis does not take the greeting; that is all the server can tell.
            context.runOnClient(mc -> ClientConfigurationNetworking.unregisterGlobalReceiver(OrbisRequired.Hello.TYPE));
            String shown = turnedAway(context, server);
            SeamWorld.check(shown.contains("needs the Orbis mod") && shown.contains(OrbisRequired.version()),
                    "A player without Orbis read: " + shown);
            context.takeScreenshot("server-join-without-orbis");

            // An Orbis that speaks another protocol leaves on its own, naming both versions.
            context.runOnClient(mc -> OrbisClient.acceptHello(OrbisRequired.PROTOCOL + 1));
            shown = turnedAway(context, server);
            SeamWorld.check(shown.contains("This server runs Orbis " + OrbisRequired.version())
                    && shown.contains("you have Orbis"), "A player with another Orbis read: " + shown);
            context.takeScreenshot("server-join-other-orbis");
            context.runOnClient(mc -> OrbisClient.acceptHello());
            context.setScreen(TitleScreen::new);
        }

        // A server whose world is not an Orbis world lets in a game without Orbis.
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(new Properties())) {
            context.runOnClient(mc -> ClientConfigurationNetworking.unregisterGlobalReceiver(OrbisRequired.Hello.TYPE));
            try (var connection = server.connect()) {
                connection.waitForChunksRender();
                context.waitTicks(20);
                boolean joined = server.computeOnServer(s -> !s.getPlayerList().getPlayers().isEmpty());
                SeamWorld.check(joined, "A player without Orbis could not join a normal world");
                context.takeScreenshot("server-normal-world-without-orbis");
            } finally {
                context.runOnClient(mc -> OrbisClient.acceptHello());
            }
        }
        context.setScreen(TitleScreen::new);
    }

    /** Connects to the server, waits to be turned away and returns what the player read; nobody is in the world. */
    private static String turnedAway(ClientGameTestContext context, TestDedicatedServerContext server) {
        int port = server.computeOnServer(MinecraftServer::getPort);
        context.setScreen(TitleScreen::new);
        context.runOnClient(mc -> ConnectScreen.startConnecting(mc.gui.screen(), mc, ServerAddress.parseString("localhost:" + port),
                new ServerData("localhost", "localhost:" + port, ServerData.Type.OTHER), false, null));
        context.waitFor(mc -> mc.gui.screen() instanceof DisconnectedScreen, 1200);
        String shown = context.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
        System.out.println("Orbis mod-required test: shown " + shown);
        boolean joined = server.computeOnServer(s -> !s.getPlayerList().getPlayers().isEmpty());
        SeamWorld.check(!joined, "A turned-away player got into the world: " + shown);
        return shown;
    }
}
