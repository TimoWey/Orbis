package dev.orbis.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.orbis.OrbisWorldShape;
import dev.orbis.WorldSize;
import dev.orbis.atlas.AtlasProbe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The player map on the Debug world: what was explored is still on it after a trip to the Nether and after leaving
 * and reopening the world; zoom keys and the mouse wheel change the scale; the map key is listed under Controls,
 * can be rebound there and opens the map under its new key.
 */
public final class MapClientGameTest implements FabricClientGameTest {
    static final WorldSize DEBUG = WorldSize.select(WorldSize.DEBUG_MIN_CHUNKS);
    private static final int HALF = DEBUG.overworldBlocks() / 2;
    static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether";
    /** Places far apart, the last one next to a corner of the world. */
    private static final int[][] PLACES = {{0, 0}, {500, 400}, {-600, -300}, {-HALF + 24, HALF - 40}};

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        List<Long> explored = new ArrayList<>(), netherExplored = new ArrayList<>();
        try (TestSingleplayerContext world = createDebug(context)) {
            save = world.getWorldSave();
            var server = world.getServer();

            for (int[] place : PLACES) {
                server.runCommand("tp @a " + place[0] + " 200 " + place[1]);
                SeamWorld.waitLoaded(context, world, place[0], place[1]);
                world.getConnection().waitForChunksRender();
                context.waitTicks(20);
            }
            Set<Long> shown = waitForCells(context, OVERWORLD);
            for (int[] place : PLACES) {
                long key = ChunkPos.pack(place[0] >> 4, place[1] >> 4);
                SeamWorld.check(shown.contains(key), "The map lacks the explored chunk at " + place[0] + ", " + place[1]);
            }
            explored.addAll(shown);
            // Beside the corner the minimap stays centred on the player and goes on past both seams.
            context.waitTicks(40);
            context.takeScreenshot("minimap-at-corner");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, true, "M did not open the map");
            context.waitTicks(20);
            context.takeScreenshot("map-explored");
            checkZoom(context);
            context.runOnClient(mc -> AtlasProbe.showSettings(mc, true));
            context.waitTicks(5);
            context.takeScreenshot("map-settings");
            context.runOnClient(mc -> AtlasProbe.showSettings(mc, false));
            // The world once, as a square: dark past its edges, which are outlined.
            context.runOnClient(mc -> AtlasProbe.setRepeat(false));
            context.waitTicks(10);
            context.takeScreenshot("map-square");
            context.runOnClient(mc -> AtlasProbe.setRepeat(true));
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, false, "M did not close the map");

            // Nether and back: the Overworld map keeps what it had.
            server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
            waitForDimension(context, Level.NETHER);
            context.waitTicks(60);
            netherExplored.addAll(waitForCells(context, NETHER));
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, true, "M did not open the map in the Nether");
            context.waitTicks(20);
            context.takeScreenshot("map-nether");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, false, "M did not close the map in the Nether");
            server.runCommand("execute in minecraft:overworld run tp @a 0 200 0");
            waitForDimension(context, Level.OVERWORLD);
            world.getConnection().waitForChunksRender();
            checkKnown(context, OVERWORLD, explored, "after the Nether");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, true, "M did not open the map after the Nether");
            context.waitTicks(20);
            context.takeScreenshot("map-after-nether");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, false, "M did not close the map after the Nether");
        }

        // Leave the world and come back: the map shows what was explored before.
        try (TestSingleplayerContext world = save.open()) {
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("join-message");
            checkKnown(context, OVERWORLD, explored, "after reopening the world");
            // Zoomed out to the whole world, so all four explored places show.
            context.runOnClient(mc -> AtlasProbe.prepare(OVERWORLD, false, DEBUG.overworldBlocks()));
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, true, "M did not open the map after reopening the world");
            context.waitTicks(40);
            context.takeScreenshot("map-after-relog");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, false, "M did not close the map after reopening the world");
            // The globe, lit from the upper left, with its thin atmosphere.
            context.runOnClient(mc -> AtlasProbe.prepare(OVERWORLD, true, 0));
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, true, "M did not open the globe");
            context.waitTicks(40);
            context.takeScreenshot("globe");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, false, "M did not close the globe");
            // The corner minimap in globe mode carries the same light.
            context.waitTicks(40);
            context.takeScreenshot("minimap-globe");
            context.runOnClient(mc -> AtlasProbe.prepare(OVERWORLD, false, DEBUG.overworldBlocks()));
            world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
            waitForDimension(context, Level.NETHER);
            checkKnown(context, NETHER, netherExplored, "in the Nether after reopening the world");
            world.getServer().runCommand("execute in minecraft:overworld run tp @a 0 200 0");
            waitForDimension(context, Level.OVERWORLD);
            world.getConnection().waitForChunksRender();
            checkRebind(context);
            checkCommand(context, world);
        }
    }

    /** /orbis check, typed in chat, looks for every biome and structure of both dimensions and reports when done. */
    private static void checkCommand(ClientGameTestContext context, TestSingleplayerContext world) {
        context.getInput().pressKey(options -> options.keyCommand);
        context.waitTicks(5);
        context.getInput().typeChars("orbis check");
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        List<String> status = List.of();
        for (int tick = 0; tick < 20 * 60 * 6; tick += 100) {
            context.waitTicks(100);
            status = world.getServer().computeOnServer(s -> {
                var lines = new ArrayList<String>();
                var source = s.createCommandSourceStack().withSource(new net.minecraft.commands.CommandSource() {
                    @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { lines.add(message.getString()); }
                    @Override public boolean acceptsSuccess() { return true; }
                    @Override public boolean acceptsFailure() { return true; }
                    @Override public boolean shouldInformAdmins() { return false; }
                });
                s.getCommands().performPrefixedCommand(source, "orbis check status");
                return lines;
            });
            if (status.stream().anyMatch(line -> line.startsWith("Orbis: Done."))) break;
        }
        System.out.println("Orbis map test: /orbis check said " + status);
        SeamWorld.check(status.stream().anyMatch(line -> line.startsWith("Orbis: Done.")), "/orbis check never finished: " + status);
        SeamWorld.check(status.stream().anyMatch(line -> line.matches("Orbis: Overworld: \\d+ of \\d+ biomes found\\.")),
                "/orbis check reported no Overworld biomes: " + status);
        SeamWorld.check(status.stream().anyMatch(line -> line.matches("Orbis: Overworld: [1-9]\\d* of \\d+ structures found\\.")),
                "/orbis check found no Overworld structures: " + status);
        SeamWorld.check(status.stream().anyMatch(line -> line.matches("Orbis: Nether: \\d+ of \\d+ structures found\\.")),
                "/orbis check reported no Nether structures: " + status);
        context.getInput().pressKey(options -> options.keyChat);
        context.waitTicks(5);
        context.takeScreenshot("orbis-check");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
    }

    /** The Debug world, 128 × 128 Overworld chunks and a 16 × 16 chunk Nether, made as a player would; noon, creative. */
    static TestSingleplayerContext createDebug(ClientGameTestContext context) {
        TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    var normal = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.NORMAL);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
                    OrbisWorldShape.orbis(true);
                    OrbisWorldShape.size(DEBUG);
                    settings.setSeed("8675309");
                    settings.setGenerateStructures(true);
                    // /orbis check is for operators; in singleplayer that means cheats on.
                    settings.setAllowCommands(true);
                })
                .create();
        var server = world.getServer();
        server.runCommand("time set noon");
        server.runCommand("weather clear");
        server.runCommand("gamerule advance_time false");
        server.runCommand("gamemode creative @a");
        return world;
    }

    /** Zoom keys and the mouse wheel change how many blocks the open map shows, and the scale is kept. */
    private static void checkZoom(ClientGameTestContext context) {
        double before = context.computeOnClient(mc -> AtlasProbe.zoom(OVERWORLD));
        context.getInput().pressKey(InputConstants.KEY_EQUALS);
        context.getInput().pressKey(InputConstants.KEY_EQUALS);
        context.waitTicks(5);
        double in = context.computeOnClient(mc -> AtlasProbe.zoom(OVERWORLD));
        SeamWorld.check(in < before, "Zooming in with = left the map at " + in + " blocks (was " + before + ")");
        context.takeScreenshot("map-zoomed-in");
        int[] centre = context.computeOnClient(mc -> new int[]{mc.getWindow().getScreenWidth() / 2, mc.getWindow().getScreenHeight() / 2});
        context.getInput().setCursorPos(centre[0], centre[1]);
        context.getInput().scroll(-4);
        context.getInput().scroll(-4);
        context.waitTicks(5);
        double out = context.computeOnClient(mc -> AtlasProbe.zoom(OVERWORLD));
        SeamWorld.check(out > in, "Scrolling down did not zoom out: " + out + " blocks (was " + in + ")");
        context.getInput().pressKey(InputConstants.KEY_MINUS);
        context.waitTicks(5);
        SeamWorld.check(context.computeOnClient(mc -> AtlasProbe.zoom(OVERWORLD)) >= out, "- zoomed in instead of out");
        context.takeScreenshot("map-zoomed-out");
        while (context.computeOnClient(mc -> AtlasProbe.zoom(OVERWORLD)) > before * 1.01) context.getInput().pressKey(InputConstants.KEY_EQUALS);
    }

    /** The map key sits under Controls and works under the key the player chose there. */
    private static void checkRebind(ClientGameTestContext context) {
        boolean listed = context.computeOnClient(mc -> List.of(mc.options.keyMappings).contains(AtlasProbe.mapKey()));
        SeamWorld.check(listed, "The map key is not among the controls");
        context.runOnClient(mc -> mc.gui.setScreen(new KeyBindsScreen(null, mc.options)));
        context.waitTicks(10);
        int[] centre = context.computeOnClient(mc -> new int[]{mc.getWindow().getScreenWidth() / 2, mc.getWindow().getScreenHeight() / 2});
        context.getInput().setCursorPos(centre[0], centre[1]);
        for (int i = 0; i < 40; i++) context.getInput().scroll(-10);
        context.waitTicks(5);
        context.takeScreenshot("map-key-in-controls");
        // As a click on the entry's button does: select the binding, then press the new key.
        context.runOnClient(mc -> {
            var screen = (KeyBindsScreen) mc.gui.screen();
            screen.selectedKey = AtlasProbe.mapKey();
        });
        context.getInput().pressKey(InputConstants.KEY_N);
        context.waitTicks(5);
        String bound = context.computeOnClient(mc -> AtlasProbe.mapKey().saveString());
        SeamWorld.check("key.keyboard.n".equals(bound), "The map key is bound to " + bound + " after choosing N");
        context.takeScreenshot("map-key-rebound");
        context.runOnClient(mc -> mc.gui.setScreen(null));
        context.waitTicks(5);
        context.getInput().pressKey(InputConstants.KEY_M);
        context.waitTicks(5);
        SeamWorld.check(!context.computeOnClient(AtlasProbe::mapOpen), "M still opens the map after rebinding it to N");
        context.getInput().pressKey(InputConstants.KEY_N);
        waitForMap(context, true, "N did not open the map after rebinding");
        context.takeScreenshot("map-opened-with-n");
        context.getInput().pressKey(InputConstants.KEY_N);
        waitForMap(context, false, "N did not close the map");
        context.runOnClient(mc -> {
            AtlasProbe.mapKey().setKey(AtlasProbe.mapKey().getDefaultKey());
            net.minecraft.client.KeyMapping.resetMapping();
            mc.options.save();
        });
    }

    static Set<Long> waitForCells(ClientGameTestContext context, String dimension) {
        for (int tick = 0; tick < 600; tick += 10) {
            var cells = context.computeOnClient(mc -> AtlasProbe.cells());
            if (cells != null && cells.getKey().equals(dimension) && !cells.getValue().isEmpty()) return new HashSet<>(cells.getValue());
            context.waitTicks(10);
        }
        throw new AssertionError("The map never showed " + dimension);
    }

    /** Waits until the map has a picture of every chunk in {@code keys}, in memory or on disk. */
    static void checkKnown(ClientGameTestContext context, String dimension, List<Long> keys, String when) {
        checkKnown(context, dimension, keys, when, 1200);
    }

    static void checkKnown(ClientGameTestContext context, String dimension, List<Long> keys, String when, int ticks) {
        Set<Long> missing = null;
        for (int tick = 0; tick < ticks; tick += 20) {
            missing = context.computeOnClient(mc -> {
                try { return AtlasProbe.unknown(dimension, keys); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            });
            if (missing != null && missing.isEmpty()) return;
            context.waitTicks(20);
        }
        String sample = missing == null ? "no map of " + dimension
                : missing.size() + " of " + keys.size() + " chunks, e.g. " + missing.stream().limit(12)
                        .map(key -> "[" + ChunkPos.getX(key) + ", " + ChunkPos.getZ(key) + "]").toList();
        throw new AssertionError("The map forgot explored chunks " + when + ": " + sample);
    }

    static void waitForMap(ClientGameTestContext context, boolean open, String message) {
        for (int tick = 0; tick < 100; tick++) {
            if (context.computeOnClient(AtlasProbe::mapOpen) == open) return;
            context.waitTick();
        }
        throw new AssertionError(message);
    }

    static void waitForDimension(ClientGameTestContext context, ResourceKey<Level> dimension) {
        for (int tick = 0; tick < 600; tick += 5) {
            if (context.computeOnClient(mc -> mc.level != null && mc.level.dimension() == dimension && mc.player != null)) return;
            context.waitTicks(5);
        }
        throw new AssertionError("Never arrived in " + dimension.identifier());
    }
}
