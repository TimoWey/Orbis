package dev.orbis.gametest;

import static dev.orbis.gametest.MapClientGameTest.*;

import dev.orbis.atlas.AtlasProbe;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Chunky pre-generates the whole Debug world, Overworld and Nether, through its own commands. After leaving and
 * reopening the world the map knows every chunk of both, without the player having been there. Runs only beside
 * Chunky ({@code -PorbisChunky=true}).
 */
public final class ChunkyClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("chunky")) {
            System.out.println("Orbis Chunky test: Chunky is not loaded, skipped");
            return;
        }
        int overworld = DEBUG.overworldBlocks() / 2, nether = DEBUG.netherBlocks() / 2;
        TestWorldSave save;
        try (TestSingleplayerContext world = createDebug(context)) {
            save = world.getWorldSave();
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            generate(context, server, OVERWORLD, overworld);
            generate(context, server, NETHER, nether);
            context.takeScreenshot("chunky-done");
        }
        try (TestSingleplayerContext world = save.open()) {
            world.getConnection().waitForChunksRender();
            checkSaved(world.getServer(), OVERWORLD, overworld);
            checkSaved(world.getServer(), NETHER, nether);
            checkKnown(context, OVERWORLD, everyChunk(overworld), "after Chunky (Overworld)", 20 * 60 * 10);
            context.runOnClient(mc -> AtlasProbe.prepare(OVERWORLD, false, DEBUG.overworldBlocks()));
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, true, "M did not open the map");
            context.waitTicks(60);
            context.takeScreenshot("chunky-map-overworld");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, false, "M did not close the map");
            context.runOnClient(mc -> AtlasProbe.prepare(OVERWORLD, true, 0));
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, true, "M did not open the globe");
            context.waitTicks(60);
            context.takeScreenshot("chunky-globe-overworld");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, false, "M did not close the globe");
            context.runOnClient(mc -> AtlasProbe.prepare(OVERWORLD, false, 512));

            world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
            waitForDimension(context, Level.NETHER);
            checkKnown(context, NETHER, everyChunk(nether), "after Chunky (Nether)", 20 * 60 * 5);
            context.runOnClient(mc -> AtlasProbe.prepare(NETHER, false, DEBUG.netherBlocks()));
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, true, "M did not open the map in the Nether");
            context.waitTicks(60);
            context.takeScreenshot("chunky-map-nether");
            context.getInput().pressKey(AtlasProbe.mapKey());
            waitForMap(context, false, "M did not close the map in the Nether");
            world.getServer().runCommand("execute in minecraft:overworld run tp @a 0 200 0");
            waitForDimension(context, Level.OVERWORLD);
        }
    }

    /** Runs Chunky over a square exactly one world wide, as a player would type it, and waits until it is done. */
    private static void generate(ClientGameTestContext context, TestServerContext server, String dimension, int half) {
        server.runCommand("chunky world " + dimension);
        server.runCommand("chunky shape square");
        server.runCommand("chunky center 0 0");
        server.runCommand("chunky radius " + half);
        server.runCommand("chunky start");
        long started = System.nanoTime();
        context.waitTicks(40);
        for (int tick = 0; running(server); tick += 100) {
            if (tick > 20 * 60 * 30) throw new AssertionError("Chunky did not finish " + dimension + " in 30 minutes");
            context.waitTicks(100);
        }
        System.out.printf("Orbis Chunky test: %s generated in %.0f s%n", dimension, (System.nanoTime() - started) / 1e9);
    }

    /** Every chunk of the world is saved as fully generated, and none outside it: the seam folds Chunky's border. */
    private static void checkSaved(TestServerContext server, String dimension, int half) {
        // A grid every few chunks plus the whole row and column just past the seams; read a batch at a time.
        var positions = new ArrayList<ChunkPos>();
        int last = half >> 4, step = Math.max(1, last / 8);
        for (int x = -last; x <= last; x++)
            for (int z = -last; z <= last; z++)
                if (x == last || z == last || x == -last || z == -last || (x % step == 0 && z % step == 0)) positions.add(new ChunkPos(x, z));
        var wrong = new ArrayList<String>();
        for (int from = 0; from < positions.size(); from += 64) {
            var batch = positions.subList(from, Math.min(positions.size(), from + 64));
            var reads = server.computeOnServer(s -> {
                var level = s.getLevel(dimension.equals(NETHER) ? Level.NETHER : Level.OVERWORLD);
                return batch.stream().map(pos -> level.getChunkSource().chunkMap.read(pos)).toList();
            });
            for (int i = 0; i < batch.size(); i++) {
                var pos = batch.get(i);
                boolean inside = pos.x() < last && pos.z() < last;
                String status = reads.get(i).join().flatMap(t -> t.getString("Status")).orElse(null);
                if (inside != "minecraft:full".equals(status)) wrong.add(pos + "=" + status);
            }
        }
        check(wrong.isEmpty(), wrong.size() + " chunks of " + dimension + " wrong after Chunky, e.g. " + wrong.subList(0, Math.min(12, wrong.size())));
    }

    private static boolean running(TestServerContext server) {
        return server.computeOnServer(s -> {
            try {
                Object chunky = Class.forName("org.popcraft.chunky.ChunkyProvider").getMethod("get").invoke(null);
                return !((java.util.Map<?, ?>) chunky.getClass().getMethod("getGenerationTasks").invoke(chunky)).isEmpty();
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        });
    }

    private static List<Long> everyChunk(int half) {
        var keys = new ArrayList<Long>();
        for (int x = -half >> 4; x < half >> 4; x++)
            for (int z = -half >> 4; z < half >> 4; z++) keys.add(ChunkPos.pack(x, z));
        return keys;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
