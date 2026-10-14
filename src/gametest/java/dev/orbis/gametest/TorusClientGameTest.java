package dev.orbis.gametest;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.orbis.atlas.AtlasProbe;
import dev.orbis.engine.Tori;
import dev.orbis.engine.mixin.ChunkMapAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Plays a Tiny world on the new engine: looks over the east seam, checks light and blocks on the far side, walks
 * across it and looks back, visits the corner, then leaves straight after a teleport over the seam. Screenshots
 * land in the run directory's screenshots folder.
 */
public final class TorusClientGameTest implements FabricClientGameTest {
    private static final ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> TINY =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("orbis", "torus_tiny"));
    private static final int HALF = 8_192;

    /** Classes with engine mixins that this test does not otherwise load; a broken injection fails when they do. */
    private static final String[] MIXIN_TARGETS = {
            "net.minecraft.world.entity.animal.equine.Llama",
            "net.minecraft.world.entity.animal.golem.SnowGolem",
            "net.minecraft.world.entity.boss.wither.WitherBoss",
            "net.minecraft.world.entity.monster.Blaze$BlazeAttackGoal",
            "net.minecraft.world.entity.monster.Ghast",
            "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal",
            "net.minecraft.world.entity.monster.Witch",
            "net.minecraft.world.entity.monster.breeze.Shoot",
            "net.minecraft.world.entity.monster.illager.Illusioner",
            "net.minecraft.world.entity.monster.zombie.Drowned",
            "net.minecraft.world.entity.projectile.ShulkerBullet",
            "net.minecraft.world.entity.ai.goal.LeapAtTargetGoal",
            "net.minecraft.world.item.CrossbowItem",
            "net.minecraft.commands.arguments.selector.EntitySelector",
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        for (String target : MIXIN_TARGETS) {
            try {
                Class.forName(target, false, TorusClientGameTest.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Missing mixin target " + target, e);
            }
        }
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    var preset = settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(TINY);
                    settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
                    settings.setSeed("8675309");
                    settings.setGenerateStructures(true);
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
            for (String selector : new String[] {
                    "@e[type=minecraft:armor_stand,x=" + (HALF - 4) + ",y=" + (y - 1) + ",z=-1,dx=6,dy=3,dz=3]",
                    "@e[type=minecraft:armor_stand,x=" + (HALF - 1) + ",y=" + y + ",z=2,distance=..4]"}) {
                int selected = server.computeOnServer(s -> {
                    try {
                        return EntityArgument.entities().parse(new StringReader(selector)).findEntities(s.createCommandSourceStack()).size();
                    } catch (CommandSyntaxException e) {
                        throw new AssertionError(e);
                    }
                });
                check(selected == 1, selector + " selected " + selected + " armor stands across the seam");
            }
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
            server.runCommand("gamerule spawn_mobs false");
            for (Seam seam : Seam.ALL) checkSeam(context, world, server, Place.OVERWORLD, seam);
            // A far teleport starts the client's frame afresh: it lands exactly where the command said.
            server.runCommand("tp @a 100.25 200 -100.25");
            context.waitTicks(5);
            Vec3 landed = context.computeOnClient(mc -> mc.player.position());
            check(landed.x == 100.25 && landed.z == -100.25, "The client landed at " + landed + " after a far teleport");
            for (Seam seam : Seam.ALL) checkSeam(context, world, server, Place.NETHER, seam);
            checkLocateAcrossSeam(server, Place.OVERWORLD, "#minecraft:village");
            checkLocateAcrossSeam(server, Place.NETHER, "minecraft:fortress");

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

    /** One of the four seams: the axis that crosses it and the side of the world it is on. */
    private record Seam(String name, boolean alongX, int sign) {
        static final Seam[] ALL = {new Seam("east", true, 1), new Seam("west", true, -1), new Seam("south", false, 1), new Seam("north", false, -1)};

        /** Block coordinates for a spot {@code a} along the crossing axis and {@code b} across it. */
        String at(double a, double y, double b) { return alongX ? n(a) + " " + n(y) + " " + n(b) : n(b) + " " + n(y) + " " + n(a); }

        /** Whole numbers without a decimal point, so that block position arguments accept them. */
        private static String n(double value) { return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value); }

        /** The command rotation that faces across the seam. */
        String facing(int pitch) { return (alongX ? (sign > 0 ? -90 : 90) : (sign > 0 ? 0 : 180)) + " " + pitch; }

        BlockPos pos(int a, int y, int b) { return alongX ? new BlockPos(a, y, b) : new BlockPos(b, y, a); }

        double along(Entity entity) { return alongX ? entity.getX() : entity.getZ(); }

        String motion(double speed) { return alongX ? "[" + sign * speed + "d,0.0d,0.0d]" : "[0.0d,0.0d," + sign * speed + "d]"; }
    }

    /** A dimension on the torus: the Overworld, and the Nether an eighth its size. */
    private record Place(String name, ResourceKey<Level> key, String id, int half) {
        static final Place OVERWORLD = new Place("overworld", Level.OVERWORLD, "minecraft:overworld", HALF);
        static final Place NETHER = new Place("nether", Level.NETHER, "minecraft:the_nether", HALF / 8);
        static final Place[] ALL = {OVERWORLD, NETHER};

        String in(String command) { return "execute in " + id + " run " + command; }

        /** Clears everything but the player from the corridor around a seam. */
        String clear(Seam seam, int y) {
            return "execute in " + id + " positioned " + seam.at(seam.sign() * half, y, 0) + " run kill @e[type=!minecraft:player,distance=..64]";
        }

        ServerLevel level(MinecraftServer server) { return server.getLevel(key); }
    }

    /**
     * Everything at one seam of one dimension, in a glass corridor that runs across it: light and blocks past it,
     * entities placed past it and flying over it, selectors, walking over it, a husk hunting a villager across it,
     * and fighting over it: the player's hit lands, a zombie's blow knocks the player away from it rather than toward
     * it, and a skeleton's arrows fly across and hit.
     */
    private static void checkSeam(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server,
                                  Place place, Seam seam) {
        int s = seam.sign(), half = place.half();
        String where = place.name() + " " + seam.name();
        String tag = place.name() + "-" + seam.name();
        int y = floor(context, world, place, seam);
        int from = Math.min(s * (half - 12), s * (half + 18)), to = Math.max(s * (half - 12), s * (half + 18));
        // Fill refuses a corridor whose far end, past the seam, the server has not loaded yet.
        for (int end : new int[] {from, to}) waitLoaded(context, world, place.key(), seam.pos(end, 0, 0));
        server.runCommand(place.clear(seam, y));
        server.runCommand(place.in("fill " + seam.at(from, y - 1, -3) + " " + seam.at(to, y + 4, 3) + " minecraft:glass"));
        server.runCommand(place.in("fill " + seam.at(from, y - 1, -2) + " " + seam.at(to, y - 1, 2) + " minecraft:stone"));
        server.runCommand(place.in("fill " + seam.at(from, y, -2) + " " + seam.at(to, y + 3, 2) + " minecraft:air"));
        server.runCommand(place.in("setblock " + seam.at(s * (half - 1), y + 2, 2) + " minecraft:glowstone"));
        server.runCommand(place.in("setblock " + seam.at(s * (half + 4), y, 2) + " minecraft:gold_block"));
        String built = server.computeOnServer(sv -> {
            var level = place.level(sv);
            for (int a = from; a <= to; a++) {
                if (!level.getBlockState(seam.pos(a, y - 1, 0)).is(Blocks.STONE)) return "no floor at " + a;
                if (!level.getBlockState(seam.pos(a, y + 1, 0)).isAir()) return "blocked at " + a;
            }
            return null;
        });
        check(built == null, where + ": corridor not built: " + built);
        server.runCommand(place.in("tp @a " + seam.at(s * (half - 6), y, 0.5) + " " + seam.facing(10)));
        context.waitTicks(40);
        world.getConnection().waitForChunksRender();
        // A short teleport over the seam is a short move for the client too, so its frame may be whole widths off
        // the command's coordinates; everything the client sees below is measured in that frame.
        double lap = context.computeOnClient(mc -> seam.along(mc.player)) - (s * (half - 6) + 0.5);
        check(Math.abs(lap / (2 * half) - Math.rint(lap / (2 * half))) < 1e-6, where + ": the client landed " + lap + " off the teleport");

        // Light from a lamp before the seam shines past it, and the client sees a block placed past it.
        int lightAcross = server.computeOnServer(sv -> place.level(sv).getBrightness(LightLayer.BLOCK, seam.pos(s * (half + 1), y + 2, 2)));
        check(lightAcross >= 12, where + ": block light did not cross the seam: " + lightAcross);
        boolean goldSeen = context.computeOnClient(mc -> mc.level.getBlockState(seam.pos(s * (half + 4) + (int) lap, y, 2)).is(Blocks.GOLD_BLOCK));
        check(goldSeen, where + ": the client does not see the block placed across the seam:" + context.computeOnClient(mc -> {
            BlockPos gold = seam.pos(s * (half + 4) + (int) lap, y, 2);
            BlockPos folded = seam.pos(s * (half + 4) - s * 2 * half, y, 2);
            return " client " + mc.level.getBlockState(gold) + " folded " + mc.level.getBlockState(folded) + " player " + mc.player.position();
        }) + " server " + server.computeOnServer(sv -> place.level(sv).getBlockState(seam.pos(s * (half + 4), y, 2)).toString()));
        context.takeScreenshot("orbis-seam-" + tag);

        // An entity placed past the seam joins at its folded position, one flying over it is folded back, the
        // selectors find them the short way, and the client sees both continuing on its side.
        server.runCommand(place.in("summon minecraft:armor_stand " + seam.at(s * (half + 2), y, 2) + " {NoGravity:1b}"));
        server.runCommand(place.in("summon minecraft:arrow " + seam.at(s * (half - 3) + 0.5, y + 1, -1.5) + " {NoGravity:1b,Motion:" + seam.motion(1) + "}"));
        context.waitTicks(10);
        String outside = server.computeOnServer(sv -> {
            var level = place.level(sv);
            var torus = Tori.of(level);
            for (Entity entity : level.getEntities(EntityTypeTest.forClass(Entity.class), e -> isProbe(e))) {
                if (!torus.contains(entity.getBlockX()) || !torus.contains(entity.getBlockZ())) return entity.toString();
            }
            return null;
        });
        check(outside == null, where + ": entity left outside the torus: " + outside);
        int boxStart = Math.min(s * (half - 4), s * (half + 4));
        for (String selector : new String[] {
                "@e[type=minecraft:armor_stand," + (seam.alongX() ? "x=" + boxStart + ",y=" + (y - 1) + ",z=1,dx=8,dy=3,dz=3"
                        : "x=1,y=" + (y - 1) + ",z=" + boxStart + ",dx=3,dy=3,dz=8") + "]",
                "@e[type=minecraft:armor_stand,distance=..4]"}) {
            int selected = server.computeOnServer(sv -> {
                Vec3 at = Vec3.atLowerCornerOf(seam.pos(s * (half - 1), y, 2));
                var source = sv.createCommandSourceStack().withSuppressedOutput().withLevel(place.level(sv)).withPosition(at);
                try {
                    return EntityArgument.entities().parse(new StringReader(selector)).findEntities(source).size();
                } catch (CommandSyntaxException e) {
                    throw new AssertionError(e);
                }
            });
            check(selected == 1, where + ": " + selector + " selected " + selected + " armor stands across the seam");
        }
        double standAlong = s * (clientAlong(context, seam, "armor_stand") - lap), arrowAlong = s * (clientAlong(context, seam, "arrow") - lap);
        check(standAlong > half && standAlong < half + 4, where + ": the client sees the armor stand at " + s * standAlong);
        check(arrowAlong > half + 2 && arrowAlong < half + 32, where + ": the client sees the arrow at " + s * arrowAlong);
        server.runCommand(place.clear(seam, y));

        // Walk over the seam for real: the server folds the player, the client keeps going.
        context.getInput().holdKeyFor(options -> options.keyUp, 60);
        context.waitTicks(10);
        double serverAlong = server.computeOnServer(sv -> seam.along(sv.getPlayerList().getPlayers().getFirst()));
        double clientAlong = s * (context.computeOnClient(mc -> seam.along(mc.player)) - lap);
        check(Math.abs(serverAlong + s * half) < 32 && serverAlong >= -half && serverAlong < half,
                where + ": the server did not fold the player: " + serverAlong);
        check(clientAlong > half && clientAlong < half + 32, where + ": the client frame jumped at the seam: " + s * clientAlong);
        context.takeScreenshot("orbis-crossed-" + tag);

        // A husk three blocks before the seam notices a villager three blocks past it, walks over and hits it: the
        // entity search, the distance and the path all take the short way across.
        server.runCommand("difficulty normal");
        server.runCommand(place.in("tp @a " + seam.at(s * (half - 10), y, 0.5) + " " + seam.facing(10)));
        server.runCommand(place.in("summon minecraft:villager " + seam.at(s * (half + 3), y, 0.5) + " {NoAI:1b,PersistenceRequired:1b}"));
        server.runCommand(place.in("summon minecraft:husk " + seam.at(s * (half - 3), y, 0.5) + " {PersistenceRequired:1b}"));
        boolean targeted = false, hunted = false;
        for (int tick = 0; tick < 400 && !hunted; tick += 10) {
            context.waitTicks(10);
            boolean[] state = server.computeOnServer(sv -> {
                Mob husk = null;
                LivingEntity villager = null;
                for (Entity e : place.level(sv).getEntities(EntityTypeTest.forClass(LivingEntity.class), e -> true)) {
                    if (typeName(e).equals("husk")) husk = (Mob) e;
                    if (typeName(e).equals("villager")) villager = (LivingEntity) e;
                }
                if (husk == null || villager == null) return new boolean[] {false, false};
                return new boolean[] {husk.getTarget() == villager, villager.getHealth() < villager.getMaxHealth()};
            });
            targeted |= state[0];
            hunted = state[1];
        }
        check(targeted, where + ": the husk never targeted the villager across the seam");
        check(hunted, where + ": the husk never reached the villager across the seam");
        server.runCommand(place.clear(seam, y));

        server.runCommand("difficulty normal");
        server.runCommand(place.in("tp @a " + seam.at(s * (half - 1.5), y, 0.5) + " " + seam.facing(20)));
        server.runCommand(place.in("summon minecraft:zombie " + seam.at(s * (half + 1), y, 0.5) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}"));
        server.runCommand(place.in("item replace entity @e[type=minecraft:zombie] armor.head with minecraft:leather_helmet"));
        context.waitTicks(10);
        world.getConnection().waitForChunksRender();
        String aimed = context.computeOnClient(mc -> mc.crosshairPickEntity == null ? "nothing" : typeName(mc.crosshairPickEntity));
        check(aimed.equals("zombie"), where + ": the crosshair is on " + aimed + ", not the zombie past the seam");
        // The client attacks what its crosshair is on, as a click would; the server must accept the reach.
        context.runOnClient(mc -> mc.gameMode.attack(mc.player, mc.crosshairPickEntity));
        context.waitTicks(5);
        context.takeScreenshot("orbis-hit-" + tag);
        String hitBy = server.computeOnServer(sv -> {
            for (Entity e : place.level(sv).getEntities(EntityTypeTest.forClass(Mob.class), e -> typeName(e).equals("zombie"))) {
                DamageSource source = ((Mob) e).getLastDamageSource();
                return source == null ? "nothing" : source.getMsgId() + (source.getEntity() == null ? "" : " by " + typeName(source.getEntity()));
            }
            return "no zombie";
        });
        check(hitBy.equals("player by player"), where + ": the player's hit on the zombie past the seam did not count: hurt by " + hitBy);
        server.runCommand(place.in("kill @e[type=minecraft:zombie]"));

        // A zombie with its AI walks up across the seam and hits; the blow pushes the player back, away from it.
        server.runCommand("gamemode survival @a");
        server.runCommand(place.in("summon minecraft:zombie " + seam.at(s * (half + 3), y, 0.5) + " {PersistenceRequired:1b,Silent:1b}"));
        double start = context.computeOnClient(mc -> seam.along(mc.player));
        double back = 0;
        boolean hurt = false;
        for (int tick = 0; tick < 200 && !hurt; tick += 2) {
            context.waitTicks(2);
            hurt = context.computeOnClient(mc -> mc.player.getHealth() < mc.player.getMaxHealth());
        }
        check(hurt, where + ": the zombie never hit the player across the seam");
        for (int tick = 0; tick < 8; tick++) {
            context.waitTick();
            back = Math.max(back, -s * (context.computeOnClient(mc -> seam.along(mc.player)) - start));
        }
        check(back > 0.2, where + ": the zombie's blow did not push the player away from it: moved back " + back);
        server.runCommand(place.in("kill @e[type=minecraft:zombie]"));
        heal(server);

        // An arrow flying straight back over the seam hits the player before it.
        server.runCommand(place.in("tp @a " + seam.at(s * (half - 4), y, 0.5) + " " + seam.facing(0)));
        context.waitTicks(5);
        server.runCommand(place.in("summon minecraft:arrow " + seam.at(s * (half + 4) + 0.5, y + 1.2, 0.5)
                + " {NoGravity:1b,Motion:" + new Seam(seam.name(), seam.alongX(), -s).motion(1.5) + "}"));
        StringBuilder flight = new StringBuilder();
        boolean struck = false;
        for (int tick = 0; tick < 20 && !struck; tick++) {
            context.waitTick();
            String state = server.computeOnServer(sv -> {
                ServerPlayer player = sv.getPlayerList().getPlayers().getFirst();
                DamageSource source = player.getLastDamageSource();
                return (source != null && source.is(DamageTypes.ARROW)) + mobs(place.level(sv), "arrow");
            });
            struck = state.startsWith("true");
            flight.append(" |").append(state);
        }
        check(struck, where + ": an arrow flying over the seam never hit the player:" + flight);
        server.runCommand(place.in("kill @e[type=minecraft:arrow]"));
        heal(server);
        context.waitTicks(20);

        // A skeleton past the seam shoots the player before it; on hard it aims well enough to hit within a few shots.
        server.runCommand("difficulty hard");
        server.runCommand(place.in("summon minecraft:skeleton " + seam.at(s * (half + 8), y, 0.5) + " {PersistenceRequired:1b,Silent:1b}"));
        server.runCommand(place.in("item replace entity @e[type=minecraft:skeleton] weapon.mainhand with minecraft:bow"));
        server.runCommand(place.in("item replace entity @e[type=minecraft:skeleton] armor.head with minecraft:leather_helmet"));
        boolean shot = false;
        String seen = "";
        for (int tick = 0; tick < 300 && !shot; tick += 5) {
            context.waitTicks(5);
            String state = server.computeOnServer(sv -> {
                ServerPlayer player = sv.getPlayerList().getPlayers().getFirst();
                DamageSource source = player.getLastDamageSource();
                return (source != null && source.is(DamageTypes.ARROW)) + " " + (source == null ? "-" : source.getMsgId())
                        + " hp=" + player.getHealth() + " at " + player.position() + mobs(place.level(sv), "skeleton") + mobs(place.level(sv), "arrow");
            });
            shot = state.startsWith("true");
            if (!state.equals(seen)) seen = seen + " | " + tick + ": " + state;
        }
        context.takeScreenshot("orbis-skeleton-" + tag);
        check(shot, where + ": the skeleton's arrows never hit the player across the seam:" + seen);
        server.runCommand(place.in("kill @e[type=minecraft:skeleton]"));
        server.runCommand(place.in("kill @e[type=minecraft:arrow]"));
        heal(server);
        server.runCommand("gamemode creative @a");
    }

    /**
     * {@code /locate} searches and measures the short way round: from just before the seam and just past it, twenty
     * blocks apart, the nearest village is the same distance away give or take those twenty blocks.
     */
    private static void checkLocateAcrossSeam(TestServerContext server, Place place, String structure) {
        int half = place.half();
        for (boolean alongX : new boolean[] {true, false}) {
            int[] distances = server.computeOnServer(s -> {
                int[] found = new int[2];
                int[] spots = {half - 10, -half + 10};
                for (int i = 0; i < 2; i++) {
                    Vec3 at = alongX ? new Vec3(spots[i], 80, 0) : new Vec3(0, 80, spots[i]);
                    var source = s.createCommandSourceStack().withSuppressedOutput().withLevel(place.level(s)).withPosition(at);
                    try {
                        found[i] = s.getCommands().getDispatcher().execute("locate structure " + structure, source);
                    } catch (CommandSyntaxException e) {
                        throw new AssertionError("No " + structure + " found from " + at + " in the " + place.name(), e);
                    }
                }
                return found;
            });
            check(Math.abs(distances[0] - distances[1]) <= 30 && distances[0] < half,
                    "Nearest " + structure + " in the " + place.name() + " from either side of the " + (alongX ? "east/west" : "north/south") + " seam: "
                            + distances[0] + " and " + distances[1] + " blocks");
        }
    }

    private static String mobs(ServerLevel level, String type) {
        StringBuilder out = new StringBuilder();
        for (Entity e : level.getAllEntities()) {
            if (!typeName(e).equals(type)) continue;
            out.append(' ').append(type).append('@').append(Math.round(e.getX() * 10) / 10.0).append(',').append(Math.round(e.getY() * 10) / 10.0);
            if (e instanceof Mob mob) out.append(" t=").append(mob.getTarget() == null ? "-" : typeName(mob.getTarget()))
                    .append(" hand=").append(mob.getMainHandItem().getItem()).append(" hp=").append(mob.getHealth());
        }
        return out.toString();
    }

    private static void heal(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
            player.setHealth(player.getMaxHealth());
        });
    }

    /** Where the client sees the first entity of a type, or NaN. */
    private static double clientEntityX(ClientGameTestContext context, String type) {
        return context.computeOnClient(mc -> {
            for (Entity entity : mc.level.entitiesForRendering()) if (typeName(entity).equals(type)) return entity.getX();
            return Double.NaN;
        });
    }

    /** Where along the seam's axis the client sees the first entity of a type, or NaN. */
    private static double clientAlong(ClientGameTestContext context, Seam seam, String type) {
        return context.computeOnClient(mc -> {
            for (Entity entity : mc.level.entitiesForRendering()) if (typeName(entity).equals(type)) return seam.along(entity);
            return Double.NaN;
        });
    }

    private static String typeName(Entity entity) { return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(); }

    private static boolean isProbe(Entity entity) { return typeName(entity).equals("armor_stand") || typeName(entity).equals("arrow"); }

    /** Every chunk exists once, at its folded position; a holder anywhere else would never be unloaded. */
    private static void checkHoldersFolded(TestServerContext server) {
        String stray = server.computeOnServer(s -> {
            for (ServerLevel level : s.getAllLevels()) {
                var torus = Tori.of(level);
                if (torus == null) continue;
                var chunks = ((ChunkMapAccessor) level.getChunkSource().chunkMap).orbis$updatingChunks();
                for (long key : chunks.keySet()) {
                    int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
                    if (!torus.containsChunk(x) || !torus.containsChunk(z)) return level.dimension() + " [" + x + ", " + z + "]";
                }
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

    /** The corridor's floor height: the surface in the Overworld, a fixed level inside the Nether's rock. */
    private static int floor(ClientGameTestContext context, TestSingleplayerContext world, Place place, Seam seam) {
        int a = seam.sign() * (place.half() - 6);
        if (place.key() == Level.OVERWORLD) return seam.alongX() ? surface(context, world, a, 0) : surface(context, world, 0, a);
        world.getServer().runCommand(place.in("tp @a " + seam.at(a, 70, 0.5)));
        waitLoaded(context, world, place.key(), seam.pos(a, 0, 0));
        return 70;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** The client may have nothing to render yet right after a teleport; wait until the server has the column. */
    private static void waitLoaded(ClientGameTestContext context, TestSingleplayerContext world, int x, int z) {
        waitLoaded(context, world, Level.OVERWORLD, new BlockPos(x, 0, z));
    }

    private static void waitLoaded(ClientGameTestContext context, TestSingleplayerContext world, ResourceKey<Level> key, BlockPos pos) {
        for (int tick = 0; !world.getServer().computeOnServer(s -> s.getLevel(key).isLoaded(pos)); tick++) {
            check(tick < 600, "Server never loaded the column at " + pos.toShortString() + " in " + key);
            context.waitTick();
        }
    }
}
