package dev.orbis.gametest;

import static dev.orbis.gametest.SeamWorld.*;

import dev.orbis.engine.Tori;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How the world behaves across the seam, at every seam and both corners of the Overworld and the Nether: a long
 * chase, a villager taking a bed and a workstation past the seam, an explosion on it, a redstone line over it,
 * cloning and spreading players over it, and an ender pearl thrown over it.
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
        server.runCommand(place.in("summon minecraft:villager " + seam.at(half, reach + 0.5, y, mid) + " {NoAI:1b,PersistenceRequired:1b}"));
        server.runCommand(place.in("summon minecraft:husk " + seam.at(half, 0.5 - reach, y, mid) + " {PersistenceRequired:1b}"));
        boolean hunted = false;
        double closest = Double.MAX_VALUE;
        for (int tick = 0; tick < 900 && !hunted; tick += 10) {
            context.waitTicks(10);
            double[] state = server.computeOnServer(sv -> {
                Mob husk = null;
                LivingEntity villager = null;
                for (Entity e : place.level(sv).getEntities(EntityTypeTest.forClass(LivingEntity.class), e -> true)) {
                    if (typeName(e).equals("husk")) husk = (Mob) e;
                    if (typeName(e).equals("villager")) villager = (LivingEntity) e;
                }
                if (husk == null || villager == null) return new double[] {Double.MAX_VALUE, 0};
                var torus = Tori.of(place.level(sv));
                double gap = Math.hypot(torus.delta(husk.getX(), villager.getX()), torus.delta(husk.getZ(), villager.getZ()));
                return new double[] {gap, villager.getHealth() < villager.getMaxHealth() ? 1 : 0};
            });
            closest = Math.min(closest, state[0]);
            hunted = state[1] > 0;
        }
        context.takeScreenshot("stage3-chase-" + tag);
        check(hunted, where + ": the husk never reached the villager thirty blocks away over the seam; it came within " + closest);
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
}
