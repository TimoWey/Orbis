package dev.orbis.gametest;

import static dev.orbis.gametest.SeamWorld.*;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The seam parcours: one saved build with machines that run over the seam, placed across every seam and both corners
 * of the Overworld and the Nether with {@code /place template}, as a structure block would. It is built once in the
 * middle of the world, where it must work, saved like a structure block saves, and its results there are the
 * yardstick for every seam. At each seam it checks that the build arrived whole, then that a dispenser shoots all its
 * items over the seam into hoppers and a chest past it, a minecart circles over it and trips a detector rail on
 * either side, an observer clock straddling it keeps ticking, and a lever lights a lamp past it.
 *
 * <p>The build runs along its x axis, the seam between x 7 (red) and 8 (blue); rotated, the same x axis points
 * across the west, south and north seams. Through a corner the build crosses the east or west seam with its middle
 * row on the other seam, so the minecart's curves and the lanes beside them also cross that one.
 */
public final class ParcoursClientGameTest implements FabricClientGameTest {
    static final Identifier ID = Identifier.fromNamespaceAndPath("orbis", "seam_parcours");
    /** Size of the build; the seam lies between x {@code SEAM - 1} and {@code SEAM}, the middle row at z {@code MIDDLE}. */
    static final int LENGTH = 17, HEIGHT = 5, WIDTH = 13, SEAM = 8, MIDDLE = 6;
    private static final int ITEMS = 9;
    /**
     * Properties the placement check ignores: those the machines change while they run, and water, which a placed
     * template lets into blocks at its edge that can hold it when it lands beside the sea (a corner does).
     */
    private static final Set<Property<?>> RUNNING = Set.of(BlockStateProperties.POWERED, BlockStateProperties.LIT,
            BlockStateProperties.ENABLED, BlockStateProperties.TRIGGERED, BlockStateProperties.POWER, BlockStateProperties.WATERLOGGED);

    // Where the machines sit, in the build's own coordinates.
    private static final BlockPos DISPENSER = new BlockPos(6, 1, 1), DISPENSER_POWER = new BlockPos(5, 1, 1), CHEST = new BlockPos(16, 0, 1);
    private static final BlockPos DETECTOR_PAST = new BlockPos(11, 1, 4), DETECTOR_BEFORE = new BlockPos(5, 1, 6);
    private static final BlockPos LAMP_PAST_RAIL = new BlockPos(11, 1, 3), LAMP_BEFORE_RAIL = new BlockPos(5, 1, 7);
    private static final BlockPos CART = new BlockPos(3, 1, 4);
    private static final BlockPos OBSERVER_BEFORE = new BlockPos(7, 1, 9), OBSERVER_PAST = new BlockPos(8, 1, 9);
    private static final BlockPos LEVER = new BlockPos(4, 1, 11), LEVER_LAMP = new BlockPos(12, 1, 11);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = createTiny(context)) {
            var server = world.getServer();
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule spawn_monsters false");
            fly(server);
            // The reference: built in the middle, far from every seam, saved, and run there first.
            int y = surface(context, world, 0, 0);
            Placed reference = new Placed(Place.OVERWORLD, origin(new BlockPos(0, y - 1, 0), Rotation.NONE), Rotation.NONE, "overworld-middle");
            prepare(context, world, server, reference);
            server.runOnServer(s -> build(reference.place.level(s), reference));
            context.waitTicks(5);
            String built = server.computeOnServer(s -> mismatch(reference.place.level(s), reference));
            check(built == null, "parcours not built as planned: " + built);
            server.runOnServer(s -> save(reference.place.level(s), reference));
            run(context, world, server, reference);

            for (Place place : new Place[] {Place.OVERWORLD, Place.NETHER}) {
                for (Seam seam : Seam.SIDES) atSeam(context, world, server, place, seam);
                for (Seam seam : Seam.CORNERS) atSeam(context, world, server, place, seam);
            }
        }
    }

    /** The build placed somewhere: block {@code (x, y, z)} of the build lands at {@link #at}. */
    record Placed(Place place, BlockPos origin, Rotation rotation, String tag) {
        BlockPos at(BlockPos local) { return origin.offset(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO)); }

        BlockPos at(int x, int y, int z) { return at(new BlockPos(x, y, z)); }

        /** The build's x axis, across the seam, in the world. */
        Direction across() { return rotation.rotate(Direction.EAST); }

        String where() { return place.name() + " " + tag; }
    }

    /** The origin that puts block {@code (SEAM, 0, MIDDLE)} of the build, the first floor block past the seam, at {@code target}. */
    private static BlockPos origin(BlockPos target, Rotation rotation) {
        return target.subtract(StructureTemplate.transform(new BlockPos(SEAM, 0, MIDDLE), Mirror.NONE, rotation, BlockPos.ZERO));
    }

    private static Rotation rotation(Seam seam) {
        if (seam.sx() > 0) return Rotation.NONE;
        if (seam.sx() < 0) return Rotation.CLOCKWISE_180;
        return seam.sz() > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
    }

    private static void atSeam(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, Place place, Seam seam) {
        int y = floor(context, world, place, seam);
        Rotation rotation = rotation(seam);
        Placed placed = new Placed(place, origin(seam.block(place.half(), 0, y - 1, 0), rotation), rotation, seam.name());
        prepare(context, world, server, placed);
        String result = server.computeOnServer(s -> {
            var source = s.createCommandSourceStack().withSuppressedOutput().withLevel(place.level(s)).withPosition(Vec3.atCenterOf(placed.origin));
            String command = "place template " + ID + " " + Seam.text(placed.origin) + " " + rotation.getSerializedName();
            try {
                return s.getCommands().getDispatcher().execute(command, source) > 0 ? null : command + " placed nothing";
            } catch (CommandSyntaxException e) {
                return command + ": " + e.getMessage();
            }
        });
        check(result == null, placed.where() + ": " + result);
        context.waitTicks(5);
        String arrived = server.computeOnServer(s -> mismatch(place.level(s), placed));
        check(arrived == null, placed.where() + ": the parcours did not arrive whole across the seam: " + arrived);
        run(context, world, server, placed);
    }

    /** Loads everything around the build, clears its mobs, and opens the air above it, where the camera hovers. */
    private static void prepare(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, Placed placed) {
        int[][] corners = {{-3, -3}, {LENGTH + 2, -3}, {-3, WIDTH + 8}, {LENGTH + 2, WIDTH + 8}, {SEAM, MIDDLE}};
        for (int[] c : corners) waitLoaded(context, world, placed.place.key(), placed.at(c[0], 0, c[1]));
        for (int[] c : corners) {
            BlockPos at = placed.at(c[0], 1, c[1]);
            for (int tick = 0; !server.computeOnServer(s -> placed.place.level(s).areEntitiesLoaded(ChunkPos.pack(at))); tick++) {
                check(tick < 600, placed.where() + ": the entities at " + at.toShortString() + " never loaded");
                context.waitTick();
            }
        }
        BlockPos middle = placed.at(SEAM, 1, MIDDLE);
        server.runCommand(placed.place.in("execute positioned " + Seam.text(middle) + " run kill @e[type=!minecraft:player,distance=..40]"));
        server.runCommand(placed.place.in("fill " + box(placed.at(-3, HEIGHT, -3), placed.at(LENGTH + 2, HEIGHT + 9, WIDTH + 8)) + " minecraft:air"));
    }

    private static String box(BlockPos a, BlockPos b) {
        return Math.min(a.getX(), b.getX()) + " " + Math.min(a.getY(), b.getY()) + " " + Math.min(a.getZ(), b.getZ()) + " "
                + Math.max(a.getX(), b.getX()) + " " + Math.max(a.getY(), b.getY()) + " " + Math.max(a.getZ(), b.getZ());
    }

    /** The build, block by block in its own coordinates, air included, so placing it clears the ground it lands on. */
    static Map<BlockPos, BlockState> plan() {
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        BlockState glass = Blocks.GLASS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                // The seam marked in red before it and blue past it, in the floor, the walls and the roof.
                boolean before = x == SEAM - 1, past = x == SEAM;
                BlockState wall = before ? Blocks.STAINED_GLASS.red().defaultBlockState() : past ? Blocks.STAINED_GLASS.blue().defaultBlockState() : glass;
                plan.put(new BlockPos(x, 0, z), before ? Blocks.CONCRETE.red().defaultBlockState() : past ? Blocks.CONCRETE.blue().defaultBlockState() : Blocks.STONE.defaultBlockState());
                boolean outer = x == 0 || x == LENGTH - 1 || z == 0 || z == WIDTH - 1, divider = z == 2 || z == 8 || z == 10;
                for (int y = 1; y < HEIGHT - 1; y++) plan.put(new BlockPos(x, y, z), outer || divider ? wall : air);
                boolean light = (x == 3 || x == 13) && z % 4 == 1;
                plan.put(new BlockPos(x, HEIGHT - 1, z), light ? Blocks.GLOWSTONE.defaultBlockState() : wall);
            }
        }
        // Lane 1: a dispenser before the seam shoots over it onto a row of hoppers that feed a chest at the far end.
        plan.put(DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        for (int x = SEAM; x < LENGTH - 1; x++) {
            plan.put(new BlockPos(x, 0, 1), Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER, Direction.EAST));
        }
        plan.put(CHEST, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        // Lanes 3 to 7: a minecart loop from x 2 to 14; out over the seam on z 4, back over it on z 6.
        for (int x = 3; x <= 13; x++) {
            for (int z : new int[] {4, 6}) plan.put(new BlockPos(x, 1, z), rail(Blocks.RAIL, RailShape.EAST_WEST));
        }
        plan.put(new BlockPos(2, 1, 4), rail(Blocks.RAIL, RailShape.SOUTH_EAST));
        plan.put(new BlockPos(2, 1, 5), rail(Blocks.RAIL, RailShape.NORTH_SOUTH));
        plan.put(new BlockPos(2, 1, 6), rail(Blocks.RAIL, RailShape.NORTH_EAST));
        plan.put(new BlockPos(14, 1, 4), rail(Blocks.RAIL, RailShape.SOUTH_WEST));
        plan.put(new BlockPos(14, 1, 5), rail(Blocks.RAIL, RailShape.NORTH_SOUTH));
        plan.put(new BlockPos(14, 1, 6), rail(Blocks.RAIL, RailShape.NORTH_WEST));
        for (int x : new int[] {4, 12}) {
            for (int z : new int[] {4, 6}) {
                plan.put(new BlockPos(x, 1, z), rail(Blocks.POWERED_RAIL, RailShape.EAST_WEST).setValue(BlockStateProperties.POWERED, true));
                plan.put(new BlockPos(x, 0, z), Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        }
        plan.put(DETECTOR_PAST, rail(Blocks.DETECTOR_RAIL, RailShape.EAST_WEST));
        plan.put(DETECTOR_BEFORE, rail(Blocks.DETECTOR_RAIL, RailShape.EAST_WEST));
        plan.put(LAMP_PAST_RAIL, Blocks.REDSTONE_LAMP.defaultBlockState());
        plan.put(LAMP_BEFORE_RAIL, Blocks.REDSTONE_LAMP.defaultBlockState());
        // Lane 9: two observers face each other over the seam, a clock that only runs while each sees the other.
        plan.put(new BlockPos(6, 1, 9), Blocks.REDSTONE_LAMP.defaultBlockState());
        plan.put(OBSERVER_BEFORE, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        plan.put(OBSERVER_PAST, Blocks.OBSERVER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
        plan.put(new BlockPos(9, 1, 9), Blocks.REDSTONE_LAMP.defaultBlockState());
        // Lane 11: a lever before the seam, redstone dust over it, a lamp past it.
        plan.put(LEVER, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        for (int x = LEVER.getX() + 1; x < LEVER_LAMP.getX(); x++) {
            plan.put(new BlockPos(x, 1, 11), Blocks.REDSTONE_WIRE.defaultBlockState()
                    .setValue(BlockStateProperties.EAST_REDSTONE, RedstoneSide.SIDE).setValue(BlockStateProperties.WEST_REDSTONE, RedstoneSide.SIDE));
        }
        plan.put(LEVER_LAMP, Blocks.REDSTONE_LAMP.defaultBlockState());
        return plan;
    }

    private static BlockState rail(Block block, RailShape shape) {
        BlockState state = block.defaultBlockState();
        return state.setValue(block == Blocks.RAIL ? BlockStateProperties.RAIL_SHAPE : BlockStateProperties.RAIL_SHAPE_STRAIGHT, shape);
    }

    /** Builds the reference by hand: blocks as planned, without updates that would reshape rails mid-build, then the cart. */
    private static void build(ServerLevel level, Placed placed) {
        var plan = plan();
        for (int pass = 0; pass < 2; pass++) {
            for (var block : plan.entrySet()) level.setBlock(placed.at(block.getKey()), block.getValue(), Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(placed.at(DISPENSER)) instanceof Container dispenser) dispenser.setItem(0, new ItemStack(Items.DIAMOND, ITEMS));
        BlockPos cart = placed.at(CART);
        var server = level.getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput().withLevel(level);
        server.getCommands().performPrefixedCommand(source, "summon minecraft:minecart " + (cart.getX() + 0.5) + " " + (cart.getY() + 0.0625) + " " + (cart.getZ() + 0.5) + " {Tags:[\"orbis_parcours\"]}");
    }

    /** Saves the reference like a structure block's Save, blocks and minecart, and exports it for loading in any world. */
    private static void save(ServerLevel level, Placed placed) {
        StructureTemplateManager templates = level.getServer().getStructureTemplateManager();
        StructureTemplate template = templates.getOrCreate(ID);
        template.fillFromWorld(level, placed.origin, new BlockPos(LENGTH, HEIGHT, WIDTH), true, java.util.List.of());
        template.setAuthor("Orbis");
        Path export = FabricLoader.getInstance().getGameDir().resolve("orbis-parcours").resolve(ID.getPath() + ".nbt");
        try {
            check(StructureTemplateManager.save(export, template, false), "could not export the parcours to " + export);
        } catch (IOException e) {
            throw new AssertionError("could not export the parcours to " + export, e);
        }
        System.out.println("Orbis parcours: saved as " + ID + ", exported to " + export);
    }

    /** Every planned block where the build should be, the running machines' power aside; null when all are. */
    private static @Nullable String mismatch(ServerLevel level, Placed placed) {
        StringBuilder out = new StringBuilder();
        int wrong = 0;
        for (var block : plan().entrySet()) {
            BlockState want = block.getValue().rotate(placed.rotation), have = level.getBlockState(placed.at(block.getKey()));
            if (same(want, have)) continue;
            if (wrong++ < 6) out.append(" ").append(block.getKey().toShortString()).append(": ").append(have).append(" not ").append(want).append(";");
        }
        int carts = carts(level).size();
        if (carts != 1) out.append(" ").append(carts).append(" minecarts;");
        if (!(level.getBlockEntity(placed.at(DISPENSER)) instanceof Container dispenser) || dispenser.countItem(Items.DIAMOND) != ITEMS) {
            out.append(" the dispenser is not loaded with ").append(ITEMS).append(" diamonds;");
        }
        return out.isEmpty() ? null : wrong + " blocks wrong:" + out;
    }

    private static boolean same(BlockState want, BlockState have) {
        if (want.getBlock() != have.getBlock()) return false;
        for (Property<?> property : want.getProperties()) {
            if (!RUNNING.contains(property) && !want.getValue(property).equals(have.getValue(property))) return false;
        }
        return true;
    }

    private static java.util.List<? extends AbstractMinecart> carts(ServerLevel level) {
        return level.getEntities(EntityTypeTest.forClass(AbstractMinecart.class), e -> e.entityTags().contains("orbis_parcours"));
    }

    private static void fly(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
    }

    /** Runs the machines of one placed build and checks each result. */
    private static void run(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, Placed placed) {
        String where = placed.where();
        // The camera hovers beside the build, looking down at it, before the seam on the left and past it on the right.
        BlockPos eye = placed.at(SEAM, HEIGHT + 7, WIDTH + 4);
        Direction look = placed.rotation.rotate(Direction.NORTH);
        int yaw = (int) Math.toDegrees(Math.atan2(-look.getStepX(), look.getStepZ()));
        server.runCommand(placed.place.in("tp @a " + eye.getX() + ".5 " + eye.getY() + " " + eye.getZ() + ".5 " + yaw + " 50"));
        fly(server);
        context.waitTicks(20);
        world.getConnection().waitForChunksRender();

        // The dispenser fires all its diamonds; every one must end up in the hoppers or the chest past the seam.
        for (int shot = 0; shot < ITEMS; shot++) {
            server.runOnServer(s -> placed.place.level(s).setBlock(placed.at(DISPENSER_POWER), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL));
            context.waitTicks(6);
            server.runOnServer(s -> placed.place.level(s).setBlock(placed.at(DISPENSER_POWER), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            context.waitTicks(6);
        }
        int caught = 0;
        String lying = "";
        for (int tick = 0; tick < 200 && caught < ITEMS; tick += 10) {
            context.waitTicks(10);
            caught = server.computeOnServer(s -> {
                var level = placed.place.level(s);
                int count = 0;
                for (int x = SEAM; x <= CHEST.getX(); x++) {
                    BlockPos at = x == CHEST.getX() ? CHEST : new BlockPos(x, 0, 1);
                    if (level.getBlockEntity(placed.at(at)) instanceof Container container) count += container.countItem(Items.DIAMOND);
                }
                return count;
            });
        }
        if (caught < ITEMS) {
            lying = server.computeOnServer(s -> {
                StringBuilder out = new StringBuilder();
                for (ItemEntity item : placed.place.level(s).getEntities(EntityTypeTest.forClass(ItemEntity.class), e -> e.getItem().is(Items.DIAMOND))) {
                    out.append(" ").append(item.position());
                }
                var level = placed.place.level(s);
                var torus = dev.orbis.engine.Tori.of(level);
                if (level.getBlockEntity(placed.at(DISPENSER)) instanceof Container d) out.append("; dispenser holds ").append(d.countItem(Items.DIAMOND));
                for (int x = SEAM; x <= CHEST.getX(); x++) {
                    BlockPos at = placed.at(x == CHEST.getX() ? CHEST : new BlockPos(x, 0, 1));
                    BlockPos folded = new BlockPos(torus.fold(at.getX()), at.getY(), torus.fold(at.getZ()));
                    var raw = level.getBlockEntity(at);
                    var fold = level.getBlockEntity(folded);
                    out.append("; ").append(at.toShortString()).append(" ").append(level.getBlockState(at).getBlock())
                            .append(" be=").append(raw == null ? "null" : raw.getBlockPos().toShortString() + "/" + ((Container) raw).countItem(Items.DIAMOND))
                            .append(" folded be=").append(fold == null ? "null" : ((Container) fold).countItem(Items.DIAMOND));
                }
                return out.toString();
            });
        }
        check(caught == ITEMS, where + ": only " + caught + " of " + ITEMS + " diamonds shot over the seam reached the chest; lying at" + lying);

        // The lever lights the lamp past the seam.
        server.runOnServer(s -> setLever(placed.place.level(s), placed, true));
        context.waitTicks(6);
        boolean lit = server.computeOnServer(s -> placed.place.level(s).getBlockState(placed.at(LEVER_LAMP)).getValue(BlockStateProperties.LIT));
        check(lit, where + ": the lever before the seam does not light the lamp past it");

        // The observer clock over the seam ticks; if placing it did not start it, one nudge does.
        int[] flips = observerFlips(context, server, placed, 40);
        if (flips[0] == 0 && flips[1] == 0) {
            server.runOnServer(s -> {
                var level = placed.place.level(s);
                BlockState observer = level.getBlockState(placed.at(OBSERVER_PAST));
                level.setBlock(placed.at(OBSERVER_PAST), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(placed.at(OBSERVER_PAST), observer, Block.UPDATE_ALL);
            });
            flips = observerFlips(context, server, placed, 40);
        }
        check(flips[0] >= 4 && flips[1] >= 4, where + ": the observer clock over the seam stalled: " + flips[0] + " and " + flips[1] + " flips");

        // The minecart circles: out over the seam past one detector rail, back over it past the other, twice each.
        Direction across = placed.across();
        server.runOnServer(s -> {
            for (AbstractMinecart cart : carts(placed.place.level(s))) cart.setDeltaMovement(across.getStepX() * 0.4, 0, across.getStepZ() * 0.4);
        });
        int past = 0, before = 0;
        boolean wasPast = false, wasBefore = false, lampPast = false, lampBefore = false, shot = false;
        String cartAt = "";
        for (int tick = 0; tick < 600 && (past < 2 || before < 2 || !lampPast || !lampBefore); tick++) {
            context.waitTick();
            boolean[] state = server.computeOnServer(s -> {
                var level = placed.place.level(s);
                return new boolean[] {
                        level.getBlockState(placed.at(DETECTOR_PAST)).getValue(BlockStateProperties.POWERED),
                        level.getBlockState(placed.at(DETECTOR_BEFORE)).getValue(BlockStateProperties.POWERED),
                        level.getBlockState(placed.at(LAMP_PAST_RAIL)).getValue(BlockStateProperties.LIT),
                        level.getBlockState(placed.at(LAMP_BEFORE_RAIL)).getValue(BlockStateProperties.LIT)};
            });
            if (state[0] && !wasPast) past++;
            if (state[1] && !wasBefore) before++;
            wasPast = state[0];
            wasBefore = state[1];
            lampPast |= state[2];
            lampBefore |= state[3];
            if (state[2] && !shot) {
                context.takeScreenshot("parcours-" + placed.place.name() + "-" + placed.tag);
                shot = true;
            }
        }
        if (past < 2 || before < 2) {
            cartAt = server.computeOnServer(s -> {
                StringBuilder out = new StringBuilder();
                for (Entity cart : carts(placed.place.level(s))) out.append(" ").append(cart.position()).append(" moving ").append(cart.getDeltaMovement());
                return out.toString();
            });
        }
        check(past >= 2 && before >= 2 && lampPast && lampBefore, where + ": the minecart tripped the detector past the seam " + past
                + " times and the one before it " + before + " times (lamps " + lampPast + ", " + lampBefore + "); cart at" + cartAt);

        server.runOnServer(s -> setLever(placed.place.level(s), placed, false));
        context.waitTicks(10);
        boolean dark = !server.computeOnServer(s -> placed.place.level(s).getBlockState(placed.at(LEVER_LAMP)).getValue(BlockStateProperties.LIT));
        check(dark, where + ": the lamp past the seam stays lit after the lever is off");
        System.out.println("Orbis parcours " + where + ": " + caught + "/" + ITEMS + " diamonds over the seam, lever lamp on and off, observer clock "
                + flips[0] + "/" + flips[1] + " flips, minecart detectors " + past + "/" + before);
        server.runCommand(placed.place.in("kill @e[tag=orbis_parcours]"));
    }

    private static void setLever(ServerLevel level, Placed placed, boolean on) {
        BlockPos at = placed.at(LEVER);
        BlockState lever = level.getBlockState(at);
        level.setBlock(at, lever.setValue(BlockStateProperties.POWERED, on), Block.UPDATE_ALL);
        level.updateNeighborsAt(at.below(), lever.getBlock());
    }

    /** How often each observer of the clock switched over {@code ticks} ticks. */
    private static int[] observerFlips(ClientGameTestContext context, TestServerContext server, Placed placed, int ticks) {
        int[] flips = new int[2];
        boolean[] was = null;
        for (int tick = 0; tick < ticks; tick++) {
            context.waitTick();
            boolean[] now = server.computeOnServer(s -> new boolean[] {
                    placed.place.level(s).getBlockState(placed.at(OBSERVER_BEFORE)).getValue(BlockStateProperties.POWERED),
                    placed.place.level(s).getBlockState(placed.at(OBSERVER_PAST)).getValue(BlockStateProperties.POWERED)});
            if (was != null) {
                if (now[0] != was[0]) flips[0]++;
                if (now[1] != was[1]) flips[1]++;
            }
            was = now;
        }
        return flips;
    }
}
