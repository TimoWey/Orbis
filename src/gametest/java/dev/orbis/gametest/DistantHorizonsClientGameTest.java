package dev.orbis.gametest;

import dev.orbis.OrbisWorldShape;
import dev.orbis.WorldSize;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Distant Horizons' far terrain in the 128-chunk debug world. The player first flies over the middle of the world, so
 * DH builds far terrain there from the loaded chunks, then flies north over the seam and on, one world width away from
 * the world's own coordinates (the client's frame continues). Looking further north, the middle of the world lies
 * ahead again: its far terrain must be there. Only runs with Distant Horizons installed (-PorbisCompat=true).
 */
public final class DistantHorizonsClientGameTest implements FabricClientGameTest {
    private static final WorldSize DEBUG = WorldSize.select(WorldSize.DEBUG_MIN_CHUNKS);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("distanthorizons")) return;
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    var normal = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.NORMAL);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
                    OrbisWorldShape.orbis(true);
                    OrbisWorldShape.size(DEBUG);
                    settings.setSeed("8675309");
                    settings.setGenerateStructures(true);
                })
                .create()) {
            var server = world.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            context.runOnClient(mc -> mc.options.renderDistance().set(4));
            // DH's own generation of the whole world takes far too long on the cloud's CPUs; its far terrain here
            // comes from the chunks the client loads, which is the same path past the seam.
            try {
                Object configs = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed").getField("configs").get(null);
                Object generator = configs.getClass().getMethod("worldGenerator").invoke(configs);
                Object enabled = generator.getClass().getMethod("enableDistantWorldGeneration").invoke(generator);
                enabled.getClass().getMethod("setValue", Object.class).invoke(enabled, false);
                // A short far view: software rendering in the cloud draws every far section on the CPU.
                Object graphics = configs.getClass().getMethod("graphics").invoke(configs);
                Object distance = graphics.getClass().getMethod("chunkRenderDistance").invoke(graphics);
                distance.getClass().getMethod("setValue", Object.class).invoke(distance, 48);
            } catch (ReflectiveOperationException e) { throw new AssertionError("DH config unavailable", e); }
            // The middle of the world, flown over in a grid: DH builds far terrain from the chunks the client loads.
            for (int x = -300; x <= 300; x += 150)
                for (int z = -300; z <= 300; z += 150) {
                    server.runCommand("tp @a " + x + " 200 " + z + " 0 90");
                    waitLoaded(context);
                }
            int home = waitColumn(context, 0, 0);
            System.out.println("Orbis DH test: far data at home column 0,0: " + home + " points");
            if (home <= 0) throw new AssertionError("DH holds no far terrain for the middle of the world");
            server.runCommand("tp @a -45 250 -300 180 25");
            waitLoaded(context);
            context.waitTicks(400);
            screenshot(context, "dh-middle-north");
            // North over the seam in relative hops, which keep the client's frame: the client ends one world width
            // further north than the world's own coordinates, while the server folds the player back.
            for (int hop = 0; hop < 14; hop++) {
                server.runCommand("execute as @a at @s run tp @s ~ ~ ~-100");
                waitLoaded(context);
            }
            String where = "client " + context.computeOnClient(mc -> mc.player.position())
                    + ", server " + server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
            System.out.println("Orbis DH test: past the seam, " + where);
            // The middle of the world one width further north, as the client sees it: the same far terrain.
            int image = waitColumn(context, 0, -DEBUG.overworldBlocks());
            boolean torus = !"off".equals(System.getenv("ORBIS_DH_TORUS"));
            System.out.println("Orbis DH test: far data at image column 0," + -DEBUG.overworldBlocks() + ": " + image
                    + " points (torus support " + (torus ? "on" : "off") + ")");
            if (torus && image <= 0) throw new AssertionError("DH shows no far terrain for the middle of the world past the seam");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 180 25");
            context.waitTicks(600);
            screenshot(context, "dh-lap-north");
            server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 25");
            context.waitTicks(200);
            screenshot(context, "dh-lap-south");
        }
    }

    /**
     * The data points DH holds for the overworld column at a block position, waiting a while for it to appear; 0 if
     * none. Read through DH's API by reflection, as Orbis compiles its game tests without DH.
     */
    private static int waitColumn(ClientGameTestContext context, int x, int z) {
        int points = 0;
        for (int tick = 0; tick < 600 && points == 0; tick += 20) {
            // DH answers on its own threads, which need the game to keep ticking: never wait on it here.
            boolean report = tick % 200 == 0;
            var answer = java.util.concurrent.CompletableFuture.supplyAsync(() -> column(x, z, report));
            for (int wait = 0; !answer.isDone() && wait < 600; wait += 5) context.waitTicks(5);
            points = answer.getNow(0);
            if (points == 0) context.waitTicks(20);
        }
        return points;
    }

    private static int column(int x, int z, boolean report) {
        try {
            String api = "com.seibel.distanthorizons.api.";
            Class<?> delayed = Class.forName(api + "DhApi$Delayed");
            Class<?> wrapper = Class.forName(api + "interfaces.world.IDhApiLevelWrapper");
            Class<?> repoType = Class.forName(api + "interfaces.data.IDhApiTerrainDataRepo");
            Class<?> proxyType = Class.forName(api + "interfaces.world.IDhApiWorldProxy");
            var get = repoType.getMethod("getColumnDataAtBlockPos", wrapper, int.class, int.class,
                    Class.forName(api + "interfaces.data.IDhApiTerrainDataCache"));
            Object repo = delayed.getField("terrainRepo").get(null), proxy = delayed.getField("worldProxy").get(null);
            // Without a cache DH refuses to answer.
            Object cache = repoType.getMethod("createSoftCache").invoke(repo);
            int best = 0;
            for (Object level : (Iterable<?>) proxyType.getMethod("getAllLoadedLevelWrappers").invoke(proxy)) {
                String name = String.valueOf(wrapper.getMethod("getDimensionName").invoke(level));
                if (!name.contains("overworld")) continue;
                Object result = get.invoke(repo, level, x, z, cache);
                boolean success = result.getClass().getField("success").getBoolean(result);
                Object[] payload = (Object[]) result.getClass().getField("payload").get(result);
                int count = 0;
                if (success && payload != null) for (Object point : payload) if (point != null) count++;
                if (report) System.out.println("Orbis DH test: column " + x + "," + z + " in " + name + " ("
                        + level.getClass().getSimpleName() + "): success " + success + ", "
                        + result.getClass().getField("message").get(result) + ", " + count + " points");
                best = Math.max(best, count);
            }
            return best;
        } catch (ReflectiveOperationException e) { throw new AssertionError("DH API unavailable", e); }
    }

    /** A screenshot, taken again while the cloud's software renderer still shows nothing but sky. */
    private static void screenshot(ClientGameTestContext context, String name) {
        for (int attempt = 0; ; attempt++) {
            var path = context.takeScreenshot(name);
            try {
                var image = javax.imageio.ImageIO.read(path.toFile());
                java.util.Set<Integer> colours = new java.util.HashSet<>();
                for (int y = image.getHeight() / 2; y < image.getHeight(); y += 7)
                    for (int x = 0; x < image.getWidth(); x += 7) colours.add(image.getRGB(x, y) & 0xf0f0f0);
                if (colours.size() > 8 || attempt >= 4) {
                    System.out.println("Orbis DH test: " + name + " after " + attempt + " retries, " + colours.size() + " colours");
                    return;
                }
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            context.waitTicks(100);
        }
    }

    /** Until the client holds every chunk in its render distance; the server is slow while DH generates. */
    private static void waitLoaded(ClientGameTestContext context) {
        context.waitTicks(10);
        for (int tick = 0; tick < 200; tick += 10) {
            boolean all = context.computeOnClient(mc -> {
                int r = mc.options.getEffectiveRenderDistance();
                var centre = mc.player.chunkPosition();
                for (int x = centre.x() - r; x <= centre.x() + r; x++)
                    for (int z = centre.z() - r; z <= centre.z() + r; z++)
                        if (!mc.level.getChunkSource().hasChunk(x, z)) return false;
                return true;
            });
            if (all) return;
            context.waitTicks(10);
        }
        System.out.println("Orbis DH test: chunks still missing around " + context.computeOnClient(mc -> mc.player.position()));
    }
}
