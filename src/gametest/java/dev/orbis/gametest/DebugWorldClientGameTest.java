package dev.orbis.gametest;

import com.toroidalworld.shape.WorldShapes;
import dev.orbis.OrbisShape;
import dev.orbis.WorldSize;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.mixin.ChunkMapAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The smallest world the side-length input allows, 128 × 128 Overworld chunks and a 16 × 16 chunk Nether, made through
 * the Orbis World Shape as a player would. Walks over the Overworld seam, looks at the caves under it and the whole
 * world from above, goes through a portal built across the seam, walks over the Nether seam and comes back.
 */
public final class DebugWorldClientGameTest implements FabricClientGameTest {
    private static final WorldSize DEBUG = WorldSize.select(WorldSize.DEBUG_MIN_CHUNKS);
    private static final int HALF = DEBUG.overworldBlocks() / 2, NETHER_HALF = DEBUG.netherBlocks() / 2;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    var normal = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.NORMAL);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
                    WorldShapes.select(WorldShapes.shapes().stream().filter(shape -> shape.id().equals(OrbisShape.MODULE.id()))
                            .findFirst().orElseThrow());
                    OrbisShape.MODULE.settings(DEBUG);
                    settings.setSeed("8675309");
                    settings.setGenerateStructures(true);
                })
                .create()) {
            var server = world.getServer();
            String sizes = server.computeOnServer(s -> {
                Torus overworld = Tori.of(s.overworld()), nether = Tori.of(s.getLevel(Level.NETHER));
                return overworld == null || nether == null ? "not on the Orbis engine"
                        : overworld.widthChunks() + " / " + nether.widthChunks();
            });
            check("128 / 16".equals(sizes), "Debug world chunks (Overworld / Nether): " + sizes);
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            waitRendered(context, world);
            checkView(server, Math.min(62, server.computeOnServer(s -> s.getPlayerList().getViewDistance())));

            // Overworld seam: a cleared corridor across it, walked for real.
            int y = surface(context, world, HALF - 6, 0);
            server.runCommand("tp @a " + (HALF - 6) + " " + y + " 0.5 -90 10");
            waitRendered(context, world);
            server.runCommand("fill " + (HALF - 12) + " " + (y - 1) + " -2 " + (HALF + 18) + " " + (y - 1) + " 2 minecraft:stone");
            server.runCommand("fill " + (HALF - 12) + " " + y + " -2 " + (HALF + 18) + " " + (y + 3) + " 2 minecraft:air");
            server.runCommand("setblock " + (HALF + 4) + " " + y + " 2 minecraft:gold_block");
            context.waitTicks(20);
            context.takeScreenshot("debug-seam-east");
            context.getInput().holdKeyFor(options -> options.keyUp, 60);
            context.waitTicks(10);
            double serverX = server.computeOnServer(s -> player(s).getX());
            double clientX = context.computeOnClient(mc -> mc.player.getX());
            check(serverX >= -HALF && serverX < -HALF + 32, "Server did not fold the player: x=" + serverX);
            check(clientX > HALF && clientX < HALF + 32, "Client frame jumped at the seam: x=" + clientX);
            context.takeScreenshot("debug-seam-crossed");
            checkHoldersFolded(server, Level.OVERWORLD);

            probeLoad(context, server, -HALF + 40, HALF - 40);
            // Caves under the seam, seen in spectator mode from inside the rock.
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a " + (HALF - 10) + " 20 0.5 -90 0");
            context.waitTicks(40);
            waitRendered(context, world);
            context.takeScreenshot("debug-caves-at-seam");
            server.runCommand("gamemode creative @a");

            checkStructureAcrossSeam(context, world, server);
            checkPortalRoundTrip(context, world, server, true);
            checkPortalRoundTrip(context, world, server, false);
            checkPortalFromNetherSeam(context, world, server);
            checkHoldersFolded(server, Level.OVERWORLD);
            checkHoldersFolded(server, Level.NETHER);
        }
    }

    /**
     * A structure that generated across the seam, seen from beside it: its pieces on both sides belong to one start.
     * A surface structure is preferred; otherwise a mineshaft, looked at from inside the rock in spectator mode.
     */
    private static void checkStructureAcrossSeam(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server) {
        String found = server.computeOnServer(s -> {
            var level = s.overworld();
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            String best = null;
            int bestScore = 0, edge = HALF >> 4;
            for (int cx : java.util.stream.IntStream.concat(java.util.stream.IntStream.range(edge - 8, edge),
                    java.util.stream.IntStream.range(-edge, -edge + 8)).toArray()) {
                for (int cz = -edge; cz < edge; cz++) {
                    var chunk = level.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
                    for (var entry : chunk.getAllStarts().entrySet()) {
                        var box = entry.getValue().getBoundingBox();
                        if (!(box.maxX() >= HALF || box.minX() < -HALF)) continue;
                        String name = registry.getKey(entry.getKey()).getPath();
                        int score = (box.maxY() > 50 ? 1_000_000 : 0) + box.getXSpan() * box.getZSpan();
                        if (score > bestScore) {
                            bestScore = score;
                            int seamX = box.maxX() >= HALF ? HALF : -HALF;
                            best = name + " " + seamX + " " + (box.minZ() + box.maxZ()) / 2 + " " + box.minY() + " " + box.maxY();
                        }
                    }
                }
            }
            return best;
        });
        check(found != null, "No structure generated across the seam");
        System.out.println("Orbis debug test: structure across the seam: " + found);
        String[] parts = found.split(" ");
        int seamX = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
        int bottom = Integer.parseInt(parts[3]), top = Integer.parseInt(parts[4]);
        boolean surface = top > 50;
        server.runCommand("gamemode spectator @a");
        server.runCommand("tp @a " + (seamX - 16) + " " + (surface ? top + 16 : (bottom + top) / 2 + 2) + " " + z + " -90 " + (surface ? 35 : 10));
        context.waitTicks(40);
        waitRendered(context, world);
        context.takeScreenshot("debug-structure-at-seam-" + parts[0]);
        server.runCommand("gamemode creative @a");
    }

    /**
     * A portal whose two columns sit on either side of the east seam (or the south seam), into the 16 × 16 chunk
     * Nether, over its seam and back.
     */
    private static void checkPortalRoundTrip(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server,
                                             boolean alongX) {
        String side = alongX ? "" : "-south";
        // a runs across the seam, b along it; positions are written as (a, b) and turned into (x, z).
        java.util.function.BiFunction<Object, Object, String> at = (a, b) -> alongX ? a + " %Y% " + b : b + " %Y% " + a;
        int y = alongX ? surface(context, world, HALF, 8) : surface(context, world, 8, HALF);
        server.runCommand(fill(at, HALF - 6, 4, HALF + 5, 12, y - 1, y - 1, "minecraft:stone"));
        server.runCommand(fill(at, HALF - 6, 4, HALF + 5, 12, y, y + 5, "minecraft:air"));
        server.runCommand(fill(at, HALF - 2, 8, HALF + 1, 8, y - 1, y + 3, "minecraft:obsidian"));
        server.runCommand(fill(at, HALF - 1, 8, HALF, 8, y, y + 2, "minecraft:nether_portal[axis=" + (alongX ? "x" : "z") + "]"));
        server.runCommand("tp @a " + at.apply(HALF - 1, 11.5).replace("%Y%", Integer.toString(y)) + " " + (alongX ? "180" : "90") + " 0");
        context.waitTicks(20);
        waitRendered(context, world);
        context.takeScreenshot("debug-portal-at-seam" + side);
        boolean whole = server.computeOnServer(s -> s.overworld().getBlockState(pos(alongX, HALF - 1, y + 1, 8)).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                && s.overworld().getBlockState(pos(alongX, -HALF, y + 1, 8)).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL));
        check(whole, "The portal across the seam" + side + " broke");

        server.runCommand("tp @a " + at.apply((HALF - 1) + ".5", "8.5").replace("%Y%", Integer.toString(y)));
        waitForDimension(context, server, Level.NETHER);
        BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
        Torus nether = server.computeOnServer(s -> Tori.of(s.getLevel(Level.NETHER)));
        check(nether.contains(arrival.getX()) && nether.contains(arrival.getZ()), "Arrived outside the Nether torus at " + arrival);
        context.waitTicks(40);
        // The view is narrower than the client's render distance here, so there is no 'all chunks rendered' to wait for.
        context.waitTicks(40);
        context.takeScreenshot("debug-nether-arrival" + side);
        checkView(server, Math.min(6, server.computeOnServer(s -> s.getPlayerList().getViewDistance())));
        if (alongX) walkNetherSeam(context, server, arrival);
        else {
            // Standing in a portal keeps renewing its cooldown, so the player waits on a ledge away from it.
            int lx = arrival.getX() + 20, lz = arrival.getZ();
            server.runCommand("execute in minecraft:the_nether run fill " + (lx - 1) + " 99 " + (lz - 1) + " " + (lx + 1) + " 99 " + (lz + 1) + " minecraft:netherrack");
            server.runCommand("execute in minecraft:the_nether run fill " + (lx - 1) + " 100 " + (lz - 1) + " " + (lx + 1) + " 102 " + (lz + 1) + " minecraft:air");
            server.runCommand("execute in minecraft:the_nether run tp @a " + (lx + 0.5) + " 100.0 " + (lz + 0.5));
        }

        // Back through the portal the Nether side found or built; the cooldown after arriving runs out first.
        context.waitTicks(320);
        server.runCommand("execute in minecraft:the_nether run tp @a " + (arrival.getX() + 0.5) + " " + arrival.getY() + " " + (arrival.getZ() + 0.5));
        waitForDimension(context, server, Level.OVERWORLD);
        double distance = server.computeOnServer(s -> {
            var torus = Tori.of(s.overworld());
            ServerPlayer player = player(s);
            double a = alongX ? player.getX() : player.getZ(), b = alongX ? player.getZ() : player.getX();
            return Math.hypot(torus.delta(HALF - 0.5, a), torus.delta(8.5, b));
        });
        check(distance < 16, "Came back " + distance + " blocks from the portal across the seam" + side);
        context.waitTicks(20);
        waitRendered(context, world);
        context.takeScreenshot("debug-portal-back" + side);
    }

    /**
     * The other way round: a portal whose two columns sit on either side of the Nether's east seam, out into the
     * Overworld and back, landing next to it again.
     */
    private static void checkPortalFromNetherSeam(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server) {
        String nether = "execute in minecraft:the_nether run ";
        int y = 100;
        server.runCommand(nether + "tp @a " + (NETHER_HALF - 4) + " " + y + " 12.5 180 0");
        waitForDimension(context, server, Level.NETHER);
        SeamWorld.waitLoaded(context, world, Level.NETHER, new BlockPos(NETHER_HALF - 1, y, 8));
        SeamWorld.waitLoaded(context, world, Level.NETHER, new BlockPos(-NETHER_HALF, y, 8));
        server.runCommand(nether + "fill " + (NETHER_HALF - 6) + " " + (y - 1) + " 4 " + (NETHER_HALF + 5) + " " + (y - 1) + " 14 minecraft:netherrack");
        server.runCommand(nether + "fill " + (NETHER_HALF - 6) + " " + y + " 4 " + (NETHER_HALF + 5) + " " + (y + 5) + " 14 minecraft:air");
        server.runCommand(nether + "fill " + (NETHER_HALF - 2) + " " + (y - 1) + " 8 " + (NETHER_HALF + 1) + " " + (y + 3) + " 8 minecraft:obsidian");
        server.runCommand(nether + "fill " + (NETHER_HALF - 1) + " " + y + " 8 " + NETHER_HALF + " " + (y + 2) + " 8 minecraft:nether_portal[axis=x]");
        context.waitTicks(40);
        context.takeScreenshot("debug-portal-at-nether-seam");
        boolean whole = server.computeOnServer(s -> s.getLevel(Level.NETHER).getBlockState(new BlockPos(NETHER_HALF - 1, y + 1, 8)).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)
                && s.getLevel(Level.NETHER).getBlockState(new BlockPos(-NETHER_HALF, y + 1, 8)).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL));
        check(whole, "The portal across the Nether seam broke");

        server.runCommand(nether + "tp @a " + (NETHER_HALF - 0.5) + " " + y + " 8.5");
        waitForDimension(context, server, Level.OVERWORLD);
        BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
        Torus overworld = server.computeOnServer(s -> Tori.of(s.overworld()));
        check(overworld.contains(arrival.getX()) && overworld.contains(arrival.getZ()), "Arrived outside the Overworld torus at " + arrival);
        double off = Math.hypot(overworld.delta(HALF, arrival.getX()), overworld.delta(64, arrival.getZ()));
        check(off < 128, "Arrived " + off + " blocks from where the Nether seam portal leads");
        context.waitTicks(40);
        waitRendered(context, world);
        context.takeScreenshot("debug-overworld-from-nether-seam");
        // Standing in a portal keeps renewing its cooldown, so the player waits on a ledge away from it.
        int lx = arrival.getX() + 20, ly = arrival.getY() + 30, lz = arrival.getZ();
        server.runCommand("fill " + (lx - 1) + " " + (ly - 1) + " " + (lz - 1) + " " + (lx + 1) + " " + (ly - 1) + " " + (lz + 1) + " minecraft:stone");
        server.runCommand("tp @a " + (lx + 0.5) + " " + ly + " " + (lz + 0.5));
        context.waitTicks(320);
        server.runCommand("tp @a " + (arrival.getX() + 0.5) + " " + arrival.getY() + " " + (arrival.getZ() + 0.5));
        waitForDimension(context, server, Level.NETHER);
        double distance = server.computeOnServer(s -> {
            var torus = Tori.of(s.getLevel(Level.NETHER));
            ServerPlayer player = player(s);
            return Math.hypot(torus.delta(NETHER_HALF - 0.5, player.getX()), torus.delta(8.5, player.getZ()));
        });
        check(distance < 16, "Came back " + distance + " blocks from the portal across the Nether seam");
        context.waitTicks(40);
        context.takeScreenshot("debug-nether-seam-portal-back");
        server.runCommand("execute in minecraft:overworld run tp @a 0.5 200 0.5");
        waitForDimension(context, server, Level.OVERWORLD);
    }

    private static BlockPos pos(boolean alongX, int a, int y, int b) { return alongX ? new BlockPos(a, y, b) : new BlockPos(b, y, a); }

    private static String fill(java.util.function.BiFunction<Object, Object, String> at, int a0, int b0, int a1, int b1, int y0, int y1, String block) {
        return "fill " + at.apply(a0, b0).replace("%Y%", Integer.toString(y0)) + " " + at.apply(a1, b1).replace("%Y%", Integer.toString(y1)) + " " + block;
    }

    /** Over the Nether seam, 256 blocks round, on a cleared corridor away from the portal. */
    private static void walkNetherSeam(ClientGameTestContext context, TestServerContext server, BlockPos arrival) {
        int ny = 100, z = arrival.getZ() + 12;
        server.runCommand("execute in minecraft:the_nether run fill " + (NETHER_HALF - 14) + " " + (ny - 1) + " " + (z - 2) + " "
                + (NETHER_HALF + 14) + " " + (ny - 1) + " " + (z + 2) + " minecraft:netherrack");
        server.runCommand("execute in minecraft:the_nether run fill " + (NETHER_HALF - 14) + " " + ny + " " + (z - 2) + " "
                + (NETHER_HALF + 14) + " " + (ny + 3) + " " + (z + 2) + " minecraft:air");
        server.runCommand("execute in minecraft:the_nether run tp @a " + (NETHER_HALF - 6) + " " + ny + " " + (z + 0.5) + " -90 10");
        context.waitTicks(20);
        context.waitTicks(40);
        context.takeScreenshot("debug-nether-seam-east");
        context.getInput().holdKeyFor(options -> options.keyUp, 30);
        context.waitTicks(10);
        double serverX = server.computeOnServer(s -> player(s).getX());
        double clientX = context.computeOnClient(mc -> mc.player.getX());
        check(serverX >= -NETHER_HALF && serverX < -NETHER_HALF + 32, "Server did not fold the player in the Nether: x=" + serverX);
        check(clientX > NETHER_HALF && clientX < NETHER_HALF + 32, "Client frame jumped at the Nether seam: x=" + clientX);
        context.waitTicks(40);
        String holes = context.computeOnClient(mc -> {
            ChunkPos at = mc.player.chunkPosition();
            var absent = new java.util.ArrayList<String>();
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (!mc.level.getChunkSource().hasChunk(at.x() + dx, at.z() + dz)) absent.add((at.x() + dx) + "," + (at.z() + dz));
            return absent.isEmpty() ? null : "around " + at + " missing " + absent;
        });
        check(holes == null, "Client lacks Nether chunks after the seam: " + holes);
        server.runCommand("rotate @p 90 10");
        context.waitTicks(20);
        context.takeScreenshot("debug-nether-seam-look-back");
        checkHoldersFolded(server, Level.NETHER);
    }

    private static void waitForDimension(ClientGameTestContext context, TestServerContext server, ResourceKey<Level> dimension) {
        for (int tick = 0; !server.computeOnServer(s -> player(s).level().dimension() == dimension); tick += 5) {
            check(tick < 600, "The portal never took the player to " + dimension.identifier());
            context.waitTicks(5);
        }
    }

    /** A view wider than the world would hold chunks twice; it is limited to just under the width. */
    private static void checkView(TestServerContext server, int expected) {
        int radius = server.computeOnServer(s -> player(s).getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
                ? view.viewDistance() : -1);
        check(radius == expected, "View radius " + radius + " instead of " + expected);
    }

    private static ServerPlayer player(MinecraftServer server) { return server.getPlayerList().getPlayers().getFirst(); }

    private static void checkHoldersFolded(TestServerContext server, ResourceKey<Level> dimension) {
        String stray = server.computeOnServer(s -> {
            ServerLevel level = s.getLevel(dimension);
            var torus = Tori.of(level);
            for (long key : ((ChunkMapAccessor) level.getChunkSource().chunkMap).orbis$updatingChunks().keySet()) {
                int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
                if (!torus.containsChunk(x) || !torus.containsChunk(z)) return "[" + x + ", " + z + "]";
            }
            return null;
        });
        check(stray == null, "Chunk holder outside the torus in " + dimension.identifier() + " at " + stray);
    }

    private static int surface(ClientGameTestContext context, TestSingleplayerContext world, int x, int z) {
        world.getServer().runCommand("tp @a " + x + " 320 " + z);
        BlockPos pos = new BlockPos(x, 0, z);
        for (int tick = 0; !world.getServer().computeOnServer(s -> s.overworld().isLoaded(pos)); tick++) {
            check(tick < 600, "Server never loaded the column at " + x + ", " + z);
            context.waitTick();
        }
        waitRendered(context, world);
        return world.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
    }

    /** As waitForChunksRender, but says which chunks never arrived. */
    private static void waitRendered(ClientGameTestContext context, TestSingleplayerContext world) {
        String missing = null;
        for (int tick = 0; tick < 1200; tick += 10) {
            missing = context.computeOnClient(mc -> {
                int r = mc.options.getEffectiveRenderDistance();
                ChunkPos centre = mc.player.chunkPosition();
                var absent = new java.util.ArrayList<String>();
                for (int x = centre.x() - r; x <= centre.x() + r; x++)
                    for (int z = centre.z() - r; z <= centre.z() + r; z++)
                        if (!mc.level.getChunkSource().hasChunk(x, z)) absent.add(x + "," + z);
                return absent.isEmpty() ? null : "player chunk " + centre + " r=" + r + " missing " + absent;
            });
            if (missing == null) break;
            context.waitTicks(10);
        }
        if (missing != null) {
            String server = world.getServer().computeOnServer(s -> {
                ServerPlayer player = player(s);
                return player.level().dimension().identifier() + " " + player.position() + " view " + player.getChunkTrackingView();
            });
            throw new AssertionError("Chunks never arrived: " + missing + "; server: " + server);
        }
        world.getConnection().waitForChunksRender();
    }

    /**
     * Loads a chunk near the corner as a sync load would, with the same ticket, but without blocking the server, and
     * reports the holders that never got there. The old engine once kept neighbours across the seam from generating.
     */
    private static void probeLoad(ClientGameTestContext context, TestServerContext server, int x, int z) {
        server.runOnServer(s -> s.overworld().getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.FORCED, new ChunkPos(x >> 4, z >> 4), 0));
        for (int tick = 0; tick < 600; tick += 10) {
            if (server.computeOnServer(s -> s.overworld().getChunkSource().getChunkNow(x >> 4, z >> 4) != null)) {
                server.runOnServer(s -> s.overworld().getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED, new ChunkPos(x >> 4, z >> 4), 0));
                return;
            }
            context.waitTicks(10);
        }
        String report = server.computeOnServer(s -> {
            var torus = Tori.of(s.overworld());
            var holders = ((ChunkMapAccessor) s.overworld().getChunkSource().chunkMap).orbis$updatingChunks();
            var lines = new java.util.ArrayList<String>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int cx = torus.foldChunk((x >> 4) + dx), cz = torus.foldChunk((z >> 4) + dz);
                    var holder = holders.get(ChunkPos.pack(cx, cz));
                    if (holder == null) lines.add(cx + "," + cz + " none");
                    else
                        lines.add(cx + "," + cz + " latest=" + holder.getLatestStatus() + " persisted=" + holder.getPersistedStatus()
                                + " level=" + holder.getTicketLevel());
                }
            }
            return lines.size() + " holders: " + String.join("; ", lines.subList(0, Math.min(80, lines.size())));
        });
        throw new AssertionError("Chunk at " + x + ", " + z + " never loaded: " + report);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
