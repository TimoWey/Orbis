package dev.orbis.gametest;

import dev.orbis.atlas.AtlasProbe;
import dev.orbis.engine.Tori;
import dev.orbis.engine.mixin.ChunkMapAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Plays a Tiny world on the new engine: looks over the east seam, checks light and blocks on the far side, walks
 * across it and looks back, visits the corner, then leaves straight after a teleport over the seam. Screenshots
 * land in the run directory's screenshots folder.
 */
public final class TorusClientGameTest implements FabricClientGameTest {
    private static final ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> TINY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("orbis", "torus_tiny"));
    private static final int HALF = 8_192;

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
            check(width == 2 * HALF, "Unexpected width " + width);
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("orbis-spawn");

            // East seam: stand six blocks before it, facing east, on a cleared corridor that runs across it.
            int y = surface(context, world, HALF - 6, 0);
            server.runCommand("tp @a " + (HALF - 6) + " " + y + " 0.5 -90 10");
            waitLoaded(context, world, HALF - 12, 0);
            waitLoaded(context, world, HALF + 18, 0);
            world.getConnection().waitForChunksRender();
            server.runCommand("fill " + (HALF - 12) + " " + (y - 1) + " -2 " + (HALF + 18) + " " + (y - 1) + " 2 minecraft:stone");
            server.runCommand("fill " + (HALF - 12) + " " + y + " -2 " + (HALF + 18) + " " + (y + 3) + " 2 minecraft:air");
            server.runCommand("setblock " + (HALF - 1) + " " + (y + 2) + " 2 minecraft:glowstone");
            server.runCommand("setblock " + (HALF + 4) + " " + y + " 2 minecraft:gold_block");
            context.waitTicks(40);

            int lightAcross = server.computeOnServer(s -> s.overworld().getBrightness(LightLayer.BLOCK, new BlockPos(HALF + 1, y + 2, 2)));
            check(lightAcross >= 12, "Block light did not cross the seam: " + lightAcross);
            boolean goldSeen = context.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(HALF + 4, y, 2)).is(Blocks.GOLD_BLOCK));
            check(goldSeen, "Client does not see the block placed across the seam");
            context.takeScreenshot("orbis-seam-east");

            // Walk over the seam for real: the server folds the player, the client keeps going.
            context.getInput().holdKeyFor(options -> options.keyUp, 60);
            context.waitTicks(10);
            double serverX = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getX());
            double clientX = context.computeOnClient(mc -> mc.player.getX());
            check(serverX >= -HALF && serverX < -HALF + 32, "Server did not fold the player: x=" + serverX);
            check(clientX > HALF && clientX < HALF + 32, "Client frame jumped at the seam: x=" + clientX);
            context.takeScreenshot("orbis-seam-crossed");
            checkHoldersFolded(server);

            // Entities: one placed past the seam joins at its folded position, one flying over it is folded back,
            // and the client sees both continuing on its side.
            server.runCommand("summon minecraft:armor_stand " + (HALF + 2) + " " + y + " 2 {NoGravity:1b}");
            server.runCommand("summon minecraft:arrow " + (HALF - 3) + " " + (y + 1) + " -1 {NoGravity:1b,"
                    + "Motion:[1.0d,0.0d,0.0d]}");
            context.waitTicks(10);
            String outside = server.computeOnServer(s -> {
                var torus = Tori.of(s.overworld());
                for (Entity entity : s.overworld().getEntities(EntityTypeTest.forClass(Entity.class), e -> isProbe(e))) {
                    if (!torus.contains(entity.getBlockX()) || !torus.contains(entity.getBlockZ())) return entity.toString();
                }
                return null;
            });
            check(outside == null, "Entity left outside the torus: " + outside);
            double standX = clientEntityX(context, "armor_stand"), arrowX = clientEntityX(context, "arrow");
            check(standX > HALF && standX < HALF + 4, "Client sees the armor stand at x=" + standX);
            check(arrowX > HALF + 2 && arrowX < HALF + 32, "Client sees the arrow at x=" + arrowX);
            server.runCommand("rotate @p 90 10");
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("orbis-seam-look-back");

            // The map shows the torus: one frame of the world's width, and the terrain just crossed folded into it.
            context.waitTicks(20);
            String frame = context.computeOnClient(mc -> AtlasProbe.frame());
            check(("true " + -HALF + " " + -HALF + " " + 2 * HALF).equals(frame), "Map frame is " + frame);
            long outsideCells = context.computeOnClient(mc -> AtlasProbe.cellsOutside());
            check(outsideCells == 0, outsideCells + " map cells lie outside the torus");
            context.runOnClient(AtlasProbe::openMap);
            context.waitTicks(10);
            context.takeScreenshot("orbis-map-at-seam");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            checkPortalAcrossNetherSeam(server);

            // The corner, where both axes wrap at once.
            int cornerY = surface(context, world, HALF - 4, HALF - 4);
            server.runCommand("tp @a " + (HALF - 4) + " " + (cornerY + 12) + " " + (HALF - 4) + " -135 40");
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("orbis-corner");

            checkHoldersFolded(server);

            // Leave right after a long teleport across the seam. A player counted at an unfolded chunk left a
            // holder behind that never unloaded, and saving on exit then waited for it forever.
            server.runCommand("tp @a " + (HALF - 40) + " 200 " + (-HALF + 40));
            context.waitTicks(2);
            checkHoldersFolded(server);
        }
    }

    /**
     * The Nether is exactly an eighth of the Overworld. A portal just inside its west edge is the one found from an
     * exit just inside its east edge, five blocks away across the seam rather than the whole width.
     */
    private static void checkPortalAcrossNetherSeam(TestServerContext server) {
        String problem = server.computeOnServer(s -> {
            var nether = s.getLevel(Level.NETHER);
            var torus = Tori.of(nether);
            if (torus == null) return "Nether is not on the new engine";
            if (torus.widthBlocks() * 8 != Tori.of(s.overworld()).widthBlocks()) return "Nether width " + torus.widthBlocks();
            BlockPos portal = new BlockPos(torus.minBlock() + 2, 64, 0);
            BlockPos exit = new BlockPos(torus.minBlock() + torus.widthBlocks() - 3, 64, 0);
            nether.getChunk(portal);
            nether.getChunk(exit);
            nether.setBlock(portal, Blocks.NETHER_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            var found = nether.getPortalForcer().findClosestPortalPosition(exit, true, nether.getWorldBorder());
            return found.filter(portal::equals).isPresent() ? null : "Found " + found + " instead of " + portal;
        });
        check(problem == null, "Portal across the Nether seam: " + problem);
    }

    /** Where the client sees the first entity of a type, or NaN. */
    private static double clientEntityX(ClientGameTestContext context, String type) {
        return context.computeOnClient(mc -> {
            for (Entity entity : mc.level.entitiesForRendering()) if (typeName(entity).equals(type)) return entity.getX();
            return Double.NaN;
        });
    }

    private static String typeName(Entity entity) { return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(); }

    private static boolean isProbe(Entity entity) { return typeName(entity).equals("armor_stand") || typeName(entity).equals("arrow"); }

    /** Every chunk exists once, at its folded position; a holder anywhere else would never be unloaded. */
    private static void checkHoldersFolded(TestServerContext server) {
        String stray = server.computeOnServer(s -> {
            var torus = Tori.of(s.overworld());
            var chunks = ((ChunkMapAccessor) s.overworld().getChunkSource().chunkMap).orbis$updatingChunks();
            for (long key : chunks.keySet()) {
                int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
                if (!torus.containsChunk(x) || !torus.containsChunk(z)) return "[" + x + ", " + z + "]";
            }
            return null;
        });
        check(stray == null, "Chunk holder outside the torus at " + stray);
    }

    /** The column must be loaded before its heightmap means anything, so go there from above first. */
    private static int surface(ClientGameTestContext context, TestSingleplayerContext world, int x, int z) {
        world.getServer().runCommand("tp @a " + x + " 320 " + z);
        waitLoaded(context, world, x, z);
        world.getConnection().waitForChunksRender();
        return world.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** The client may have nothing to render yet right after a teleport; wait until the server has the column. */
    private static void waitLoaded(ClientGameTestContext context, TestSingleplayerContext world, int x, int z) {
        BlockPos pos = new BlockPos(x, 0, z);
        for (int tick = 0; !world.getServer().computeOnServer(s -> s.overworld().isLoaded(pos)); tick++) {
            check(tick < 600, "Server never loaded the column at " + x + ", " + z);
            context.waitTick();
        }
    }
}
