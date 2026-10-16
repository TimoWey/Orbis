package dev.orbis.atlas;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Quiet player interface: a centred map and one row of settings on each side. */
final class PlayerAtlasScreen extends Screen {
    // Kept between openings, so the map is there at once when the screen opens again.
    private static final AtlasMapTexture texture = new AtlasMapTexture();
    private AtlasSnapshot data;
    private Button globeButton;
    private String dimension;
    private int left, top, size;
    private boolean globe, follow = true, dragging;
    private double x, z, yaw, pitch, blocks = 512, globeZoom = 1;
    PlayerAtlasScreen() { super(Component.literal("Orbis Atlas")); }
    private String tr(String key) { return Component.translatable("orbis.player." + key).getString(); }
    @Override protected void init() {
        size = Math.max(64, Math.min(width - 32, height - 112));
        left = (width - size) / 2; top = (height - size) / 2;
        int w = Math.min(100, (width - 32) / 4), start = (width - (w * 4 + 12)) / 2;
        button(start, top - 26, w, tr("flat"), b -> mode(false), () -> !globe);
        globeButton = button(start + w + 4, top - 26, w, tr("globe"), b -> mode(true), () -> globe);
        button(start + (w + 4) * 2, top - 26, w, tr("center"), b -> follow = true);
        button(start + (w + 4) * 3, top - 26, w, tr("close"), b -> onClose());
        button(start, top + size + 8, w, visibleLabel(), b -> { AtlasPreferences.toggleVisible(); b.setMessage(Component.literal(visibleLabel())); });
        button(start + w + 4, top + size + 8, w, cornerLabel(), b -> { AtlasPreferences.cycleCorner(); b.setMessage(Component.literal(cornerLabel())); });
        button(start + (w + 4) * 2, top + size + 8, w, sizeLabel(), b -> { AtlasPreferences.cycleSize(); b.setMessage(Component.literal(sizeLabel())); });
        button(start + (w + 4) * 3, top + size + 8, w, miniZoomLabel(), b -> { AtlasPreferences.cycleMinimapZoom(); b.setMessage(Component.literal(miniZoomLabel())); });
    }
    private String visibleLabel() { return tr(AtlasPreferences.visible() ? "minimap_on" : "minimap_off"); }
    private String cornerLabel() { return tr("corner_" + AtlasPreferences.corner()); }
    private String sizeLabel() { return tr("size") + ": " + new String[]{"S", "M", "L"}[AtlasPreferences.sizeIndex()]; }
    private String miniZoomLabel() { return "Mini: " + AtlasPreferences.minimapZoom() + "×"; }
    private Button button(int x, int y, int w, String label, Button.OnPress action) {
        return button(x, y, w, label, action, () -> false);
    }
    private Button button(int x, int y, int w, String label, Button.OnPress action, java.util.function.BooleanSupplier selected) {
        return addRenderableWidget(new AtlasButton(x, y, w, 18, label, action, selected, false));
    }
    @Override public void tick() {
        if (minecraft.level == null || minecraft.player == null) { onClose(); return; }
        data = PlayerAtlasClient.snapshot;
        globeButton.active = data != null && data.wrapped();
        if (data != null && !data.dimension().equals(dimension)) {
            dimension = data.dimension(); globe = AtlasPreferences.globe(dimension) && data.wrapped();
            blocks = AtlasPreferences.zoom(dimension, false); globeZoom = AtlasPreferences.zoom(dimension, true); follow = true;
        }
    }
    private void mode(boolean value) {
        if (data == null || value && !data.wrapped()) return;
        globe = value; follow = true; AtlasPreferences.setGlobe(dimension, globe);
    }
    private void zoom(double factor) {
        if (globe) globeZoom = Math.clamp(globeZoom * factor, .5, 12);
        else blocks = Math.clamp(blocks / factor, 32, data != null && data.wrapped() ? data.span() : 30000000);
        AtlasPreferences.zoom(dimension, globe, globe ? globeZoom : blocks);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xd9182028);
        g.text(font, "ORBIS · " + tr("atlas"), (width - font.width("ORBIS · " + tr("atlas"))) / 2, top - 42, 0xff70dfbd, false);
        if (data != null && minecraft.player != null) {
            if (follow) {
                x = minecraft.player.getX(); z = minecraft.player.getZ();
                if (data.wrapped()) { x = AtlasMapMath.canonical(x, data.minX(), data.span()); z = AtlasMapMath.canonical(z, data.minZ(), data.span()); }
                yaw = -((x - data.minX()) / data.span() - .5) * Math.PI * 2;
                pitch = (.5 - (z - data.minZ()) / data.span()) * Math.PI;
            }
            var view = new AtlasMapRaster.View(size, size, x, z, globe ? globeZoom : size / blocks, globe, yaw, pitch, false, false);
            g.enableScissor(left, top, left + size, top + size);
            texture.draw(g, data, view, left, top);
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
        }
        String hint = AtlasPreferences.status().isEmpty() ? tr("hint") : AtlasPreferences.status();
        g.text(font, hint, (width - font.width(hint)) / 2, top + size + 32, 0xffafbecf, false);
        super.extractRenderState(g, mx, my, delta);
    }
    private boolean inside(double mx, double my) { return mx >= left && mx < left + size && my >= top && my < top + size; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y())) {
            dragging = true; setDragging(true); return true;
        }
        return super.mouseClicked(event, twice);
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
                // The grabbed spot stays under the pointer: one screen pixel is blocks / size blocks.
                x -= dx * blocks / size; z -= dy * blocks / size;
                if (data.wrapped()) { x = AtlasMapMath.canonical(x, data.minX(), data.span()); z = AtlasMapMath.canonical(z, data.minZ(), data.span()); }
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
        double radius = Math.max(8, size * .5 - 4) * globeZoom, cx = left + size * .5, cy = top + size * .5;
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
        if (PlayerAtlasClient.mapKey.matches(event)) { onClose(); return true; }
        if (event.key() == InputConstants.KEY_EQUALS || event.key() == InputConstants.KEY_ADD) { zoom(1.25); return true; }
        if (event.key() == InputConstants.KEY_MINUS) { zoom(1/1.25); return true; }
        if (event.key() == InputConstants.KEY_HOME) { follow = true; return true; }
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {}
    /** The world changed or was left: the kept picture belongs to it. */
    static void forget() { texture.close(); }
}
