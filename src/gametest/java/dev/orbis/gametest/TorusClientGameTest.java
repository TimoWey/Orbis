package dev.orbis.gametest;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import static dev.orbis.gametest.SeamWorld.*;

import dev.orbis.atlas.AtlasProbe;
import dev.orbis.engine.Tori;
import dev.orbis.engine.mixin.ChunkMapAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Plays a Tiny world on the new engine: looks over the east seam, checks light and blocks on the far side, walks
 * across it and looks back, visits the corner, then leaves straight after a teleport over the seam. Screenshots
 * land in the run directory's screenshots folder.
 */
public final class TorusClientGameTest implements FabricClientGameTest {
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
        TestWorldSave save;
        try (TestSingleplayerContext world = createTiny(context)) {
            var server = world.getServer();
            save = world.getWorldSave();
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
            for (Seam seam : Seam.SIDES) checkSeam(context, world, server, Place.OVERWORLD, seam);
            for (Seam seam : Seam.CORNERS) checkSeam(context, world, server, Place.OVERWORLD, seam);
            // A far teleport starts the client's frame afresh: it lands exactly where the command said.
            server.runCommand("tp @a 100.25 200 -100.25");
            context.waitTicks(5);
            Vec3 landed = context.computeOnClient(mc -> mc.player.position());
            check(landed.x == 100.25 && landed.z == -100.25, "The client landed at " + landed + " after a far teleport");
            for (Seam seam : Seam.SIDES) checkSeam(context, world, server, Place.NETHER, seam);
            for (Seam seam : Seam.CORNERS) checkSeam(context, world, server, Place.NETHER, seam);
            checkTerrainAcrossSeams(server);
            checkFortressAcrossSeam(context, world, server);
            checkLocateAcrossSeam(server, Place.OVERWORLD, "#minecraft:village");
            checkLocateAcrossSeam(server, Place.NETHER, "minecraft:fortress");

            // The corner, where both axes wrap at once.
            int cornerY = surface(context, world, HALF - 4, HALF - 4);
            server.runCommand("tp @a " + (HALF - 4) + " " + (cornerY + 12) + " " + (HALF - 4) + " -135 40");
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("orbis-corner");

            checkHoldersFolded(server);

            // Marks for after reloading: a block either side of the seam and an armor stand placed past it.
            server.runCommand("tp @a " + (HALF - 40) + " 200 " + (-HALF + 40));
            waitLoaded(context, world, HALF - 1, -HALF + 40);
            waitLoaded(context, world, -HALF, -HALF + 40);
            server.runCommand("setblock " + (HALF - 1) + " 220 " + (-HALF + 40) + " minecraft:diamond_block");
            server.runCommand("setblock " + -HALF + " 220 " + (-HALF + 40) + " minecraft:emerald_block");
            server.runCommand("summon minecraft:armor_stand " + (HALF + 2.5) + " 220 " + (-HALF + 40.5) + " {NoGravity:1b,CustomName:\"kept\"}");

            // Leave right after a long teleport across the seam. A player counted at an unfolded chunk left a
            // holder behind that never unloaded, and saving on exit then waited for it forever.
            server.runCommand("tp @a " + (-HALF + 40) + " 200 " + (HALF - 40));
            context.waitTicks(2);
            checkHoldersFolded(server);

            // The same at the Nether's seam: marks either side of it and an armor stand past it, and the player
            // leaves the world standing just past it, sent there by its unfolded coordinates.
            int nh = HALF / 8;
            BlockPos netherSide = new BlockPos(nh - 1, 100, 40);
            server.runCommand("execute in minecraft:the_nether run tp @a " + (nh - 6) + " 101 40.5");
            waitLoaded(context, world, Level.NETHER, netherSide);
            waitLoaded(context, world, Level.NETHER, new BlockPos(-nh, 100, 40));
            server.runCommand("execute in minecraft:the_nether run fill " + (nh - 8) + " 99 36 " + (nh + 8) + " 99 44 minecraft:netherrack");
            server.runCommand("execute in minecraft:the_nether run fill " + (nh - 8) + " 100 36 " + (nh + 8) + " 104 44 minecraft:air");
            server.runCommand("execute in minecraft:the_nether run setblock " + (nh - 1) + " 103 40 minecraft:gold_block");
            server.runCommand("execute in minecraft:the_nether run setblock " + -nh + " 103 40 minecraft:lapis_block");
            server.runCommand("execute in minecraft:the_nether run summon minecraft:armor_stand " + (nh + 2.5) + " 100 41.5 {NoGravity:1b,CustomName:\"kept\"}");
            server.runCommand("execute in minecraft:the_nether run tp @a " + (nh + 4.5) + " 100 40.5 90 0");
            context.waitTicks(2);
            checkHoldersFolded(server);
        }

        // Back into the saved world: the marks stand where they were on both sides of the seam, the armor stand is
        // there once at its folded place, and the client sees them the short way round.
        try (TestSingleplayerContext world = save.open()) {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            String marks = server.computeOnServer(s -> {
                var level = s.overworld();
                if (!level.getBlockState(new BlockPos(HALF - 1, 220, -HALF + 40)).is(Blocks.DIAMOND_BLOCK)) return "diamond block gone";
                if (!level.getBlockState(new BlockPos(-HALF, 220, -HALF + 40)).is(Blocks.EMERALD_BLOCK)) return "emerald block gone";
                int nh = HALF / 8;
                var nether = s.getLevel(Level.NETHER);
                if (!nether.getBlockState(new BlockPos(nh - 1, 103, 40)).is(Blocks.GOLD_BLOCK)) return "Nether gold block gone";
                if (!nether.getBlockState(new BlockPos(-nh, 103, 40)).is(Blocks.LAPIS_BLOCK)) return "Nether lapis block gone";
                var netherStands = nether.getEntities(EntityTypeTest.forClass(Entity.class), e -> typeName(e).equals("armor_stand"));
                if (netherStands.size() != 1) return netherStands.size() + " armor stands in the Nether";
                if (Math.abs(netherStands.getFirst().getX() - (-nh + 2.5)) > 0.01) return "Nether armor stand at " + netherStands.getFirst().position();
                var player = s.getPlayerList().getPlayers().getFirst();
                if (player.level().dimension() != Level.NETHER) return "player in " + player.level().dimension().identifier();
                if (Math.abs(player.getX() - (-nh + 4.5)) > 1 || Math.abs(player.getZ() - 40.5) > 1) return "player at " + player.position();
                return null;
            });
            check(marks == null, "After reloading: " + marks);
            String netherSeen = context.computeOnClient(mc -> {
                int nh = HALF / 8;
                double px = mc.player.getX();
                var torus = dev.orbis.engine.Torus.centered(2 * nh);
                BlockPos gold = BlockPos.containing(torus.nearest(nh - 1 + 0.5, px), 103, 40);
                BlockPos lapis = BlockPos.containing(torus.nearest(-nh + 0.5, px), 103, 40);
                if (!mc.level.getBlockState(gold).is(Blocks.GOLD_BLOCK)) return "no gold block at " + gold.toShortString();
                if (!mc.level.getBlockState(lapis).is(Blocks.LAPIS_BLOCK)) return "no lapis block at " + lapis.toShortString();
                return null;
            });
            check(netherSeen == null, "After reloading at the Nether seam the client sees " + netherSeen);
            server.runCommand("execute in minecraft:the_nether run tp @a " + (-HALF / 8 + 6.5) + " 100 40.5 90 15");
            context.waitTicks(40);
            context.takeScreenshot("orbis-after-reload-nether-seam");
            checkHoldersFolded(server);
            // Back to the Overworld marks for the rest.
            server.runCommand("execute in minecraft:overworld run tp @a " + (-HALF + 40.5) + " 200 " + (HALF - 39.5));
            for (int tick = 0; !server.computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().level().dimension() == Level.OVERWORLD); tick++) {
                check(tick < 200, "The player did not get back to the Overworld");
                context.waitTick();
            }
            waitLoaded(context, world, HALF - 1, -HALF + 40);
            waitLoaded(context, world, -HALF, -HALF + 40);
            // The player came back from the Nether, so the Overworld's entities load only now.
            long standChunk = net.minecraft.world.level.ChunkPos.pack(new BlockPos(-HALF + 2, 220, -HALF + 40));
            for (int tick = 0; !server.computeOnServer(sv -> sv.overworld().areEntitiesLoaded(standChunk)); tick++) {
                check(tick < 600, "The Overworld's entities at the seam never loaded after reloading");
                context.waitTick();
            }
            String stand = server.computeOnServer(s -> {
                var stands = s.overworld().getEntities(EntityTypeTest.forClass(Entity.class), e -> typeName(e).equals("armor_stand"));
                if (stands.size() != 1) return stands.size() + " armor stands";
                if (Math.abs(stands.getFirst().getX() - (-HALF + 2.5)) > 0.01) return "armor stand at " + stands.getFirst().position();
                return null;
            });
            check(stand == null, "After reloading: " + stand);
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            String seen = context.computeOnClient(mc -> {
                double px = mc.player.getX(), pz = mc.player.getZ();
                var torus = dev.orbis.engine.Torus.centered(2 * HALF);
                BlockPos diamond = BlockPos.containing(torus.nearest(HALF - 1 + 0.5, px), 220, torus.nearest(-HALF + 40 + 0.5, pz));
                BlockPos emerald = BlockPos.containing(torus.nearest(-HALF + 0.5, px), 220, torus.nearest(-HALF + 40 + 0.5, pz));
                if (!mc.level.getBlockState(diamond).is(Blocks.DIAMOND_BLOCK)) return "no diamond block at " + diamond.toShortString();
                if (!mc.level.getBlockState(emerald).is(Blocks.EMERALD_BLOCK)) return "no emerald block at " + emerald.toShortString();
                return null;
            });
            check(seen == null, "After reloading the client sees " + seen);
            server.runCommand("tp @a " + (HALF - 6) + " 222 " + (-HALF + 44) + " -90 30");
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("orbis-after-reload");
            checkHoldersFolded(server);
        }
    }

    /**
     * The Nether is exactly an eighth of the Overworld. A portal just inside one edge is the one found from an exit
     * just inside the opposite edge, five blocks away across the seam rather than the whole width; on both axes and
     * both ways round.
     */
    private static void checkPortalAcrossNetherSeam(TestServerContext server) {
        String problem = server.computeOnServer(s -> {
            var nether = s.getLevel(Level.NETHER);
            var torus = Tori.of(nether);
            if (torus == null) return "Nether is not on the new engine";
            if (torus.widthBlocks() * 8 != Tori.of(s.overworld()).widthBlocks()) return "Nether width " + torus.widthBlocks();
            int low = torus.minBlock() + 2, high = torus.minBlock() + torus.widthBlocks() - 3;
            for (boolean alongX : new boolean[] {true, false}) {
                for (int[] pair : new int[][] {{low, high}, {high - 1, low - 1}}) {
                    BlockPos portal = alongX ? new BlockPos(pair[0], 64, 0) : new BlockPos(0, 64, pair[0]);
                    BlockPos exit = alongX ? new BlockPos(pair[1], 64, 0) : new BlockPos(0, 64, pair[1]);
                    nether.getChunk(portal);
                    nether.getChunk(exit);
                    nether.setBlock(portal, Blocks.NETHER_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    var found = nether.getPortalForcer().findClosestPortalPosition(exit, true, nether.getWorldBorder());
                    nether.setBlock(portal, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    if (found.filter(portal::equals).isEmpty()) return "Found " + found + " instead of " + portal + " from " + exit;
                }
            }
            return null;
        });
        check(problem == null, "Portal across the Nether seam: " + problem);
    }

    /**
     * Everything at one seam of one dimension, in a glass corridor that runs across it: light and blocks past it,
     * entities placed past it and flying over it, selectors, walking over it, the map's frame and coordinates, items
     * thrown over it and picked up across it, a minecart and a boat carrying the player over it, a second player
     * past it, a husk hunting a villager across it, and fighting over it: the player's hit lands, a zombie's blow
     * knocks the player away from it rather than toward it, and arrows and a skeleton's shots fly across and hit.
     */
    private static void checkSeam(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server,
                                  Place place, Seam seam) {
        int half = place.half();
        double mid = seam.mid(), side = mid + 2;
        boolean corner = seam.corner();
        String where = place.name() + " " + seam.name();
        String tag = place.name() + "-" + seam.name();
        int y = floor(context, world, place, seam);
        // Fill refuses a corridor whose far end, past the seam, the server has not loaded yet.
        for (int k : new int[] {-12, 18}) {
            for (int b : new int[] {-3, 3}) waitLoaded(context, world, place.key(), seam.block(half, k, 0, b));
        }
        server.runCommand(place.clear(seam, y));
        server.runCommand(place.in("fill " + seam.box(half, -12, 18, y - 1, y + 4, -3, 3) + " minecraft:glass"));
        server.runCommand(place.in("fill " + seam.box(half, -12, 18, y - 1, y - 1, -2, 2) + " minecraft:stone"));
        server.runCommand(place.in("fill " + seam.box(half, -12, 18, y, y + 3, -2, 2) + " minecraft:air"));
        server.runCommand(place.in("setblock " + Seam.text(seam.block(half, -1, y + 2, 2)) + " minecraft:glowstone"));
        server.runCommand(place.in("setblock " + Seam.text(seam.block(half, 4, y, 2)) + " minecraft:gold_block"));
        seam.mark(server, place, -12, 18, y);
        String built = server.computeOnServer(sv -> {
            var level = place.level(sv);
            for (int k = -12; k <= 18; k++) {
                if (level.getBlockState(seam.block(half, k, y - 1, 0)).isAir()) return "no floor at " + k;
                if (!level.getBlockState(seam.block(half, k, y + 1, 0)).isAir()) return "blocked at " + k;
            }
            return null;
        });
        check(built == null, where + ": corridor not built: " + built);
        server.runCommand(place.in("tp @a " + seam.at(half, -5.5, y, mid) + " " + seam.facing(10)));
        context.waitTicks(40);
        world.getConnection().waitForChunksRender();
        // A short teleport over the seam is a short move for the client too, so its frame may be whole widths off
        // the command's coordinates; everything the client sees below is measured in that frame.
        Vec3 landed = context.computeOnClient(mc -> mc.player.position());
        double[] lap = {landed.x - seam.x(half, -5.5, mid), landed.z - seam.z(half, -5.5, mid)};
        // The player may settle a little after landing, so allow a fraction of a block on top of whole widths.
        for (int axis = 0; axis < 2; axis++) {
            double off = lap[axis], widths = Math.rint(off / (2 * half));
            check(Math.abs(off - widths * 2 * half) < 0.5, where + ": the client landed " + off + " off the teleport");
            lap[axis] = widths * 2 * half;
        }
        int lapX = (int) Math.rint(lap[0]), lapZ = (int) Math.rint(lap[1]);

        // Light from a lamp before the seam shines past it, and the client sees a block placed past it.
        int lightAcross = server.computeOnServer(sv -> place.level(sv).getBrightness(LightLayer.BLOCK, seam.block(half, 1, y + 2, 2)));
        check(lightAcross >= 11, where + ": block light did not cross the seam: " + lightAcross);
        BlockPos gold = seam.block(half, 4, y, 2);
        boolean goldSeen = context.computeOnClient(mc -> mc.level.getBlockState(gold.offset(lapX, 0, lapZ)).is(Blocks.GOLD_BLOCK));
        check(goldSeen, where + ": the client does not see the block placed across the seam");
        context.takeScreenshot("orbis-seam-" + tag);

        // An entity placed past the seam joins at its folded position, one flying over it is folded back, the
        // selectors find them the short way, and the client sees both continuing on its side.
        server.runCommand(place.in("summon minecraft:armor_stand " + seam.at(half, 2.5, y, side) + " {NoGravity:1b}"));
        server.runCommand(place.in("summon minecraft:arrow " + seam.at(half, -2.5, y + 1, mid - 2) + " {NoGravity:1b,Motion:" + seam.motion(1) + "}"));
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
        int boxX = (int) Math.floor(seam.x(half, 2.5, side)) - 4, boxZ = (int) Math.floor(seam.z(half, 2.5, side)) - 4;
        for (String selector : new String[] {
                "@e[type=minecraft:armor_stand,x=" + boxX + ",y=" + (y - 1) + ",z=" + boxZ + ",dx=8,dy=3,dz=8]",
                "@e[type=minecraft:armor_stand,distance=..5]"}) {
            int selected = server.computeOnServer(sv -> {
                Vec3 at = new Vec3(seam.x(half, -0.5, side), y, seam.z(half, -0.5, side));
                var source = sv.createCommandSourceStack().withSuppressedOutput().withLevel(place.level(sv)).withPosition(at);
                try {
                    return EntityArgument.entities().parse(new StringReader(selector)).findEntities(source).size();
                } catch (CommandSyntaxException e) {
                    throw new AssertionError(e);
                }
            });
            check(selected == 1, where + ": " + selector + " selected " + selected + " armor stands across the seam");
        }
        String stand = seam.past(half, clientEntity(context, "armor_stand"), lap, 0, 5);
        check(stand == null, where + ": the client sees the armor stand at " + stand);
        String arrow = seam.past(half, clientEntity(context, "arrow"), lap, 2, 32);
        check(arrow == null, where + ": the client sees the arrow at " + arrow);
        server.runCommand(place.clear(seam, y));

        // Walk over the seam for real: the server folds the player, the client keeps going, and the map shows the
        // world's own frame and coordinates.
        context.getInput().holdKeyFor(options -> options.keyUp, 60);
        context.waitTicks(10);
        String serverSide = seam.folded(half, server.computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().position()));
        check(serverSide == null, where + ": the server did not fold the player: " + serverSide);
        String clientSide = seam.past(half, context.computeOnClient(mc -> mc.player.position()), lap, 0, 32);
        check(clientSide == null, where + ": the client frame jumped at the seam: " + clientSide);
        context.waitTicks(20);
        String frame = context.computeOnClient(mc -> AtlasProbe.frame());
        check(("true " + -half + " " + -half + " " + 2 * half).equals(frame), where + ": the map frame is " + frame);
        long outsideCells = context.computeOnClient(mc -> AtlasProbe.cellsOutside());
        check(outsideCells == 0, where + ": " + outsideCells + " map cells lie outside the torus");
        BlockPos real = server.computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().blockPosition());
        String shown = context.computeOnClient(AtlasProbe::shownCoordinates);
        check((real.getX() + " " + real.getZ()).equals(shown), where + ": the map shows " + shown + " for a player at " + real.toShortString());
        context.takeScreenshot("orbis-crossed-" + tag);

        checkItemsAcrossSeam(context, server, place, seam, y, lap, where);
        checkFluidsAcrossSeam(context, server, place, seam, y, lap, where, tag);
        if (!corner) checkVehiclesAcrossSeam(context, server, place, seam, y, lap, where, tag);
        checkSecondPlayer(context, server, place, seam, y, lap, where);

        // A husk three blocks before the seam notices a villager three blocks past it, walks over and hits it: the
        // entity search, the distance and the path all take the short way across.
        server.runCommand("difficulty normal");
        server.runCommand(place.in("tp @a " + seam.at(half, -9.5, y, mid) + " " + seam.facing(10)));
        server.runCommand(place.in("summon minecraft:villager " + seam.at(half, 3.5, y, mid) + " {NoAI:1b,PersistenceRequired:1b}"));
        server.runCommand(place.in("summon minecraft:husk " + seam.at(half, -2.5, y, mid) + " {PersistenceRequired:1b}"));
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

        // The player's hit on a zombie just past the seam, aimed with the crosshair as a click would be.
        double hitFrom = corner ? -0.5 : -1.5;
        server.runCommand(place.in("tp @a " + seam.at(half, hitFrom, y, mid) + " " + seam.facing(20)));
        server.runCommand(place.in("summon minecraft:zombie " + seam.at(half, 1.5, y, mid) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}"));
        server.runCommand(place.in("item replace entity @e[type=minecraft:zombie] armor.head with minecraft:leather_helmet"));
        context.waitTicks(10);
        world.getConnection().waitForChunksRender();
        String aimed = context.computeOnClient(mc -> mc.crosshairPickEntity == null ? "nothing" : typeName(mc.crosshairPickEntity));
        check(aimed.equals("zombie"), where + ": the crosshair is on " + aimed + ", not the zombie past the seam");
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
        server.runCommand(place.in("kill @e[type=minecraft:experience_orb]"));

        // An experience orb just past the seam flies the short way to the player and is picked up. Aimed the long
        // way, its first move swept a whole world width for collisions and stalled the server for minutes.
        server.runCommand(place.in("summon minecraft:experience_orb " + seam.at(half, 2.5, y + 0.5, mid) + " {Value:1s}"));
        long orbStart = System.nanoTime();
        boolean collected = false;
        for (int tick = 0; tick < 100 && !collected; tick += 5) {
            context.waitTicks(5);
            collected = server.computeOnServer(sv -> place.level(sv).getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.ExperienceOrb.class), e -> true).isEmpty());
        }
        double orbSeconds = (System.nanoTime() - orbStart) / 1e9;
        check(collected, where + ": the experience orb past the seam never reached the player");
        check(orbSeconds < 20, where + ": collecting an experience orb across the seam took " + orbSeconds + " s");

        // A zombie with its AI walks up across the seam and hits; the blow pushes the player back, away from it.
        server.runCommand("gamemode survival @a");
        server.runCommand(place.in("summon minecraft:zombie " + seam.at(half, 3.5, y, mid) + " {PersistenceRequired:1b,Silent:1b}"));
        Vec3 start = context.computeOnClient(mc -> mc.player.position());
        double back = 0;
        boolean hurt = false;
        for (int tick = 0; tick < 200 && !hurt; tick += 2) {
            context.waitTicks(2);
            hurt = context.computeOnClient(mc -> mc.player.getHealth() < mc.player.getMaxHealth());
        }
        check(hurt, where + ": the zombie never hit the player across the seam");
        for (int tick = 0; tick < 8; tick++) {
            context.waitTick();
            Vec3 now = context.computeOnClient(mc -> mc.player.position());
            back = Math.max(back, -seam.along(now.x - start.x, now.z - start.z));
        }
        check(back > 0.2, where + ": the zombie's blow did not push the player away from it: moved back " + back);
        server.runCommand(place.in("kill @e[type=minecraft:zombie]"));
        heal(server);

        // An arrow flying straight back over the seam hits the player before it.
        server.runCommand(place.in("tp @a " + seam.at(half, -3.5, y, mid) + " " + seam.facing(0)));
        context.waitTicks(5);
        server.runCommand(place.in("summon minecraft:arrow " + seam.at(half, 4.5, y + 1.2, mid)
                + " {NoGravity:1b,Motion:" + seam.motion(-1.5) + "}"));
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
        // The arrow stays the player's last damage for two seconds; let it lapse before the skeleton shoots.
        context.waitTicks(50);

        // A skeleton past the seam shoots the player before it; on hard it aims well enough to hit within a few shots.
        server.runCommand("difficulty hard");
        server.runCommand(place.in("summon minecraft:skeleton " + seam.at(half, corner ? 5.5 : 8.5, y, mid) + " {PersistenceRequired:1b,Silent:1b}"));
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
        server.runCommand(place.clear(seam, y));
        heal(server);
        server.runCommand("difficulty normal");
        server.runCommand("gamemode creative @a");
    }

    /** An item thrown over the seam lands past it, and one lying just past it is picked up from before it. */
    private static void checkItemsAcrossSeam(ClientGameTestContext context, TestServerContext server, Place place, Seam seam,
                                             int y, double[] lap, String where) {
        int half = place.half();
        double mid = seam.mid();
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        server.runCommand(place.in("tp @a " + seam.at(half, -1.5, y, mid) + " " + seam.facing(-10)));
        server.runCommand(place.clear(seam, y));
        server.runCommand("give @a minecraft:diamond");
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyDrop);
        context.waitTicks(30);
        Vec3 thrown = server.computeOnServer(sv -> {
            for (var item : place.level(sv).getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.item.ItemEntity.class),
                    e -> e.getItem().is(net.minecraft.world.item.Items.DIAMOND))) return item.position();
            return null;
        });
        check(thrown != null, where + ": the thrown item is gone");
        String landed = seam.folded(half, thrown);
        check(landed == null, where + ": the item thrown over the seam is not past it on the server: " + landed);
        Vec3 seen = context.computeOnClient(mc -> {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().is(net.minecraft.world.item.Items.DIAMOND)) return entity.position();
            }
            return new Vec3(Double.NaN, Double.NaN, Double.NaN);
        });
        String onClient = seam.past(half, seen, lap, 0, 12);
        check(onClient == null, where + ": the client sees the thrown item at " + onClient);
        server.runCommand(place.clear(seam, y));

        server.runCommand(place.in("tp @a " + seam.at(half, -0.6, y, mid) + " " + seam.facing(10)));
        server.runCommand(place.in("summon minecraft:item " + seam.at(half, 0.4, y, mid) + " {PickupDelay:0s,Item:{id:\"minecraft:emerald\",count:1}}"));
        boolean picked = false;
        for (int tick = 0; tick < 40 && !picked; tick += 2) {
            context.waitTicks(2);
            picked = server.computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().getInventory().countItem(net.minecraft.world.item.Items.EMERALD) > 0);
        }
        check(picked, where + ": the item lying just past the seam was not picked up");
        server.runCommand("clear @a");
        server.runCommand(place.clear(seam, y));
        server.runCommand("gamemode creative @a");
    }

    /** Water and lava poured just before the seam flow over it, and the client sees them past it. */
    private static void checkFluidsAcrossSeam(ClientGameTestContext context, TestServerContext server, Place place, Seam seam,
                                              int y, double[] lap, String where, String tag) {
        int half = place.half();
        int lapX = (int) Math.rint(lap[0]), lapZ = (int) Math.rint(lap[1]);
        server.runCommand(place.in("tp @a " + seam.at(half, -8.5, y, seam.mid()) + " " + seam.facing(30)));
        Object[][] fluids = {{"water", -2, 1, Blocks.WATER}, {"lava", -1, 0, Blocks.LAVA}};
        for (Object[] fluid : fluids) {
            BlockPos source = seam.block(half, (int) fluid[1], y, 0), target = seam.block(half, (int) fluid[2], y, 0);
            Block block = (Block) fluid[3];
            server.runCommand(place.in("setblock " + Seam.text(source) + " minecraft:" + fluid[0]));
            boolean flowed = false;
            for (int tick = 0; tick < 300 && !flowed; tick += 5) {
                context.waitTicks(5);
                flowed = server.computeOnServer(sv -> place.level(sv).getBlockState(target).is(block));
            }
            check(flowed, where + ": " + fluid[0] + " did not flow over the seam");
            context.waitTicks(5);
            boolean seen = context.computeOnClient(mc -> mc.level.getBlockState(target.offset(lapX, 0, lapZ)).is(block));
            check(seen, where + ": the client does not see the " + fluid[0] + " past the seam");
            context.takeScreenshot("orbis-" + fluid[0] + "-" + tag);
            server.runCommand(place.in("fill " + seam.box(half, -12, 18, y, y + 3, -2, 2) + " minecraft:air"));
            server.runCommand(place.in("fill " + seam.box(half, -12, 18, y - 1, y - 1, -2, 2) + " minecraft:stone"));
            seam.mark(server, place, -12, 18, y);
        }
        server.runCommand(place.clear(seam, y));
    }

    /**
     * The player rides over the seam: a minecart rolling on rails and a boat steered on ice. The server folds the
     * vehicle and the player together; the client rides on without a jump.
     */
    private static void checkVehiclesAcrossSeam(ClientGameTestContext context, TestServerContext server, Place place, Seam seam,
                                                int y, double[] lap, String where, String tag) {
        int half = place.half();
        double mid = seam.mid();
        String[][] rides = {
                {"minecart", "minecraft:rail", "minecraft:stone", "minecraft:minecart", "{Motion:" + seam.motion(1.2) + "}"},
                {"boat", "minecraft:air", "minecraft:blue_ice", "minecraft:oak_boat", "{Rotation:[" + seam.facing(0).split(" ")[0] + "f,0f]}"}};
        for (String[] ride : rides) {
            int wide = ride[0].equals("boat") ? 1 : 0;
            server.runCommand(place.in("fill " + seam.box(half, -10, 18, y - 1, y - 1, -wide, wide) + " " + ride[2]));
            server.runCommand(place.in("fill " + seam.box(half, -10, 18, y, y, 0, 0) + " " + ride[1]));
            server.runCommand(place.in("tp @a " + seam.at(half, -8.5, y + 0.1, mid) + " " + seam.facing(10)));
            server.runCommand(place.in("summon " + ride[3] + " " + seam.at(half, -8.5, y + 0.1, mid) + " " + ride[4]));
            server.runCommand(place.in("ride @p mount @e[type=" + ride[3] + ",limit=1,sort=nearest]"));
            context.waitTicks(5);
            boolean riding = server.computeOnServer(sv -> sv.getPlayerList().getPlayers().getFirst().isPassenger());
            check(riding, where + ": the player is not in the " + ride[0]);
            if (ride[0].equals("boat")) context.getInput().holdKeyFor(options -> options.keyUp, 80);
            else context.waitTicks(60);
            context.waitTicks(5);
            Vec3[] server2 = server.computeOnServer(sv -> {
                ServerPlayer player = sv.getPlayerList().getPlayers().getFirst();
                return new Vec3[] {player.position(), player.getVehicle() == null ? null : player.getVehicle().position()};
            });
            check(server2[1] != null, where + ": the player fell out of the " + ride[0] + " at the seam");
            String vehicle = seam.folded(half, server2[1]), rider = seam.folded(half, server2[0]);
            check(vehicle == null && rider == null, where + ": the server did not fold the " + ride[0] + " and its rider: " + vehicle + " / " + rider);
            String onClient = seam.past(half, context.computeOnClient(mc -> mc.player.position()), lap, 0, 64);
            check(onClient == null, where + ": the client's ride jumped at the seam: " + onClient);
            context.takeScreenshot("orbis-" + ride[0] + "-" + tag);
            server.runCommand("ride @p dismount");
            server.runCommand(place.clear(seam, y));
        }
        server.runCommand(place.in("fill " + seam.box(half, -10, 18, y - 1, y - 1, -1, 1) + " minecraft:stone"));
        server.runCommand(place.in("fill " + seam.box(half, -10, 18, y, y, 0, 0) + " minecraft:air"));
        seam.mark(server, place, -12, 18, y);
    }

    /**
     * A second player just past the seam: the client sees it there, hits it across the seam, and its blow, facing
     * the client's player the short way, pushes the client's player away from it.
     */
    private static void checkSecondPlayer(ClientGameTestContext context, TestServerContext server, Place place, Seam seam,
                                          int y, double[] lap, String where) {
        int half = place.half();
        double mid = seam.mid();
        server.runCommand("gamemode survival @a");
        server.runCommand(place.in("tp @a " + seam.at(half, seam.corner() ? -0.5 : -1.5, y, mid) + " " + seam.facing(15)));
        Vec3 other = new Vec3(seam.x(half, 1.5, mid), y, seam.z(half, 1.5, mid));
        UUID id = UUID.randomUUID();
        server.runOnServer(sv -> {
            ServerLevel level = place.level(sv);
            var torus = Tori.of(level);
            SecondPlayer second = new SecondPlayer(level, new com.mojang.authlib.GameProfile(id, "Orbis2"));
            second.snapTo(torus.fold(other.x), other.y, torus.fold(other.z), 0, 0);
            sv.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(second)));
            level.addNewPlayer(second);
        });
        context.waitTicks(20);
        String seen = seam.past(half, clientEntity(context, "player"), lap, 0, 4);
        check(seen == null, where + ": the client sees the second player at " + seen);
        String aimed = context.computeOnClient(mc -> mc.crosshairPickEntity == null ? "nothing" : typeName(mc.crosshairPickEntity));
        check(aimed.equals("player"), where + ": the crosshair is on " + aimed + ", not the second player past the seam");
        context.runOnClient(mc -> mc.gameMode.attack(mc.player, mc.crosshairPickEntity));
        context.waitTicks(5);
        float health = server.computeOnServer(sv -> place.level(sv).getPlayerByUUID(id).getHealth());
        check(health < 20, where + ": the hit on the second player past the seam did not count");
        Vec3 start = context.computeOnClient(mc -> mc.player.position());
        server.runOnServer(sv -> {
            ServerPlayer second = (ServerPlayer) place.level(sv).getPlayerByUUID(id);
            ServerPlayer first = sv.getPlayerList().getPlayers().getFirst();
            second.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, first.getEyePosition());
            second.attack(first);
        });
        double back = 0;
        for (int tick = 0; tick < 8; tick++) {
            context.waitTick();
            Vec3 now = context.computeOnClient(mc -> mc.player.position());
            back = Math.max(back, -seam.along(now.x - start.x, now.z - start.z));
        }
        check(back > 0.2, where + ": the second player's blow did not push the player away: moved back " + back);
        server.runOnServer(sv -> {
            Player second = place.level(sv).getPlayerByUUID(id);
            if (second instanceof ServerPlayer player) place.level(sv).removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            sv.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(id)));
        });
        heal(server);
        server.runCommand("gamemode creative @a");
    }

    /**
     * Terrain and caves run on over every seam of both dimensions: the two columns either side of a seam match each
     * other about as well as two neighbouring columns inside the world do.
     */
    private static void checkTerrainAcrossSeams(TestServerContext server) {
        String problem = server.computeOnServer(sv -> {
            for (Place place : new Place[] {Place.OVERWORLD, Place.NETHER}) {
                ServerLevel level = place.level(sv);
                int half = place.half(), bottom = place == Place.NETHER ? 10 : 0, top = place == Place.NETHER ? 118 : 60;
                for (boolean alongX : new boolean[] {true, false}) {
                    double across = 0, inside = 0, inside2 = 0;
                    int samples = 0;
                    for (int c = -64; c < 64; c += 2) {
                        for (int y = bottom; y < top; y++) {
                            boolean last = solid(level, alongX, half - 1, y, c), first = solid(level, alongX, -half, y, c);
                            across += last == first ? 1 : 0;
                            inside += last == solid(level, alongX, half - 2, y, c) ? 1 : 0;
                            inside2 += first == solid(level, alongX, -half + 1, y, c) ? 1 : 0;
                            samples++;
                        }
                    }
                    across /= samples;
                    inside = Math.min(inside, inside2) / samples;
                    System.out.println("Orbis test: " + place.name() + (alongX ? " east/west" : " north/south")
                            + " columns alike across the seam " + across + ", inside " + inside);
                    if (across < inside - 0.1) return place.name() + (alongX ? " east/west" : " north/south")
                            + ": columns across the seam match " + across + ", neighbours inside " + inside;
                }
            }
            return null;
        });
        check(problem == null, "Terrain breaks at the seam: " + problem);
    }

    private static boolean solid(ServerLevel level, boolean alongX, int a, int y, int c) {
        return !level.getBlockState(alongX ? new BlockPos(a, y, c) : new BlockPos(c, y, a)).isAir();
    }

    /**
     * A Nether fortress that generated across a seam: its start lies on one side, and a piece of it lies wholly on
     * the other side, where its nether bricks stand at the folded place. The player looks at it in spectator mode.
     */
    private static void checkFortressAcrossSeam(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server) {
        int half = Place.NETHER.half();
        String found = server.computeOnServer(sv -> {
            ServerLevel level = Place.NETHER.level(sv);
            var torus = Tori.of(level);
            var fortress = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                    .getValue(Identifier.withDefaultNamespace("fortress"));
            int edge = half >> 4;
            for (int a = -edge; a < edge; a++) {
                for (int b : java.util.stream.IntStream.concat(java.util.stream.IntStream.range(edge - 8, edge),
                        java.util.stream.IntStream.range(-edge, -edge + 8)).toArray()) {
                    for (boolean alongX : new boolean[] {true, false}) {
                        var chunk = level.getChunk(alongX ? b : a, alongX ? a : b, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
                        var start = chunk.getStartForStructure(fortress);
                        if (start == null || !start.isValid()) continue;
                        for (var piece : start.getPieces()) {
                            var box = piece.getBoundingBox();
                            int min = alongX ? box.minX() : box.minZ(), max = alongX ? box.maxX() : box.maxZ();
                            if (min < half && max >= -half) continue;
                            // Wholly past the seam: its blocks stand at the folded place.
                            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                                BlockPos at = new BlockPos(torus.fold(pos.getX()), pos.getY(), torus.fold(pos.getZ()));
                                if (level.getBlockState(at).is(Blocks.NETHER_BRICKS)) {
                                    return at.getX() + " " + at.getY() + " " + at.getZ() + " " + (alongX ? "x" : "z");
                                }
                            }
                            return "empty piece " + box;
                        }
                    }
                }
            }
            return null;
        });
        check(found != null && !found.startsWith("empty"), "No Nether fortress generated across the seam: " + found);
        System.out.println("Orbis test: Nether fortress across the seam at " + found);
        String[] at = found.split(" ");
        server.runCommand("gamemode spectator @a");
        server.runCommand(Place.NETHER.in("tp @a " + at[0] + " " + (Integer.parseInt(at[1]) + 6) + " " + at[2] + " 0 60"));
        context.waitTicks(40);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot("orbis-nether-fortress-across-seam");
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

    /** Where the client sees the first entity of a type, or NaN. */
    private static double clientEntityX(ClientGameTestContext context, String type) {
        return context.computeOnClient(mc -> {
            for (Entity entity : mc.level.entitiesForRendering()) if (typeName(entity).equals(type)) return entity.getX();
            return Double.NaN;
        });
    }

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

}
