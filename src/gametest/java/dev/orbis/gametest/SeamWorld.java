package dev.orbis.gametest;

import dev.orbis.engine.Tori;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The Tiny test world and its seams, shared by the client game tests that play on it. */
final class SeamWorld {
    private SeamWorld() {}

    static final ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> TINY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("orbis", "torus_tiny"));
    static final int HALF = 8_192;

    /** A Tiny world on the new engine with structures, fixed seed, noon and clear weather, the player in creative. */
    static TestSingleplayerContext createTiny(ClientGameTestContext context) {
        TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    var preset = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(TINY);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
                    settings.setSeed("8675309");
                    settings.setGenerateStructures(true);
                })
                .create();
        var server = world.getServer();
        int width = server.computeOnServer(sv -> {
            var torus = Tori.of(sv.overworld());
            if (torus == null) throw new AssertionError("Overworld is not on the new engine");
            return torus.widthBlocks();
        });
        check(width == 2 * HALF, "Unexpected width " + width);
        server.runCommand("time set noon");
        server.runCommand("weather clear");
        server.runCommand("gamerule advance_time false");
        server.runCommand("gamemode creative @a");
        world.getConnection().waitForChunksRender();
        return world;
    }
    /**
     * One way over the seam: east, west, south, north, or diagonally through the corner, where both axes wrap at
     * once. Spots are {@code k} blocks past the seam (negative: before it) and {@code b} blocks to the side; the
     * spots on opposite sides mirror each other exactly.
     */
    record Seam(String name, int sx, int sz) {
        static final Seam[] SIDES = {new Seam("east", 1, 0), new Seam("west", -1, 0), new Seam("south", 0, 1), new Seam("north", 0, -1)};
        static final Seam[] CORNERS = {new Seam("southeast-corner", 1, 1), new Seam("northwest-corner", -1, -1)};

        boolean corner() { return sx != 0 && sz != 0; }

        /** The side direction: across the corridor for a side, across the diagonal for a corner. */
        private int px() { return sz == 0 ? 0 : sx == 0 ? 1 : -1; }

        private int pz() { return sx == 0 ? 0 : 1; }

        /** The line entities stand on: the middle of the corridor's centre blocks. */
        double mid() { return corner() ? 0 : 0.5; }

        double x(int half, double k, double b) { return sx * (half + k) + px() * b; }

        double z(int half, double k, double b) { return sz * (half + k) + pz() * b; }

        /** An exact position for a command; always written with a decimal point, so nothing re-centres it. */
        String at(int half, double k, double y, double b) { return x(half, k, b) + " " + y + " " + z(half, k, b); }

        /** The block {@code k} past the seam: on the negative side the first block past it is {@code -half - 1}. */
        BlockPos block(int half, int k, int y, int b) {
            int x = sx > 0 ? half + k : sx < 0 ? -half - 1 - k : 0, z = sz > 0 ? half + k : sz < 0 ? -half - 1 - k : 0;
            return new BlockPos(x + px() * b, y, z + pz() * b);
        }

        static String text(BlockPos pos) { return pos.getX() + " " + pos.getY() + " " + pos.getZ(); }

        /** The command rotation that faces across the seam. */
        String facing(int pitch) { return (int) Math.toDegrees(Math.atan2(-sx, sz)) + " " + pitch; }

        String motion(double speed) { return "[" + sx * speed + "d,0.0d," + sz * speed + "d]"; }

        /** How far a position has come along the way over the seam. */
        double along(double x, double z) { return sx * x + sz * z; }

        /**
         * Whether a position seen by the client, in its frame shifted by {@code lap}, lies between {@code min} and
         * {@code max} blocks past the seam on every axis this way crosses; null when it does.
         */
        @Nullable String past(int half, Vec3 at, double[] lap, double min, double max) {
            if (sx != 0 && !(sx * (at.x - lap[0]) - half > min && sx * (at.x - lap[0]) - half < max)) return "x=" + at.x;
            if (sz != 0 && !(sz * (at.z - lap[1]) - half > min && sz * (at.z - lap[1]) - half < max)) return "z=" + at.z;
            return null;
        }

        /** Whether the server holds a position just past the seam at its folded place, on the far side; null when so. */
        @Nullable String folded(int half, Vec3 at) {
            if (sx != 0 && !(Math.abs(at.x + sx * half) < 64 && at.x >= -half && at.x < half)) return "x=" + at.x;
            if (sz != 0 && !(Math.abs(at.z + sz * half) < 64 && at.z >= -half && at.z < half)) return "z=" + at.z;
            return null;
        }

        /** The two corners of the box over the spots {@code k0..k1} and {@code b0..b1}. */
        String box(int half, int k0, int k1, int y0, int y1, int b0, int b1) {
            BlockPos a = block(half, k0, y0, b0), c = block(half, k1, y1, b1), d = block(half, k0, y0, b1), e = block(half, k1, y1, b0);
            int minX = Math.min(Math.min(a.getX(), c.getX()), Math.min(d.getX(), e.getX()));
            int maxX = Math.max(Math.max(a.getX(), c.getX()), Math.max(d.getX(), e.getX()));
            int minZ = Math.min(Math.min(a.getZ(), c.getZ()), Math.min(d.getZ(), e.getZ()));
            int maxZ = Math.max(Math.max(a.getZ(), c.getZ()), Math.max(d.getZ(), e.getZ()));
            return minX + " " + y0 + " " + minZ + " " + maxX + " " + y1 + " " + maxZ;
        }
    }

    /** A dimension on the torus: the Overworld, and the Nether an eighth its size. */
    record Place(String name, ResourceKey<Level> key, String id, int half) {
        static final Place OVERWORLD = new Place("overworld", Level.OVERWORLD, "minecraft:overworld", HALF);
        static final Place NETHER = new Place("nether", Level.NETHER, "minecraft:the_nether", HALF / 8);

        String in(String command) { return "execute in " + id + " run " + command; }

        /** Clears everything but the player from the corridor around a seam. */
        String clear(Seam seam, int y) {
            return "execute in " + id + " positioned " + seam.at(half, 0, y, 0) + " run kill @e[type=!minecraft:player,distance=..64]";
        }

        ServerLevel level(MinecraftServer server) { return server.getLevel(key); }
    }

    /** A player on the server with no client behind it; unlike Fabric's fake player it can be hurt. */
    static final class SecondPlayer extends FakePlayer {
        SecondPlayer(ServerLevel level, com.mojang.authlib.GameProfile profile) { super(level, profile); }

        @Override
        public boolean isInvulnerableTo(ServerLevel level, DamageSource source) { return false; }
    }

    static void heal(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
            player.setHealth(player.getMaxHealth());
        });
    }

    /** Where the client sees the first entity of a type, other than its own player; NaN when nowhere. */
    static Vec3 clientEntity(ClientGameTestContext context, String type) {
        return context.computeOnClient(mc -> {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity != mc.player && typeName(entity).equals(type)) return entity.position();
            }
            return new Vec3(Double.NaN, Double.NaN, Double.NaN);
        });
    }

    static String typeName(Entity entity) { return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(); }

    /** The column must be loaded before its heightmap means anything, so go there from above first. */
    static int surface(ClientGameTestContext context, TestSingleplayerContext world, int x, int z) {
        world.getServer().runCommand("tp @a " + x + " 320 " + z);
        waitLoaded(context, world, x, z);
        world.getConnection().waitForChunksRender();
        return world.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
    }

    /** The corridor's floor height: the surface in the Overworld, a fixed level inside the Nether's rock. */
    static int floor(ClientGameTestContext context, TestSingleplayerContext world, Place place, Seam seam) {
        BlockPos start = seam.block(place.half(), -6, 0, 0);
        if (place.key() == Level.OVERWORLD) return surface(context, world, start.getX(), start.getZ());
        world.getServer().runCommand(place.in("tp @a " + seam.at(place.half(), -5.5, 70, seam.mid())));
        waitLoaded(context, world, place.key(), start);
        return 70;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** The client may have nothing to render yet right after a teleport; wait until the server has the column. */
    static void waitLoaded(ClientGameTestContext context, TestSingleplayerContext world, int x, int z) {
        waitLoaded(context, world, Level.OVERWORLD, new BlockPos(x, 0, z));
    }

    static void waitLoaded(ClientGameTestContext context, TestSingleplayerContext world, ResourceKey<Level> key, BlockPos pos) {
        for (int tick = 0; !world.getServer().computeOnServer(s -> s.getLevel(key).isLoaded(pos)); tick++) {
            check(tick < 600, "Server never loaded the column at " + pos.toShortString() + " in " + key);
            context.waitTick();
        }
    }
}
