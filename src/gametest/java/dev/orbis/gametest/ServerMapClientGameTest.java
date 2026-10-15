package dev.orbis.gametest;

import static dev.orbis.gametest.MapClientGameTest.NETHER;
import static dev.orbis.gametest.MapClientGameTest.OVERWORLD;
import static dev.orbis.gametest.MapClientGameTest.checkKnown;
import static dev.orbis.gametest.MapClientGameTest.waitForCells;
import static dev.orbis.gametest.MapClientGameTest.waitForDimension;
import static dev.orbis.gametest.MapClientGameTest.waitForMap;

import dev.orbis.atlas.AtlasProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * The player map on a dedicated server: a player explores places far apart in both dimensions, leaves, joins again
 * and finds every chunk seen before still on the map, kept by the client per server world.
 */
public final class ServerMapClientGameTest implements FabricClientGameTest {
    private static final int HALF = SeamWorld.HALF;
    /** Places far apart; the last two beside the east seam and beside a corner of the world. */
    private static final int[][] PLACES = {{0, 0}, {400, 300}, {HALF - 40, 0}, {-HALF + 24, HALF - 40}};

    @Override
    public void runTest(ClientGameTestContext context) {
        List<Long> explored = new ArrayList<>(), netherExplored = new ArrayList<>();
        try (TestDedicatedServerContext server = context.worldBuilder()
                .adjustSettings(settings -> {
                    var preset = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(SeamWorld.TINY);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
                    settings.setSeed("8675309");
                    settings.setGenerateStructures(true);
                })
                .createServer(new Properties())) {
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule advance_time false");

            try (TestDedicatedServerConnection connection = server.connect()) {
                server.runCommand("gamemode creative @a");
                for (int[] place : PLACES) {
                    server.runCommand("tp @a " + place[0] + " 200 " + place[1]);
                    waitNear(context, place[0], place[1]);
                    connection.waitForChunksRender();
                    context.waitTicks(40);
                }
                var shown = waitForCells(context, OVERWORLD);
                for (int[] place : PLACES) {
                    long key = ChunkPos.pack(place[0] >> 4, place[1] >> 4);
                    SeamWorld.check(shown.contains(key), "The server map lacks the explored chunk at " + place[0] + ", " + place[1]);
                }
                explored.addAll(shown);
                openMap(context, "server-map-explored");

                server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
                waitForDimension(context, Level.NETHER);
                connection.waitForChunksRender();
                context.waitTicks(60);
                netherExplored.addAll(waitForCells(context, NETHER));
                openMap(context, "server-map-nether");
                server.runCommand("execute in minecraft:overworld run tp @a 0 200 0");
                waitForDimension(context, Level.OVERWORLD);
                connection.waitForChunksRender();
                context.waitTicks(40);
            }
            System.out.println("Orbis server map test: explored " + explored.size() + " Overworld and "
                    + netherExplored.size() + " Nether chunks before leaving");

            // Join again: the map shows everything seen before, around the spawn point and far away.
            try (TestDedicatedServerConnection connection = server.connect()) {
                waitForDimension(context, Level.OVERWORLD);
                connection.waitForChunksRender();
                checkKnown(context, OVERWORLD, explored, "after rejoining the server");
                // Let the chat notice of an offline-mode server fade before the pictures.
                context.waitTicks(140);
                openMap(context, "server-map-after-rejoin");
                // Zoomed out, so the place explored at 400, 300 shows beside the spawn point.
                context.runOnClient(mc -> AtlasProbe.prepare(OVERWORLD, false, 1024));
                openMap(context, "server-map-after-rejoin-zoomed-out");
                server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
                waitForDimension(context, Level.NETHER);
                connection.waitForChunksRender();
                checkKnown(context, NETHER, netherExplored, "in the Nether after rejoining the server");
                openMap(context, "server-map-nether-after-rejoin");
            }
            context.setScreen(TitleScreen::new);
        }
    }

    private static void waitNear(ClientGameTestContext context, int x, int z) {
        for (int tick = 0; tick < 600; tick += 5) {
            if (context.computeOnClient(mc -> mc.player != null && Math.abs(mc.player.getX() - x - 0.5) < 2
                    && Math.abs(mc.player.getZ() - z - 0.5) < 2)) return;
            context.waitTicks(5);
        }
        throw new AssertionError("The player never arrived at " + x + ", " + z);
    }

    private static void openMap(ClientGameTestContext context, String screenshot) {
        context.getInput().pressKey(AtlasProbe.mapKey());
        waitForMap(context, true, "M did not open the map for " + screenshot);
        context.waitTicks(40);
        context.takeScreenshot(screenshot);
        context.getInput().pressKey(AtlasProbe.mapKey());
        waitForMap(context, false, "M did not close the map for " + screenshot);
    }
}
