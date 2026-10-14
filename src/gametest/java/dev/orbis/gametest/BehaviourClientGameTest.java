package dev.orbis.gametest;

import static dev.orbis.gametest.SeamWorld.*;

import dev.orbis.engine.Tori;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How the world behaves across the seam, at every seam and both corners of the Overworld and the Nether: a long
 * chase, a villager taking a bed and a workstation past the seam, an explosion on it, a redstone line over it,
 * cloning and spreading players over it, an ender pearl and a loyal trident thrown over it, despawning, spawners and
 * natural spawning past it, a horse ridden and a creeper stalking over it, and a raid on a village across it.
 */
public final class BehaviourClientGameTest implements FabricClientGameTest {
    /** The corridor runs this far before and past the seam. */
    private static final int BEFORE = 24, PAST = 24;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = createTiny(context)) {
            var server = world.getServer();
            server.runCommand("gamerule spawn_mobs false");
            for (Place place : new Place[] {Place.OVERWORLD, Place.NETHER}) {
                for (Seam seam : Seam.SIDES) checkSeam(context, world, server, place, seam);
                for (Seam seam : Seam.CORNERS) checkSeam(context, world, server, place, seam);
            }
        }
    }

    private static void checkSeam(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server,
                                  Place place, Seam seam) {
        int half = place.half();
        String where = place.name() + " " + seam.name(), tag = place.name() + "-" + seam.name();
        int y = floor(context, world, place, seam);
        for (int k : new int[] {-BEFORE, PAST}) {
            for (int b : new int[] {-3, 3}) waitLoaded(context, world, place.key(), seam.block(half, k, 0, b));
        }
        // Entities summoned into a chunk whose entities have not loaded yet stay hidden; wait for both sides.
        for (int k : new int[] {-BEFORE, 0, PAST}) {
            BlockPos at = seam.block(half, k, y, 0);
            for (int tick = 0; !server.computeOnServer(sv -> place.level(sv).areEntitiesLoaded(ChunkPos.pack(at))); tick++) {
                check(tick < 600, where + ": the entities at " + at.toShortString() + " never loaded");
                context.waitTick();
            }
        }
        corridor(server, place, seam, y);
        String built = server.computeOnServer(sv -> {
            var level = place.level(sv);
            for (int k = 1 - BEFORE; k < PAST; k++) {
                if (!level.getBlockState(seam.block(half, k, y - 1, 0)).is(Blocks.STONE)) return "no floor at " + k;
                if (!level.getBlockState(seam.block(half, k, y + 1, 0)).isAir()) return "blocked at " + k;
            }
            return null;
        });
        check(built == null, where + ": corridor not built: " + built);

        checkExplosion(context, server, place, seam, y, where, tag);
        corridor(server, place, seam, y);
        checkLongChase(context, server, place, seam, y, where, tag);
        checkVillagerPoi(context, server, place, seam, y, where, tag);
        checkRedstone(context, server, place, seam, y, where, tag);
        checkCommands(context, server, place, seam, y, where);
        checkEnderPearl(context, server, place, seam, y, where, tag);
        checkDespawnAndSpawner(context, server, place, seam, y, where, tag);
        checkTrident(context, server, place, seam, y, where, tag);
        checkHorse(context, server, place, seam, y, where, tag);
        checkCreeper(context, server, place, seam, y, where, tag);
        if (place == Place.OVERWORLD) checkRaid(context, server, place, seam, y, where, tag);
        checkNaturalSpawning(context, server, place, seam, y, where, tag);
        server.runCommand(place.clear(seam, y));
    }

    /** A glass tube over a stone floor, open from {@link #BEFORE} blocks before the seam to {@link #PAST} past it. */
    private static void corridor(TestServerContext server, Place place, Seam seam, int y) {
        int half = place.half();
        server.runCommand(place.clear(seam, y));
        server.runCommand(place.in("fill " + seam.box(half, -BEFORE, PAST, y - 1, y + 4, -3, 3) + " minecraft:glass"));
        server.runCommand(place.in("fill " + seam.box(half, -BEFORE, PAST, y - 1, y - 1, -2, 2) + " minecraft:stone"));
        // Closed at both ends, so nothing walks off to the terrain or a village nearby.
        server.runCommand(place.in("fill " + seam.box(half, 1 - BEFORE, PAST - 1, y, y + 3, -2, 2) + " minecraft:air"));
        server.runCommand(place.in("fill " + seam.box(half, 1 - BEFORE, PAST - 1, y + 3, y + 3, -2, 2) + " minecraft:glowstone"));
    }

    /** A husk fifteen blocks before the seam hunts a villager fifteen past it, within its follow range of 35, the whole way over. */
    private static void checkLongChase(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                       String where, String tag) {
        int half = place.half();
        double mid = seam.mid();
        server.runCommand("difficulty normal");
        server.runCommand(place.in("tp @a " + seam.at(half, -22.5, y, mid) + " " + seam.facing(10)));
        // Through a corner both axes count, so the spots move in to keep thirty blocks between them.
        double reach = seam.corner() ? 10.5 : 15;
        server.runCommand(place.in("summon minecraft:villager " + seam.at(half, reach + 0.5, y, mid) + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"orbis\"]}"));
        server.runCommand(place.in("summon minecraft:husk " + seam.at(half, 0.5 - reach, y, mid) + " {PersistenceRequired:1b,Tags:[\"orbis\"]}"));
        boolean hunted = false;
        double closest = Double.MAX_VALUE;
        for (int tick = 0; tick < 900 && !hunted; tick += 10) {
            context.waitTicks(10);
            double[] state = server.computeOnServer(sv -> {
                Mob husk = null;
                LivingEntity villager = null;
                // Only the test's own pair: a village nearby has villagers of its own.
                for (Entity e : place.level(sv).getEntities(EntityTypeTest.forClass(LivingEntity.class), e -> e.entityTags().contains("orbis"))) {
                    if (typeName(e).equals("husk")) husk = (Mob) e;
                    if (typeName(e).equals("villager")) villager = (LivingEntity) e;
                }
                if (husk == null || villager == null) return new double[] {Double.MAX_VALUE, 0, villager == null ? 1 : 0};
                var torus = Tori.of(place.level(sv));
                double gap = Math.hypot(torus.delta(husk.getX(), villager.getX()), torus.delta(husk.getZ(), villager.getZ()));
                return new double[] {gap, villager.getHealth() < villager.getMaxHealth() ? 1 : 0};
            });
            // Killed between two looks counts too, once the villager was seen alive.
            hunted = state[1] > 0 || state.length > 2 && state[2] > 0 && closest < Double.MAX_VALUE;
            closest = Math.min(closest, state[0]);
        }
        context.takeScreenshot("stage3-chase-" + tag);
        String husk = server.computeOnServer(sv -> {
            for (Mob mob : place.level(sv).getEntities(EntityTypeTest.forClass(Mob.class), e -> typeName(e).equals("husk"))) {
                var path = mob.getNavigation().getPath();
                return "husk at " + mob.position() + ", target " + (mob.getTarget() == null ? "-" : typeName(mob.getTarget()) + " at " + mob.getTarget().position())
                        + ", path " + (path == null ? "-" : "to " + path.getTarget().toShortString() + " reach " + path.canReach());
            }
            return "no husk";
        });
        check(hunted, where + ": the husk never reached the villager thirty blocks away over the seam; it came within " + closest + "; " + husk);
        server.runCommand(place.clear(seam, y));
    }

    /**
     * A villager before the seam claims a bed and a composter past it: the search for points of interest, the
     * claim and the walk there all go over the seam, and it becomes a farmer.
     */
    private static void checkVillagerPoi(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                         String where, String tag) {
        int half = place.half();
        BlockPos foot = seam.block(half, 6, y, 1), head = foot.south(), composter = seam.block(half, 4, y, -1);
        server.runCommand(place.in("setblock " + Seam.text(foot) + " minecraft:red_bed[facing=south,part=foot]"));
        server.runCommand(place.in("setblock " + Seam.text(head) + " minecraft:red_bed[facing=south,part=head]"));
        server.runCommand(place.in("setblock " + Seam.text(composter) + " minecraft:composter"));
        server.runCommand(place.in("tp @a " + seam.at(half, -10.5, y, seam.mid()) + " " + seam.facing(20)));
        server.runCommand(place.in("summon minecraft:villager " + seam.at(half, -4.5, y, seam.mid()) + " {PersistenceRequired:1b,Tags:[\"orbis\"]}"));
        BlockPos homeWanted = server.computeOnServer(sv -> folded(Tori.of(place.level(sv)), head));
        BlockPos jobWanted = server.computeOnServer(sv -> folded(Tori.of(place.level(sv)), composter));
        String state = "no villager";
        boolean done = false;
        for (int tick = 0; tick < 2400 && !done; tick += 20) {
            context.waitTicks(20);
            String[] now = server.computeOnServer(sv -> {
                ServerLevel level = place.level(sv);
                // Only the test's own villager: a village may stand nearby.
                for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), e -> e.entityTags().contains("orbis"))) {
                    String home = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).map(Seam::text).orElse("-");
                    String job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).map(Seam::text).orElse("-");
                    boolean farmer = villager.getVillagerData().profession().is(VillagerProfession.FARMER);
                    boolean ok = home.equals(Seam.text(homeWanted)) && job.equals(Seam.text(jobWanted)) && farmer;
                    return new String[] {"home " + home + ", job " + job + ", farmer " + farmer + " at " + villager.blockPosition().toShortString(), ok ? "y" : ""};
                }
                return new String[] {"no villager", ""};
            });
            state = now[0];
            done = !now[1].isEmpty();
        }
        context.takeScreenshot("stage3-villager-" + tag);
        String seen = server.computeOnServer(sv -> {
            var pois = place.level(sv).getPoiManager();
            BlockPos from = seam.block(half, -5, y, 0);
            return pois.findAllClosestFirstWithType(type -> true, pos -> true, from, 48, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                    .limit(6).map(pair -> pair.getFirst().getRegisteredName() + "@" + pair.getSecond().toShortString()).toList().toString();
        });
        check(done, where + ": the villager did not take the bed " + homeWanted.toShortString() + " and composter "
                + jobWanted.toShortString() + " past the seam: " + state + "; points near its start: " + seen);
        // The claimed points make a village, and it reaches over the seam: the ground before it belongs to it too.
        boolean village = server.computeOnServer(sv -> place.level(sv).isVillage(seam.block(half, -12, y, 0)));
        check(village, where + ": the village past the seam does not reach the ground before it");
        server.runCommand(place.clear(seam, y));
        server.runCommand(place.in("fill " + seam.box(half, 1 - BEFORE, PAST - 1, y, y + 2, -2, 2) + " minecraft:air"));
    }

    private static BlockPos folded(dev.orbis.engine.Torus torus, BlockPos pos) {
        return new BlockPos(torus.fold(pos.getX()), pos.getY(), torus.fold(pos.getZ()));
    }

    /** A redstone line laid over the seam carries power from a block before it to a lamp past it, and off again. */
    private static void checkRedstone(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                      String where, String tag) {
        int half = place.half();
        int b = seam.corner() ? 0 : -2, reach = seam.corner() ? 2 : 5;
        BlockPos source = seam.block(half, -reach, y, b), lamp = seam.block(half, reach - 1, y, b);
        if (seam.corner()) {
            // The diagonal is not a line of touching blocks, so the wire turns a corner: along x over the one seam,
            // then along z over the other.
            server.runCommand(place.in("fill " + Seam.text(source.offset(seam.sx(), 0, 0)) + " " + Seam.text(new BlockPos(lamp.getX(), y, source.getZ())) + " minecraft:redstone_wire"));
            server.runCommand(place.in("fill " + Seam.text(new BlockPos(lamp.getX(), y, source.getZ())) + " " + Seam.text(lamp.offset(0, 0, -seam.sz())) + " minecraft:redstone_wire"));
        } else {
            server.runCommand(place.in("fill " + seam.box(half, 1 - reach, reach - 2, y, y, b, b) + " minecraft:redstone_wire"));
        }
        server.runCommand(place.in("setblock " + Seam.text(lamp) + " minecraft:redstone_lamp"));
        server.runCommand(place.in("tp @a " + seam.at(half, -8.5, y, seam.mid()) + " " + seam.facing(25)));
        server.runCommand(place.in("setblock " + Seam.text(source) + " minecraft:redstone_block"));
        boolean lit = waitLamp(context, server, place, lamp, true);
        check(lit, where + ": the redstone line did not light the lamp past the seam");
        context.waitTicks(10);
        int[] lap = lap(context, place, seam, -8.5);
        String seen = context.computeOnClient(mc -> {
            var state = mc.level.getBlockState(lamp.offset(lap[0], 0, lap[1]));
            return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT) ? null : state.toString();
        });
        String stale = context.computeOnClient(mc -> {
            for (int k = 1 - BEFORE; k < PAST; k++) {
                for (int side = -2; side <= 2; side++) {
                    BlockPos at = seam.block(half, k, y, side).offset(lap[0], 0, lap[1]);
                    if (mc.level.getBlockState(at).is(net.minecraft.tags.BlockTags.BEDS)) return "a bed at " + k + "," + side;
                }
            }
            return null;
        });
        context.takeScreenshot("stage3-redstone-" + tag);
        check(seen == null, where + ": the client sees " + seen + " where the lit lamp past the seam is");
        check(stale == null, where + ": the client still shows " + stale + " that the server removed");
        server.runCommand(place.in("setblock " + Seam.text(source) + " minecraft:air"));
        boolean off = waitLamp(context, server, place, lamp, false);
        check(off, where + ": the lamp past the seam stayed lit after the power was removed");
        server.runCommand(place.in("fill " + seam.box(half, 1 - BEFORE, PAST - 1, y, y + 2, -2, 2) + " minecraft:air"));
    }

    /** The client's frame after a teleport to {@code k} blocks past the seam: whole widths off the command's spot. */
    private static int[] lap(ClientGameTestContext context, Place place, Seam seam, double k) {
        Vec3 at = context.computeOnClient(mc -> mc.player.position());
        int half = place.half();
        return new int[] {(int) Math.rint(at.x - seam.x(half, k, seam.mid())), (int) Math.rint(at.z - seam.z(half, k, seam.mid()))};
    }

    private static boolean waitLamp(ClientGameTestContext context, TestServerContext server, Place place, BlockPos lamp, boolean lit) {
        for (int tick = 0; tick < 40; tick += 2) {
            context.waitTicks(2);
            boolean now = server.computeOnServer(sv -> {
                var state = place.level(sv).getBlockState(lamp);
                return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
            });
            if (now == lit) return true;
        }
        return false;
    }

    /**
     * TNT going off in the last block before the seam blows the floor away evenly on both sides of it, and hurts and throws back a zombie
     * past it, away from the blast. The zombie cannot walk, so only the blast moves it, and resists enough to live.
     */
    private static void checkExplosion(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                       String where, String tag) {
        int half = place.half();
        double mid = seam.mid();
        server.runCommand(place.in("tp @a " + seam.at(half, -14.5, y, mid) + " " + seam.facing(20)));
        server.runCommand(place.in("summon minecraft:zombie " + seam.at(half, 2.5, y, mid)
                + " {PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:movement_speed\",base:0.0d}],"
                + "active_effects:[{id:\"minecraft:resistance\",amplifier:2,duration:400}]}"));
        context.waitTicks(2);
        Vec3 before = zombie(server, place);
        check(before != null, where + ": the zombie summoned past the seam is missing: " + mobsNear(server, place, seam, y));
        server.runCommand(place.in("summon minecraft:tnt " + seam.at(half, -0.5, y, mid) + " {fuse:20}"));
        double pushed = -1;
        float health = 20;
        for (int tick = 0; tick < 40; tick += 2) {
            context.waitTicks(2);
            Vec3 now = zombie(server, place);
            if (now != null && before != null) {
                var torus = server.computeOnServer(sv -> Tori.of(place.level(sv)));
                pushed = Math.max(pushed, seam.along(torus.delta(before.x, now.x), torus.delta(before.z, now.z)));
            }
            health = server.computeOnServer(sv -> {
                for (Mob mob : place.level(sv).getEntities(EntityTypeTest.forClass(Mob.class), e -> typeName(e).equals("zombie"))) return mob.getHealth();
                return 0f;
            });
        }
        context.takeScreenshot("stage3-explosion-" + tag);
        String holes = server.computeOnServer(sv -> {
            var level = place.level(sv);
            StringBuilder intact = new StringBuilder();
            boolean whole = false;
            for (int k : new int[] {-2, -1, 0}) whole |= !level.getBlockState(seam.block(half, k, y - 1, 0)).isAir();
            for (int k = -5; k <= 4; k++) intact.append(' ').append(k).append(level.getBlockState(seam.block(half, k, y - 1, 0)).isAir() ? ":hole" : ":floor");
            return whole ? "floor along the corridor:" + intact : null;
        });
        check(holes == null, where + ": the explosion on the seam did not reach both sides: " + holes);
        check(health < 20, where + ": the explosion did not hurt the zombie past the seam");
        check(pushed > 0.3, where + ": the explosion did not push the zombie away over the seam: moved " + pushed);
        server.runCommand(place.clear(seam, y));
    }

    private static String mobsNear(TestServerContext server, Place place, Seam seam, int y) {
        return server.computeOnServer(sv -> {
            StringBuilder out = new StringBuilder();
            for (Entity e : place.level(sv).getAllEntities()) {
                if (e instanceof LivingEntity) out.append(' ').append(typeName(e)).append('@').append(e.blockPosition().toShortString())
                        .append(e.isRemoved() ? " removed " + e.getRemovalReason() : "");
            }
            return out.toString();
        });
    }

    private static @Nullable Vec3 zombie(TestServerContext server, Place place) {
        return server.computeOnServer(sv -> {
            for (Mob mob : place.level(sv).getEntities(EntityTypeTest.forClass(Mob.class), e -> typeName(e).equals("zombie"))) return mob.position();
            return null;
        });
    }

    /** {@code /clone} copies blocks from both sides of the seam, and {@code /spreadplayers} on it lands in the world. */
    private static void checkCommands(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                      String where) {
        int half = place.half();
        server.runCommand(place.in("tp @a " + seam.at(half, -12.5, y, seam.mid()) + " " + seam.facing(20)));
        server.runCommand(place.in("setblock " + Seam.text(seam.block(half, -1, y, 0)) + " minecraft:diamond_block"));
        server.runCommand(place.in("setblock " + Seam.text(seam.block(half, 0, y, 0)) + " minecraft:emerald_block"));
        BlockPos from = seam.block(half, -1, y, 0), to = seam.block(half, 0, y, 0);
        BlockPos low = new BlockPos(Math.min(from.getX(), to.getX()), y, Math.min(from.getZ(), to.getZ()));
        BlockPos high = new BlockPos(Math.max(from.getX(), to.getX()), y, Math.max(from.getZ(), to.getZ()));
        BlockPos copy = seam.block(half, -10, y + 1, 0);
        server.runCommand(place.in("clone " + Seam.text(low) + " " + Seam.text(high) + " " + Seam.text(copy)));
        String cloned = server.computeOnServer(sv -> {
            var level = place.level(sv);
            BlockPos d = copy.offset(from.getX() - low.getX(), 0, from.getZ() - low.getZ());
            BlockPos e = copy.offset(to.getX() - low.getX(), 0, to.getZ() - low.getZ());
            boolean ok = level.getBlockState(d).is(Blocks.DIAMOND_BLOCK) && level.getBlockState(e).is(Blocks.EMERALD_BLOCK);
            return ok ? null : level.getBlockState(d) + " / " + level.getBlockState(e);
        });
        check(cloned == null, where + ": /clone over the seam copied " + cloned);
        server.runCommand(place.in("fill " + seam.box(half, 1 - BEFORE, PAST - 1, y, y + 2, -2, 2) + " minecraft:air"));

        server.runCommand(place.in("spreadplayers " + seam.x(half, 0, 0) + " " + seam.z(half, 0, 0) + " 0 2 under " + (y + 2) + " false @a"));
        context.waitTicks(5);
        String spread = server.computeOnServer(sv -> {
            ServerPlayer player = sv.getPlayerList().getPlayers().getFirst();
            var torus = Tori.of(place.level(sv));
            double gap = Math.hypot(torus.delta(seam.x(half, 0, 0), player.getX()), torus.delta(seam.z(half, 0, 0), player.getZ()));
            boolean inside = torus.contains(player.getBlockX()) && torus.contains(player.getBlockZ());
            return gap <= 4 && inside ? null : player.position() + " (" + gap + " blocks off)";
        });
        check(spread == null, where + ": /spreadplayers on the seam put the player at " + spread);
        server.runCommand(place.in("particle minecraft:flame " + seam.at(half, 0, y + 1, seam.mid()) + " 1 1 1 0 20 force"));
    }

    /** An ender pearl thrown over the seam takes the player past it; the server folds them, the client goes on. */
    private static void checkEnderPearl(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                        String where, String tag) {
        int half = place.half();
        server.runCommand(place.in("tp @a " + seam.at(half, -4.5, y, seam.mid()) + " " + seam.facing(0)));
        context.waitTicks(10);
        Vec3 start = context.computeOnClient(mc -> mc.player.position());
        double[] lap = {start.x - seam.x(half, -4.5, seam.mid()), start.z - seam.z(half, -4.5, seam.mid())};
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:ender_pearl");
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        String landed = "not thrown";
        for (int tick = 0; tick < 100; tick += 5) {
            context.waitTicks(5);
            Vec3 at = server.computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().position());
            landed = seam.folded(half, at);
            if (landed == null) break;
        }
        context.waitTicks(10);
        context.takeScreenshot("stage3-pearl-" + tag);
        check(landed == null, where + ": the ender pearl did not take the player over the seam: " + landed);
        String onClient = seam.past(half, context.computeOnClient(mc -> mc.player.position()), lap, 0, 64);
        check(onClient == null, where + ": the client jumped when the pearl landed past the seam: " + onClient);
        server.runCommand("clear @a");
    }

    private static void survival(TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance 600 4 true");
        server.runCommand("clear @a");
    }

    private static void creative(TestServerContext server) {
        server.runCommand("effect clear @a");
        server.runCommand("clear @a");
        server.runCommand("gamemode creative @a");
        server.runOnServer(sv -> {
            ServerPlayer player = sv.getPlayerList().getPlayers().getFirst();
            player.setHealth(player.getMaxHealth());
        });
    }

    private static int count(TestServerContext server, Place place, String type) {
        return server.computeOnServer(sv -> {
            int n = 0;
            for (Entity e : place.level(sv).getAllEntities()) if (typeName(e).equals(type) && e.isAlive()) n++;
            return n;
        });
    }

    /**
     * A zombie past the seam, near the player the short way, does not despawn as if it were a world away; and a
     * spawner past the seam counts the player before it as near and spawns.
     */
    private static void checkDespawnAndSpawner(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                               String where, String tag) {
        int half = place.half();
        server.runCommand("difficulty normal");
        server.runCommand(place.in("tp @a " + seam.at(half, -8.5, y, seam.mid()) + " " + seam.facing(10)));
        server.runCommand(place.in("summon minecraft:zombie " + seam.at(half, 12.5, y, seam.mid()) + " {Silent:1b}"));
        context.waitTicks(40);
        check(count(server, place, "zombie") == 1, where + ": a zombie 21 blocks away past the seam despawned as if it were far");
        server.runCommand(place.clear(seam, y));

        // Through a corner both axes count, so there the player steps up to stay within the spawner's sixteen blocks.
        BlockPos spawner = seam.block(half, 3, y, seam.corner() ? 0 : 2);
        if (seam.corner()) server.runCommand(place.in("tp @a " + seam.at(half, -4.5, y, seam.mid()) + " " + seam.facing(10)));
        server.runCommand(place.in("setblock " + Seam.text(spawner) + " minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\",Silent:1b},"
                + "custom_spawn_rules:{block_light_limit:[0,15],sky_light_limit:[0,15]}},Delay:0s,MinSpawnDelay:20s,MaxSpawnDelay:40s,"
                + "SpawnRange:2s,SpawnCount:2s,RequiredPlayerRange:16s}"));
        int spawned = 0;
        for (int tick = 0; tick < 300 && spawned == 0; tick += 10) {
            context.waitTicks(10);
            spawned = count(server, place, "zombie");
        }
        context.takeScreenshot("stage3-spawner-" + tag);
        check(spawned > 0, where + ": the spawner past the seam did not notice the player before it");
        server.runCommand(place.in("setblock " + Seam.text(spawner) + " minecraft:air"));
        server.runCommand(place.clear(seam, y));
    }

    /** A loyal trident thrown over the seam flies past it and comes back to the player the short way. */
    private static void checkTrident(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                     String where, String tag) {
        int half = place.half();
        survival(server);
        server.runCommand(place.in("tp @a " + seam.at(half, -5.5, y, seam.mid()) + " " + seam.facing(0)));
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:trident[enchantments={\"minecraft:loyalty\":3}]");
        context.waitTicks(5);
        context.getInput().holdKeyFor(options -> options.keyUse, 15);
        boolean crossed = false, back = false;
        for (int tick = 0; tick < 300 && !back; tick += 5) {
            context.waitTicks(5);
            Vec3 at = server.computeOnServer(sv -> {
                for (Entity e : place.level(sv).getAllEntities()) if (typeName(e).equals("trident")) return e.position();
                return null;
            });
            if (at != null && seam.folded(half, at) == null) crossed = true;
            if (crossed && !back && at != null) context.takeScreenshot("stage3-trident-" + tag);
            back = crossed && server.computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().getInventory()
                    .countItem(net.minecraft.world.item.Items.TRIDENT) == 1);
        }
        check(crossed, where + ": the trident never flew over the seam");
        check(back, where + ": the loyal trident did not come back over the seam");
        creative(server);
        server.runCommand(place.clear(seam, y));
    }

    /** A saddled horse ridden over the seam: the server folds horse and rider, the client rides on. */
    private static void checkHorse(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                   String where, String tag) {
        int half = place.half();
        server.runCommand(place.in("tp @a " + seam.at(half, -10.5, y, seam.mid()) + " " + seam.facing(10)));
        context.waitTicks(5);
        int[] lap = lap(context, place, seam, -10.5);
        server.runCommand(place.in("summon minecraft:horse " + seam.at(half, -10.5, y, seam.mid())
                + " {Tags:[\"orbis\"],Tame:1b,Rotation:[" + seam.facing(0).split(" ")[0] + "f,0f],equipment:{saddle:{id:\"minecraft:saddle\",count:1}},"
                + "attributes:[{id:\"minecraft:movement_speed\",base:0.3d}]}"));
        // By tag: a wild horse elsewhere may be nearer to where the command runs.
        server.runCommand(place.in("ride @p mount @e[type=minecraft:horse,tag=orbis,limit=1]"));
        context.waitTicks(5);
        check(server.computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().isPassenger()), where + ": the player is not on the horse");
        context.getInput().holdKeyFor(options -> options.keyUp, 60);
        context.waitTicks(5);
        Vec3[] now = server.computeOnServer(sv -> {
            ServerPlayer player = sv.getPlayerList().getPlayers().getFirst();
            return new Vec3[] {player.position(), player.getVehicle() == null ? null : player.getVehicle().position()};
        });
        check(now[1] != null, where + ": the player fell off the horse at the seam");
        String horse = seam.folded(half, now[1]), rider = seam.folded(half, now[0]);
        check(horse == null && rider == null, where + ": the server did not fold the horse and its rider: " + horse + " / " + rider);
        String onClient = seam.past(half, context.computeOnClient(mc -> mc.player.position()), new double[] {lap[0], lap[1]}, 0, 64);
        context.takeScreenshot("stage3-horse-" + tag);
        check(onClient == null, where + ": the client's ride jumped at the seam: " + onClient);
        server.runCommand("ride @p dismount");
        server.runCommand(place.clear(seam, y));
    }

    /** A creeper past the seam stalks the player before it and blows up next to them. */
    private static void checkCreeper(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                     String where, String tag) {
        int half = place.half();
        survival(server);
        server.runCommand(place.in("tp @a " + seam.at(half, -4.5, y, seam.mid()) + " " + seam.facing(10)));
        server.runCommand(place.in("summon minecraft:creeper " + seam.at(half, 6.5, y, seam.mid()) + " {PersistenceRequired:1b}"));
        boolean blown = false;
        for (int tick = 0; tick < 400 && !blown; tick += 5) {
            context.waitTicks(5);
            blown = count(server, place, "creeper") == 0;
        }
        context.takeScreenshot("stage3-creeper-" + tag);
        check(blown, where + ": the creeper past the seam never came over and blew up");
        creative(server);
        corridor(server, place, seam, y);
    }

    /**
     * A raid on a village whose homes lie on both sides of the seam: it is centred on the seam, a player past it is
     * still in it, and its first wave keeps every raider wherever on either side it stands. Raids only come to the
     * Overworld.
     */
    private static void checkRaid(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                  String where, String tag) {
        int half = place.half();
        BlockPos[] heads = new BlockPos[2];
        int[] ks = {-6, 6};
        for (int i = 0; i < 2; i++) {
            BlockPos foot = seam.block(half, ks[i], y, 1);
            heads[i] = foot.south();
            server.runCommand(place.in("setblock " + Seam.text(foot) + " minecraft:red_bed[facing=south,part=foot]"));
            server.runCommand(place.in("setblock " + Seam.text(heads[i]) + " minecraft:red_bed[facing=south,part=head]"));
        }
        server.runCommand(place.in("tp @a " + seam.at(half, -2.5, y, seam.mid()) + " " + seam.facing(10)));
        server.runCommand("effect give @a minecraft:raid_omen 600 0 true");
        context.waitTicks(5);
        // Both homes taken, as villagers would: together they make one village over the seam.
        String claimed = server.computeOnServer(sv -> {
            ServerLevel level = place.level(sv);
            var torus = Tori.of(level);
            for (BlockPos head : heads) {
                BlockPos at = folded(torus, head);
                if (level.getPoiManager().take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                        (type, pos) -> pos.equals(at), at, 1).isEmpty()) return "no free home at " + at.toShortString();
            }
            return null;
        });
        check(claimed == null, where + ": " + claimed);
        Raid raid = server.computeOnServer(sv -> {
            ServerPlayer player = sv.getPlayerList().getPlayers().getFirst();
            return place.level(sv).getRaids().createOrExtendRaid(player, player.blockPosition());
        });
        check(raid != null, where + ": no raid started");
        BlockPos seamPoint = seam.block(half, 0, y, 0), farSide = seam.block(half, 12, y, 0);
        String placed = server.computeOnServer(sv -> {
            ServerLevel level = place.level(sv);
            var torus = Tori.of(level);
            if (level.getRaidAt(folded(torus, seamPoint)) != raid) return "the seam is not in the raid at " + raid.getCenter().toShortString();
            BlockPos center = raid.getCenter();
            double dx = torus.delta(seamPoint.getX(), center.getX()), dz = torus.delta(seamPoint.getZ(), center.getZ());
            // Homes of a village nearby count too, so the centre lies near the seam, not on it; a world away when wrong.
            if (dx * dx + dz * dz > 48 * 48) return "the raid is centred at " + center.toShortString() + ", far from the seam";
            if (level.getRaidAt(folded(torus, farSide)) != raid) return "12 blocks past the seam is not in the raid at " + center.toShortString();
            return null;
        });
        check(placed == null, where + ": " + placed);
        // Raiders arrive on solid ground near the centre; a deck over the corridor gives them some, ocean or not.
        BlockPos center = server.computeOnServer(sv -> raid.getCenter());
        String deck = (center.getX() - 14) + " " + (y + 5) + " " + (center.getZ() - 14) + " " + (center.getX() + 14) + " " + (y + 5) + " " + (center.getZ() + 14);
        server.runCommand(place.in("fill " + deck + " minecraft:stone"));
        int alive = 0;
        for (int tick = 0; tick < 900 && alive == 0; tick += 10) {
            context.waitTicks(10);
            alive = server.computeOnServer(sv -> raid.isStopped() ? -1 : raid.getTotalRaidersAlive());
            check(alive >= 0, where + ": the raid at " + raid.getCenter().toShortString() + " ended before its first wave, village there "
                    + server.computeOnServer(sv -> place.level(sv).isVillage(raid.getCenter())));
        }
        check(alive > 0, where + ": the raid's first wave never came");
        context.waitTicks(100);
        String kept = server.computeOnServer(sv -> {
            ServerLevel level = place.level(sv);
            if (raid.isStopped()) return "the raid ended during its first wave";
            int raiders = 0;
            for (Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.raid.Raider raider && raider.isAlive()) {
                    raiders++;
                    if (raider.getCurrentRaid() != raid) return typeName(e) + " at " + e.blockPosition().toShortString() + " fell out of the raid";
                }
            }
            return raiders == 0 ? "no raider left" : null;
        });
        context.takeScreenshot("stage3-raid-" + tag);
        check(kept == null, where + ": " + kept);
        server.runOnServer(sv -> raid.stop());
        server.runCommand(place.in("kill @e[type=#minecraft:raiders]"));
        server.runCommand(place.in("fill " + deck + " minecraft:air"));
        for (BlockPos head : heads) {
            server.runCommand(place.in("setblock " + Seam.text(head) + " minecraft:air"));
            server.runCommand(place.in("setblock " + Seam.text(head.north()) + " minecraft:air"));
        }
        creative(server);
        corridor(server, place, seam, y);
    }

    /**
     * Natural spawning in a dark room past the seam while the player waits 26 blocks before it: the room's chunk is
     * one the server spawns in, and the spawn routine, measuring the player the short way, puts monsters into it.
     * The routine is called directly; waiting for the world to pick the room by chance takes too long.
     */
    private static void checkNaturalSpawning(ClientGameTestContext context, TestServerContext server, Place place, Seam seam, int y,
                                             String where, String tag) {
        int half = place.half();
        server.runCommand(place.in("fill " + seam.box(half, 5, PAST, y - 1, y + 4, -3, 3) + " minecraft:stone"));
        server.runCommand(place.in("fill " + seam.box(half, 6, PAST - 1, y, y + 2, -2, 2) + " minecraft:air"));
        server.runCommand(place.in("tp @a " + seam.at(half, -20.5, y, seam.mid()) + " " + seam.facing(10)));
        context.waitTicks(5);
        BlockPos room = seam.block(half, 14, y, 0);
        String spawned = server.computeOnServer(sv -> {
            ServerLevel level = place.level(sv);
            var torus = Tori.of(level);
            BlockPos centre = folded(torus, room);
            var chunks = new java.util.ArrayList<net.minecraft.world.level.chunk.LevelChunk>();
            level.getChunkSource().chunkMap.collectSpawningChunks(chunks);
            if (chunks.stream().noneMatch(chunk -> chunk.getPos().equals(ChunkPos.containing(centre))))
                return "the room's chunk is not among the " + chunks.size() + " chunks the server spawns in";
            for (int attempt = 0; attempt < 50; attempt++) {
                net.minecraft.world.level.NaturalSpawner.spawnCategoryForPosition(net.minecraft.world.entity.MobCategory.MONSTER, level, centre);
                for (Entity e : level.getAllEntities()) {
                    if (!(e instanceof net.minecraft.world.entity.monster.Enemy) || !e.isAlive()) continue;
                    BlockPos at = e.blockPosition();
                    double dx = torus.delta(centre.getX(), at.getX()), dz = torus.delta(centre.getZ(), at.getZ());
                    if (dx * dx + dz * dz < 16 * 16 && at.getY() == y) return null;
                }
            }
            return "no monster spawned in 50 tries";
        });
        // Seen from inside: the room is dark, so a look from its doorway shows what spawned.
        server.runCommand(place.in("setblock " + Seam.text(seam.block(half, 5, y, 0)) + " minecraft:air"));
        server.runCommand(place.in("setblock " + Seam.text(seam.block(half, 5, y + 1, 0)) + " minecraft:air"));
        server.runCommand(place.in("tp @a " + seam.at(half, 2.5, y, seam.mid()) + " " + seam.facing(10)));
        server.runCommand("effect give @a minecraft:night_vision 10 0 true");
        context.waitTicks(10);
        context.takeScreenshot("stage3-spawning-" + tag);
        server.runCommand("effect clear @a");
        check(spawned == null, where + ": natural spawning in the dark room past the seam: " + spawned);
        server.runCommand(place.clear(seam, y));
        corridor(server, place, seam, y);
    }
}
