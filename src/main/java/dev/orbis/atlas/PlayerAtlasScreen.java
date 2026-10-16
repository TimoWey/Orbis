package dev.orbis.atlas;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Quiet player interface: the map fills the screen and its few controls sit on it. Map or globe at the top left,
 * close at the top right, zoom, centre and the minimap settings (behind the gear) at the bottom right.
 */
final class PlayerAtlasScreen extends Screen {
    // SDL scancode of numpad minus; InputConstants names numpad plus (KEY_ADD) but not this one.
    private static final int KEY_SUBTRACT = 86;
    /** How far past one world width the flat map zooms out. */
    private static final double WORLD_ROOM = 1.05;
    private static final int MARGIN = 6, PAD = 6, ICON = 20, GAP = 2, PANEL = 116;
    // Pixel pictures for the icon buttons, so they share one style whatever the font has.
    private static final String[] CLOSE = {
            "#......#", ".#....#.", "..#..#..", "...##...", "...##...", "..#..#..", ".#....#.", "#......#"};
    private static final String[] PLUS = {
            "...##...", "...##...", "...##...", "########", "########", "...##...", "...##...", "...##..."};
    private static final String[] MINUS = {"########", "########"};
    private static final String[] CENTER = {
            "....#....", "..#####..", ".#..#..#.", ".#.....#.", "###.#.###", ".#.....#.", ".#..#..#.", "..#####..", "....#...."};
    private static final String[] GEAR = {
            ".....##.....", "..#.####.#..", ".##########.", "..###..###..", ".###....###.", "####....####",
            "####....####", ".###....###.", "..###..###..", ".##########.", "..#.####.#..", ".....##....."};
    // Kept between openings, so the map is there at once when the screen opens again.
    private static final AtlasMapTexture texture = new AtlasMapTexture();
    private final java.util.List<Button> settings = new java.util.ArrayList<>();
    private AtlasSnapshot data;
    private Button globeButton;
    private String dimension;
    private int left, top, mapWidth, mapHeight;
    private boolean globe, follow = true, dragging, zoomed, zoomKeyHandled, settingsOpen;
    private double x, z, yaw, pitch, blocks = 512, globeZoom = 1;
    // Zoom glides toward these; the wheel and the buttons only move the goal.
    private double blocksGoal = 512, globeZoomGoal = 1;
    private long lastFrame;
    PlayerAtlasScreen() { super(Component.translatable("orbis.atlas.title")); }
    private String tr(String key) { return Component.translatable("orbis.player." + key).getString(); }
    /** The shorter side of the map: the flat zoom counts blocks across it, and the globe fits inside it. */
    private int side() { return Math.min(mapWidth, mapHeight); }
    @Override protected void init() {
        settings.clear();
        left = MARGIN; top = MARGIN;
        mapWidth = Math.max(64, width - 2 * MARGIN); mapHeight = Math.max(64, height - 2 * MARGIN);
        int right = left + mapWidth - PAD, bottom = top + mapHeight - PAD;
        int modeWidth = Math.max(font.width(tr("flat")), font.width(tr("globe"))) + 12;
        button(left + PAD, top + PAD, modeWidth, tr("flat"), b -> mode(false), () -> !globe);
        globeButton = button(left + PAD + modeWidth, top + PAD, modeWidth, tr("globe"), b -> mode(true), () -> globe);
        icon(right - ICON, top + PAD, CLOSE, tr("close"), b -> onClose());
        int column = right - ICON;
        icon(column, bottom - 4 * ICON - 3 * GAP, PLUS, tr("zoom_in"), b -> zoom(1.25));
        icon(column, bottom - 3 * ICON - 2 * GAP, MINUS, tr("zoom_out"), b -> zoom(1 / 1.25));
        icon(column, bottom - 2 * ICON - GAP, CENTER, tr("center"), b -> follow = true);
        icon(column, bottom - ICON, GEAR, tr("settings"), b -> showSettings(!settingsOpen));
        // The minimap settings open beside the gear, bottom row level with it.
        int panelX = column - GAP - PANEL - 4, rowY = bottom - ICON;
        setting(panelX, rowY - 4 * (ICON + GAP), repeatLabel(), b -> { AtlasPreferences.toggleRepeat(); b.setMessage(Component.literal(repeatLabel())); });
        setting(panelX, rowY - 3 * (ICON + GAP), miniZoomLabel(), b -> { AtlasPreferences.cycleMinimapZoom(); b.setMessage(Component.literal(miniZoomLabel())); });
        setting(panelX, rowY - 2 * (ICON + GAP), sizeLabel(), b -> { AtlasPreferences.cycleSize(); b.setMessage(Component.literal(sizeLabel())); });
        setting(panelX, rowY - (ICON + GAP), cornerLabel(), b -> { AtlasPreferences.cycleCorner(); b.setMessage(Component.literal(cornerLabel())); });
        setting(panelX, rowY, visibleLabel(), b -> { AtlasPreferences.toggleVisible(); b.setMessage(Component.literal(visibleLabel())); });
        showSettings(settingsOpen);
    }
    private String repeatLabel() { return tr(AtlasPreferences.repeat() ? "map_repeat" : "map_square"); }
    private String visibleLabel() { return tr(AtlasPreferences.visible() ? "minimap_on" : "minimap_off"); }
    private String cornerLabel() { return tr("corner_" + AtlasPreferences.corner()); }
    private String sizeLabel() { return tr("size") + ": " + new String[]{"S", "M", "L"}[AtlasPreferences.sizeIndex()]; }
    private String miniZoomLabel() {
        double zoom = AtlasPreferences.minimapZoom();
        String shown = zoom == Math.rint(zoom) ? String.valueOf((int) zoom) : zoom == .5 ? "½" : zoom == .25 ? "¼" : String.valueOf(zoom);
        return tr("mini_zoom") + ": " + shown + "×";
    }
    void showSettings(boolean open) {
        settingsOpen = open;
        for (Button b : settings) b.visible = open;
    }
    private void setting(int x, int y, String label, Button.OnPress action) {
        settings.add(button(x, y, PANEL, label, action));
    }
    private void icon(int x, int y, String[] picture, String name, Button.OnPress action) {
        Button b = addRenderableWidget(new AtlasButton(x, y, ICON, ICON, name, action, () -> false, false).picture(picture));
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(name)));
    }
    private Button button(int x, int y, int w, String label, Button.OnPress action) {
        return button(x, y, w, label, action, () -> false);
    }
    private Button button(int x, int y, int w, String label, Button.OnPress action, java.util.function.BooleanSupplier selected) {
        return addRenderableWidget(new AtlasButton(x, y, w, ICON, label, action, selected, false));
    }
    @Override public void tick() {
        if (minecraft.level == null || minecraft.player == null) { onClose(); return; }
        data = PlayerAtlasClient.snapshot;
        globeButton.active = data != null && data.wrapped();
        if (data != null && !data.dimension().equals(dimension)) {
            saveZoom();
            dimension = data.dimension(); globe = AtlasPreferences.globe(dimension) && data.wrapped();
            blocks = blocksGoal = AtlasPreferences.zoom(dimension, false);
            globeZoom = globeZoomGoal = AtlasPreferences.zoom(dimension, true); follow = true;
        }
    }
    private void mode(boolean value) {
        if (data == null || value && !data.wrapped()) return;
        globe = value; follow = true; AtlasPreferences.setGlobe(dimension, globe);
    }
    private void zoom(double factor) {
        // Before the first snapshot the stored zoom has not been read yet; it would replace this one.
        if (dimension == null) return;
        if (globe) globeZoomGoal = Math.clamp(globeZoomGoal * factor, .5, 12);
        // Fully zoomed out, a little room stays around the world, so its edge shows on every side.
        else blocksGoal = Math.clamp(blocksGoal / factor, 32, data != null && data.wrapped() ? data.span() * WORLD_ROOM : 30000000);
        zoomed = true;
    }
    private void saveZoom() {
        if (zoomed && dimension != null) AtlasPreferences.saveZoom(dimension, blocksGoal, globeZoomGoal);
        zoomed = false;
    }
    /** Flat-map blocks across while open; preferences only catch up when the screen closes. */
    double flatZoom(String dimension) { return dimension.equals(this.dimension) ? blocksGoal : AtlasPreferences.zoom(dimension, false); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // Key and text events of one press are delivered before the frame that follows them.
        zoomKeyHandled = false;
        glide();
        g.fill(0, 0, width, height, 0xd9182028);
        String place = null;
        if (data != null && minecraft.player != null) {
            if (follow) {
                x = minecraft.player.getX(); z = minecraft.player.getZ();
                if (data.wrapped()) { x = AtlasMapMath.canonical(x, data.minX(), data.span()); z = AtlasMapMath.canonical(z, data.minZ(), data.span()); }
                yaw = -((x - data.minX()) / data.span() - .5) * Math.PI * 2;
                pitch = (.5 - (z - data.minZ()) / data.span()) * Math.PI;
            }
            // Every frame, as zooming changes how much of the square fits.
            if (!globe && square()) keep();
            var view = new AtlasMapRaster.View(mapWidth, mapHeight, x, z, globe ? globeZoom : side() / blocks, globe, yaw, pitch, false, false);
            g.enableScissor(left, top, left + mapWidth, top + mapHeight);
            texture.draw(g, data, view, left, top);
            if (!globe && square()) border(g);
            var shown = texture.displayedView();
            if (shown != null) {
                AtlasPlayersClient.draw(g, data, shown, left, top, mx, my, true);
                double px = follow && globe ? shown.centerX() : minecraft.player.getX();
                double pz = follow && globe ? shown.centerZ() : minecraft.player.getZ();
                var point = AtlasPlayerMarker.project(data, shown, px, pz);
                if (point != null) AtlasPlayerMarker.draw(g, left + (int) point[0], top + (int) point[1], AtlasPlayerMarker.direction(globe,
                        (px - data.minX()) / data.span(), (pz - data.minZ()) / data.span(), shown.yaw(), shown.pitch(), minecraft.player.getYRot()));
            }
            g.disableScissor();
            place = place(mx, my);
        }
        g.outline(left, top, mapWidth, mapHeight, 0xff3b4c5a);
        String title = "ORBIS · " + tr("atlas");
        label(g, title, left + (mapWidth - font.width(title)) / 2, top + PAD + 6, 0xff70dfbd);
        int infoY = top + mapHeight - PAD - 10;
        if (place != null) label(g, place, left + PAD + 2, infoY, 0xffe6edf3);
        if (!AtlasPreferences.status().isEmpty()) label(g, AtlasPreferences.status(), left + PAD + 2, infoY - 14, 0xffafbecf);
        if (settingsOpen && !settings.isEmpty()) {
            Button first = settings.getFirst(), last = settings.getLast();
            g.fill(first.getX() - 4, first.getY() - 4, first.getRight() + 4, last.getBottom() + 4, 0xe0101820);
            g.outline(first.getX() - 4, first.getY() - 4, PANEL + 8, last.getBottom() - first.getY() + 8, 0xff3b4c5a);
        }
        super.extractRenderState(g, mx, my, delta);
    }
    /** Moves the zoom a frame's share of the way to its goal, evenly in steps of scale. */
    private void glide() {
        long now = System.nanoTime();
        double seconds = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / 1e9);
        lastFrame = now;
        double share = 1 - Math.exp(-seconds / .07);
        blocks = approach(blocks, blocksGoal, share);
        globeZoom = approach(globeZoom, globeZoomGoal, share);
    }
    private static double approach(double value, double goal, double share) {
        double next = Math.exp(Math.log(value) + (Math.log(goal) - Math.log(value)) * share);
        return Math.abs(next / goal - 1) < 1e-3 ? goal : next;
    }
    /** The flat map shows the world once, as a square, instead of continuing past its edges. */
    private boolean square() { return data != null && data.wrapped() && !AtlasPreferences.repeat(); }
    /** Darkens the map outside the world's square and outlines the world's edge. */
    private void border(GuiGraphicsExtractor g) {
        double perBlock = side() / blocks;
        int x0 = (int) Math.round(left + mapWidth * .5 + (data.minX() - x) * perBlock);
        int z0 = (int) Math.round(top + mapHeight * .5 + (data.minZ() - z) * perBlock);
        int x1 = (int) Math.round(x0 + data.span() * perBlock), z1 = (int) Math.round(z0 + data.span() * perBlock);
        int right = left + mapWidth, bottom = top + mapHeight, outside = 0xff141c24;
        g.fill(left, top, right, Math.max(top, z0), outside);
        g.fill(left, Math.min(bottom, z1), right, bottom, outside);
        g.fill(left, Math.max(top, z0), Math.max(left, x0), Math.min(bottom, z1), outside);
        g.fill(Math.min(right, x1), Math.max(top, z0), right, Math.min(bottom, z1), outside);
        g.outline(x0 - 1, z0 - 1, x1 - x0 + 2, z1 - z0 + 2, 0xff70dfbd);
    }
    /**
     * The centre of a square map along one axis showing {@code shown} blocks: the world fills the view where it can,
     * and a view wider than the world keeps it in the middle.
     */
    private double fit(double centre, int min, double shown) {
        if (shown >= data.span()) return min + data.span() * .5;
        return Math.clamp(centre, min + shown * .5, min + data.span() - shown * .5);
    }
    /** Keeps the centre of a square map on the world, where wrapping would jump it to the other side. */
    private void keep() {
        if (square()) {
            x = fit(x, data.minX(), mapWidth * blocks / side()); z = fit(z, data.minZ(), mapHeight * blocks / side());
        } else if (data.wrapped()) {
            x = AtlasMapMath.canonical(x, data.minX(), data.span()); z = AtlasMapMath.canonical(z, data.minZ(), data.span());
        }
    }
    /** Text on a dark backing, readable over any terrain. */
    private void label(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.fill(x - 3, y - 2, x + font.width(text) + 3, y + 10, 0xb0101820);
        g.text(font, text, x, y, color, false);
    }
    /** Block coordinates under the pointer on the flat map; elsewhere the player's own position. */
    private String place(int mx, int my) {
        double px = minecraft.player.getX(), pz = minecraft.player.getZ();
        String own = player();
        if (!globe && inside(mx, my)) {
            double perPixel = blocks / side();
            double cx = x + (mx - (left + mapWidth * .5)) * perPixel, cz = z + (my - (top + mapHeight * .5)) * perPixel;
            // Off the square there is no world under the pointer.
            if (square() && (cx < data.minX() || cz < data.minZ() || cx >= data.minX() + data.span() || cz >= data.minZ() + data.span()))
                return own;
            px = cx; pz = cz;
            if (data.wrapped()) { px = AtlasMapMath.canonical(px, data.minX(), data.span()); pz = AtlasMapMath.canonical(pz, data.minZ(), data.span()); }
            return "X " + (long) Math.floor(px) + " · Z " + (long) Math.floor(pz);
        }
        return own;
    }
    /** The player's position as the world numbers it: the client keeps counting past the seam, the label does not. */
    private String player() {
        int[] at = OrbisMinimap.shownBlock(data, minecraft.player.getX(), minecraft.player.getZ());
        return "X " + at[0] + " · Y " + (long) Math.floor(minecraft.player.getY()) + " · Z " + at[1];
    }
    private boolean inside(double mx, double my) { return mx >= left && mx < left + mapWidth && my >= top && my < top + mapHeight; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        // The controls lie on the map: they get the click first.
        if (super.mouseClicked(event, twice)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y())) {
            if (settingsOpen) showSettings(false);
            dragging = true; setDragging(true); return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false; setDragging(false); return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT && data != null) { follow = false;
            if (globe) turnGlobe(event.x() - dx, event.y() - dy, event.x(), event.y());
            else {
                // The grabbed spot stays under the pointer: one screen pixel is blocks / side blocks.
                x -= dx * blocks / side(); z -= dy * blocks / side();
                keep();
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    /**
     * Turns the globe so the spot grabbed at {@code (fromX, fromY)} follows the pointer to {@code (toX, toY)}. The
     * globe has no poles: it turns freely and without end in every direction.
     */
    private void turnGlobe(double fromX, double fromY, double toX, double toY) {
        double radius = Math.max(8, side() * .5 - 4) * globeZoom, cx = left + mapWidth * .5, cy = top + mapHeight * .5;
        double[] from = chart((fromX - cx) / radius, (cy - fromY) / radius), to = chart((toX - cx) / radius, (cy - toY) / radius);
        yaw = wrap(yaw + 2 * Math.PI * (to[0] - from[0]), 2 * Math.PI);
        pitch = wrap(pitch + Math.PI * (to[1] - from[1]), Math.PI);
    }
    /**
     * World offset (in world widths) of a point on the globe from its centre, as the raster's inverse chart has it.
     * Near and past the rim, where that chart runs away, the centre's own scale continues instead.
     */
    static double[] chart(double x, double y) {
        double distance = Math.hypot(x, y), rim = .9;
        double scale = distance <= rim ? (distance == 0 ? 1 / (2 * Math.PI) : Math.asin(distance) / (2 * Math.PI * distance))
                : (Math.asin(rim) / (2 * Math.PI) + (distance - rim) / (2 * Math.PI * Math.sqrt(1 - rim * rim))) / distance;
        return new double[] {x * scale, -y * scale};
    }
    private static double wrap(double angle, double period) { return angle - Math.floor(angle / period) * period; }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (inside(x,y) && dy != 0) { zoom(Math.pow(1.25, Math.clamp(dy,-4,4))); return true; }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        zoomKeyHandled = false;
        if (PlayerAtlasClient.mapKey.matches(event)) { onClose(); return true; }
        // Layout keycodes for the main row (QWERTZ has '+' and '-' elsewhere), physical keys for the numpad.
        if (event.keycode() == '+' || event.keycode() == '=' || event.key() == InputConstants.KEY_ADD) {
            zoom(1.25); zoomKeyHandled = true; return true;
        }
        if (event.keycode() == '-' || event.key() == KEY_SUBTRACT) { zoom(1/1.25); zoomKeyHandled = true; return true; }
        if (event.key() == InputConstants.KEY_HOME) { follow = true; return true; }
        return super.keyPressed(event);
    }
    /** Layouts that need a modifier for '+' or '-' only report the character; the matching key press zooms once. */
    @Override public boolean charTyped(CharacterEvent event) {
        int c = event.codepoint();
        if (c == '+' || c == '=' || c == '-') {
            if (!zoomKeyHandled) zoom(c == '-' ? 1/1.25 : 1.25);
            zoomKeyHandled = false;
            return true;
        }
        return super.charTyped(event);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {}
    @Override public void removed() { saveZoom(); super.removed(); }
    /** The world changed or was left: the kept picture belongs to it. */
    static void forget() { texture.close(); }
}
