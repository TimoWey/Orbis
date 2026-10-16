package dev.orbis.gametest;

import dev.orbis.OrbisWorldShape;
import dev.orbis.WorldSize;
import dev.orbis.atlas.AtlasProbe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Measures frame times while the player flies on: minimap off, flat and globe, and with the map open (flat and globe)
 * while it is dragged. Also measures the minimap's own time on the render thread. Prints one line per case; it fails
 * nothing (the cloud renders in software, so absolute numbers say little; the cases are compared with each other and
 * with an older build). Not part of the regular suite: it is run on its own, as the only client game test entrypoint.
 */
public final class MapPerfClientGameTest implements FabricClientGameTest {
    private static final WorldSize DEBUG = WorldSize.select(WorldSize.DEBUG_MIN_CHUNKS);
    private static final String OVERWORLD = "minecraft:overworld";
    private static final List<long[]> FRAMES = new ArrayList<>();
    private static volatile boolean recording;
    private static long start;

    static {
        var minimap = Identifier.fromNamespaceAndPath("orbis", "minimap");
        HudElementRegistry.attachElementBefore(minimap, Identifier.fromNamespaceAndPath("orbis-gametest", "frame_start"), (g, delta) -> {
            if (recording) start = System.nanoTime();
        });
        HudElementRegistry.attachElementAfter(minimap, Identifier.fromNamespaceAndPath("orbis-gametest", "frame_end"), (g, delta) -> {
            if (recording) synchronized (FRAMES) { FRAMES.add(new long[] {start, System.nanoTime() - start}); }
        });
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    var normal = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.NORMAL);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
                    OrbisWorldShape.orbis(true);
                    OrbisWorldShape.size(DEBUG);
                    settings.setSeed("8675309");
                })
                .create()) {
            var server = world.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            context.runOnClient(mc -> mc.options.renderDistance().set(8));
            // A good part of the world explored first, so the map has many chunks to draw.
            for (int x = -800; x <= 800; x += 200)
                for (int z = -800; z <= 800; z += 200) {
                    server.runCommand("tp @a " + x + " 150 " + z);
                    context.waitTicks(20);
                }
            server.runCommand("tp @a 0 150 0 -90 20");
            context.waitTicks(200);
            measure(context, server, "minimap off", false, false, false);
            measure(context, server, "minimap flat", true, false, false);
            measure(context, server, "minimap globe", true, true, false);
            measure(context, server, "map open flat, dragged", true, false, true);
            measure(context, server, "map open globe, dragged", true, true, true);
            context.runOnClient(mc -> AtlasProbe.setMinimap(true, false, OVERWORLD));
        }
    }

    private static void measure(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
                                String name, boolean minimap, boolean globe, boolean open) {
        context.runOnClient(mc -> {
            AtlasProbe.setMinimap(minimap, globe, OVERWORLD);
            if (open) AtlasProbe.openMap(mc);
        });
        context.waitTicks(40);
        synchronized (FRAMES) { FRAMES.clear(); }
        recording = true;
        for (int tick = 0; tick < 300; tick++) {
            // Flying east at sprint speed, so the minimap keeps moving.
            server.runCommand("execute as @a at @s run tp @s ~0.3 ~ ~");
            if (open) { int t = tick; context.runOnClient(mc -> AtlasProbe.drag(mc, 6 * Math.sin(t / 15.0), 4, t % 60 == 0)); }
            context.waitTick();
        }
        recording = false;
        if (open) context.runOnClient(mc -> mc.gui.setScreen(null));
        long[][] frames;
        synchronized (FRAMES) { frames = FRAMES.toArray(new long[0][]); }
        if (frames.length < 3) { System.out.println("Orbis map perf: " + name + ": too few frames (" + frames.length + ")"); return; }
        double[] gaps = new double[frames.length - 1], own = new double[frames.length];
        for (int i = 1; i < frames.length; i++) gaps[i - 1] = (frames[i][0] - frames[i - 1][0]) / 1e6;
        for (int i = 0; i < frames.length; i++) own[i] = frames[i][1] / 1e6;
        Arrays.sort(gaps); Arrays.sort(own);
        double seconds = (frames[frames.length - 1][0] - frames[0][0]) / 1e9;
        long stutters = Arrays.stream(gaps).filter(g -> g > 2 * gaps[gaps.length / 2]).count();
        System.out.printf(java.util.Locale.ROOT, "Orbis map perf: %-26s fps %5.1f | frame ms p50 %6.1f p95 %6.1f p99 %6.1f max %6.1f | frames over 2x median %3d | minimap ms mean %5.2f p99 %5.2f max %5.2f%n",
                name, frames.length / seconds, gaps[gaps.length / 2], gaps[(int) (gaps.length * .95)], gaps[(int) (gaps.length * .99)],
                gaps[gaps.length - 1], stutters, Arrays.stream(own).average().orElse(0), own[(int) (own.length * .99)], own[own.length - 1]);
    }
}
