package dev.orbis.debug;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import com.mojang.blaze3d.platform.InputConstants;

/** Read-only map navigation: no teleport, commands, chunk tickets or save edits. */
final class OrbisDebugScreen extends Screen {
    private static final int TEXT = 0xffedf3fa, MUTED = 0xffafbecf, ACCENT = 0xff70dfbd;
    private DebugSnapshot data;
    private String dimension;
    private int mapRight, mapBottom, panelX, page, tab;
    private double centerX, centerZ, zoom = 1, yaw, pitch;
    private boolean globe, dragging, initialized;
    private boolean showChunks, showPlaces;
    private double globeZoom = 1;
    private Button scanButton, viewButton, filterButton, sortButton, previousButton, nextButton;
    private final DebugMapTexture mapTexture = new DebugMapTexture();
    private BiomeOverview.Filter filter = BiomeOverview.Filter.ALL;
    private BiomeOverview.Sort sort = BiomeOverview.Sort.NAME;
    private BiomeOverview.Counts coverage = new BiomeOverview.Counts(0, 0, 0, 0);
    private final List<Button> rows = new ArrayList<>();
    private List<String> rowValues = List.of(), rowTips = List.of();
    private List<Integer> rowColors = List.of();
    private DebugSnapshot.Place selected;
    private List<String> rowLabels = List.of();
    private List<DebugSnapshot.Place> rowPlaces = List.of();

    OrbisDebugScreen() { super(Component.literal("Orbis debug atlas")); }

    @Override protected void init() {
        panelX = width - Math.clamp(width / 3, 176, 204) - 12;
        mapRight = panelX - 12;
        mapBottom = height - 44;
        int panelWidth = width - panelX - 12;
        rows.clear();
        button(8, 32, 46, "Flat", b -> globe = false, () -> !globe);
        viewButton = button(58, 32, 46, "Globe", b -> globe = true, () -> globe);
        button(112, 32, 22, "-", b -> zoom(1 / 1.5));
        button(138, 32, 22, "+", b -> zoom(1.5));
        button(8, height - 24, 44, "Player", b -> focusPlayer());
        button(56, height - 24, 42, "World", b -> resetView());
        button(106, height - 24, 48, "Chunks", b -> showChunks = !showChunks, () -> showChunks);
        button(158, height - 24, 48, "Places", b -> showPlaces = !showPlaces, () -> showPlaces);
        int third = (panelWidth - 8) / 3;
        button(panelX, 32, third, "Biomes", b -> changeTab(0), () -> tab == 0);
        button(panelX + third + 4, 32, third, "Places", b -> changeTab(1), () -> tab == 1);
        button(panelX + (third + 4) * 2, 32, third, "Stats", b -> changeTab(2), () -> tab == 2);
        scanButton = button(width - 96, height - 24, 84, "Sample", b -> {
            if (data != null) {
                if (tab == 1) DebugSurvey.audit(!data.structuresScanning());
                else DebugSurvey.scan(!data.scanning());
            }
        });
        scanButton.setTooltip(Tooltip.create(Component.literal("Check this world without loading terrain.")));
        int half = (panelWidth - 4) / 2;
        filterButton = button(panelX, 92, half, "All", b -> {
            filter = BiomeOverview.Filter.values()[(filter.ordinal() + 1) % BiomeOverview.Filter.values().length];
            page = 0; updateRows();
        });
        filterButton.setTooltip(Tooltip.create(Component.literal("Filter the checklist.")));
        sortButton = button(panelX + half + 4, 92, panelWidth - half - 4, "Name", b -> {
            sort = sort == BiomeOverview.Sort.NAME ? BiomeOverview.Sort.HITS : BiomeOverview.Sort.NAME;
            page = 0; updateRows();
        });
        sortButton.setTooltip(Tooltip.create(Component.literal("Sort by name or sample hits.")));
        int rowHeight = tab == 0 ? 16 : 23;
        for (int y = tab != 2 ? 130 : 114; y + rowHeight <= height - 30; y += rowHeight) {
            final int row = rows.size();
            rows.add(addRenderableWidget(new AtlasButton(panelX, y, panelWidth, rowHeight - 1, "", b -> {
                int index = page * rows.size() + row;
                if (index < rowPlaces.size() && rowPlaces.get(index) != null) focus(rowPlaces.get(index));
            }, () -> {
                int index = page * rows.size() + row;
                return selected != null && index < rowPlaces.size() && selected.equals(rowPlaces.get(index));
            }, true)));
        }
        previousButton = button(panelX, height - 24, 18, "<", b -> movePage(-1));
        nextButton = button(panelX + 20, height - 24, 18, ">", b -> movePage(1));
        button(width - 53, 6, 41, "Close", b -> onClose());
        if (minecraft.level != null && minecraft.player != null) {
            String current = minecraft.level.dimension().identifier().toString();
            if (!current.equals(dimension)) {
                dimension = current;
                data = null;
                initialized = false;
                DebugSurvey.open(minecraft.level.dimension(), minecraft.player.getUUID());
            }
        }
        updateRows();
    }

    private Button button(int x, int y, int w, String label, Button.OnPress action) {
        return button(x, y, w, label, action, () -> false);
    }
    private Button button(int x, int y, int w, String label, Button.OnPress action, java.util.function.BooleanSupplier selected) {
        return addRenderableWidget(new AtlasButton(x, y, w, 18, label, action, selected, false));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { DebugSurvey.close(); mapTexture.close(); }
    @Override public void tick() {
        if (minecraft.level == null || minecraft.player == null) { onClose(); return; }
        if (!minecraft.level.dimension().identifier().toString().equals(dimension)) {
            dimension = null;
            selected = null;
            page = 0;
            rebuildWidgets();
        }
        DebugSnapshot latest = DebugSurvey.snapshot;
        if (latest != null && latest != data && latest.dimension().equals(dimension)) {
            data = latest;
            if (!initialized) { resetView(); initialized = true; }
            updateRows();
        }
    }
    private void changeTab(int value) { tab = value; page = 0; rebuildWidgets(); }
    private void movePage(int step) {
        page = Math.clamp(page + step, 0, Math.max(0, (rowLabels.size() - 1) / Math.max(1, rows.size())));
        updateRows();
    }
    private void updateRows() {
        List<String> labels = new ArrayList<>(), values = new ArrayList<>(), tips = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<DebugSnapshot.Place> places = new ArrayList<>();
        if (data != null) {
            coverage = BiomeOverview.counts(data.biomes());
            scanButton.active = data.wrapped() && data.error().isEmpty();
            scanButton.setMessage(Component.literal(tab == 1 ? data.structuresScanning() ? "Pause check" : "Check places"
                    : data.scanning() ? "Pause" : data.surveyDone() == data.surveyTotal()
                    ? "Resample" : data.surveyDone() > 0 ? "Resume" : "Sample"));
            viewButton.active = data.wrapped();
            if (!data.wrapped()) globe = false;
            if (tab == 0) for (var biome : BiomeOverview.select(data.biomes(), filter, sort)) {
                labels.add(displayName(biome.id()));
                values.add(String.format(Locale.ROOT, "%,d", biome.sampleHits()));
                String status = biome.observed() ? "Observed in a loaded chunk" : biome.found() ? "Found by generator sampling" : "Not found yet; existence unconfirmed";
                tips.add(biome.id() + "\n" + status + "\n" + String.format(Locale.ROOT, "%,d sample queries", biome.sampleHits())
                        + " at four heights. Not a chunk count or area measurement.");
                colors.add(biome.observed() ? ACCENT : biome.found() ? 0xff8dbffa : MUTED);
                places.add(biome.location());
            }
            if (tab == 1) for (var entry : data.structures()) {
                if (filter == BiomeOverview.Filter.FOUND && !entry.found()) continue;
                if (filter == BiomeOverview.Filter.UNCONFIRMED && entry.found()) continue;
                labels.add(displayName(entry.id()));
                values.add(entry.status());
                tips.add(entry.status());
                colors.add(entry.observed() ? ACCENT : entry.found() ? 0xff8dbffa : MUTED);
                places.add(entry.location());
            }
            if (tab == 2) for (String[] entry : stats()) {
                labels.add(entry[0]); values.add(entry[1]); tips.add(entry[0] + ": " + entry[1]);
                places.add(null); colors.add(MUTED);
            }
        } else scanButton.active = false;
        rowLabels = labels; rowValues = values; rowTips = tips; rowColors = colors; rowPlaces = places;
        page = Math.clamp(page, 0, Math.max(0, (labels.size() - 1) / Math.max(1, rows.size())));
        filterButton.visible = tab != 2;
        sortButton.visible = tab == 0;
        filterButton.setMessage(Component.literal(switch (filter) {
            case ALL -> "Show: all"; case UNCONFIRMED -> "Not found"; case FOUND -> "Found";
        }));
        sortButton.setMessage(Component.literal(sort == BiomeOverview.Sort.NAME ? "Name A-Z" : "Samples"));
        previousButton.active = page > 0;
        nextButton.active = (page + 1) * rows.size() < labels.size();
        for (int i = 0; i < rows.size(); i++) {
            int index = page * rows.size() + i;
            Button row = rows.get(i);
            // Only locations are interactive. Unknown biomes and statistics are plain text.
            row.visible = index < labels.size() && places.get(index) != null;
            if (row.visible) {
                var place = places.get(index);
                row.setMessage(Component.literal(tips.get(index) + ". Center map at " + place.x() + ", " + place.y() + ", " + place.z()));
            }
        }
    }
    private static String displayName(String id) {
        String value = id.replace("minecraft:", "").replace("orbis:", "").replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    private void resetView() {
        if (data == null) return;
        centerX = data.minX() + data.span() / 2.0;
        centerZ = data.minZ() + data.span() / 2.0;
        zoom = 1;
        globeZoom = 1;
        yaw = pitch = 0;
    }
    private void focusPlayer() {
        if (data != null) focus(new DebugSnapshot.Place("Player", data.playerX(), data.playerY(), data.playerZ()));
    }
    private void focus(DebugSnapshot.Place place) {
        if (data == null) return;
        selected = place;
        centerX = place.x(); centerZ = place.z();
        yaw = -(unitX(place.x()) - .5) * Math.PI * 2;
        pitch = (.5 - unitZ(place.z())) * Math.PI;
        if (!globe) zoom = Math.max(zoom, Math.min(4096, data.span() / 512.0));
    }
    private void zoom(double factor) {
        if (globe) globeZoom = Math.clamp(globeZoom * factor, .5, 12);
        else zoom = Math.clamp(zoom * factor, .25, 65536);
    }
    private double scale() { return Math.max(16, Math.min(mapRight - 8, mapBottom - 58)) * zoom / (data == null ? 4096 : data.span()); }
    private double midX() { return (8 + mapRight) / 2.0; }
    private double midY() { return (58 + mapBottom) / 2.0; }
    private double radius() { return Math.max(8, Math.min(mapRight - 8, mapBottom - 58) / 2.0 - 4) * globeZoom; }
    private double unitX(double x) { return (x - data.minX()) / data.span(); }
    private double unitZ(double z) { return (z - data.minZ()) / data.span(); }
    private int sx(double x) { return (int) Math.clamp(midX() + (x - centerX) * scale(), -1000000, 1000000); }
    private int sz(double z) { return (int) Math.clamp(midY() + (z - centerZ) * scale(), -1000000, 1000000); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xff101820);
        g.fill(panelX - 8, 28, width, height, 0xff16222d);
        g.fill(panelX - 9, 28, panelX - 8, height, 0xff2a3a48);
        g.text(font, "ORBIS", 10, 11, ACCENT, false);
        g.text(font, "Debug atlas", 48, 11, TEXT, false);
        if (data != null && width > 460) g.text(font, data.preset() + " / " + displayName(data.dimension()), 130, 11, MUTED, false);
        g.fill(8, 58, mapRight, mapBottom, DebugMapRaster.BACKGROUND);
        if (data == null) {
            g.textWithWordWrap(font, Component.literal("Waiting for the integrated server..."), 14, 64, Math.max(40, mapRight - 24), MUTED);
        } else {
            g.enableScissor(8, 58, mapRight, mapBottom);
            mapTexture.draw(g, data, new DebugMapRaster.View(mapRight - 8, mapBottom - 58, centerX, centerZ,
                    globe ? globeZoom : scale(), globe, yaw, pitch, showChunks, showPlaces), 8, 58);
            marker(g, data.playerX(), data.playerZ(), "P", 0xffffffff);
            if (selected != null) marker(g, selected.x(), selected.z(), "X", ACCENT);
            if (selected != null) {
                g.fill(8, mapBottom - 25, mapRight, mapBottom, 0xe0101923);
                g.text(font, ellipsis(displayName(selected.label()), mapRight - 20), 12, mapBottom - 22, ACCENT, false);
                g.text(font, "X " + selected.x() + "  Y " + selected.y() + "  Z " + selected.z(), 12, mapBottom - 11, TEXT, false);
            } else if (data.surveyDone() == 0) {
                g.text(font, "Sample world to reveal biome coverage", 14, 64, MUTED, false);
            }
            g.disableScissor();
            int panelWidth = width - panelX - 12;
            if (tab == 1) {
                long found = data.structures().stream().filter(StructureAudit.Entry::found).count();
                g.text(font, found + " / " + data.structures().size() + " place types found", panelX, 58, ACCENT, false);
                g.text(font, ellipsis(data.structureState(), panelWidth), panelX, 71, MUTED, false);
                g.text(font, "Current dimension / generator", panelX, 118, MUTED, false);
                g.fill(panelX, 84, width - 12, 86, 0xff304251);
                if (!data.structures().isEmpty()) g.fill(panelX, 84, panelX + (int) (panelWidth * found / data.structures().size()), 86, ACCENT);
            } else {
                g.text(font, coverage.found() + " / " + coverage.total() + " biomes found", panelX, 58, ACCENT, false);
                String state = !data.error().isEmpty() ? "error" : data.surveyDone() == data.surveyTotal() ? "done" : data.scanning() ? "sampling" : data.surveyDone() > 0 ? "paused" : "ready";
                g.text(font, coverage.unknown() + " unconfirmed", panelX, 71, MUTED, false);
                String progress = data.surveyDone() * 100 / data.surveyTotal() + "% " + state;
                g.text(font, progress, width - 12 - font.width(progress), 71, MUTED, false);
                g.fill(panelX, 84, width - 12, 86, 0xff304251);
                g.fill(panelX, 84, panelX + (int) ((long) panelWidth * data.surveyDone() / data.surveyTotal()), 86, ACCENT);
            }
            if (tab == 2) g.text(font, "World / " + data.preset(), panelX, 96, TEXT, false);
            if (tab == 0) g.text(font, "Biome", panelX + 4, 118, MUTED, false);
            if (tab == 0) {
                g.text(font, "Samples", width - 16 - font.width("Samples"), 118, MUTED, false);
                if (mx >= panelX && my >= 114 && my < 129) tip(g, "Samples = biome queries, not chunks.", mx, my);
            }
            g.fill(panelX, tab != 2 ? 128 : 111, width - 12, tab != 2 ? 129 : 112, 0xff304251);
            if (rowLabels.isEmpty()) g.textWithWordWrap(font, Component.literal(tab == 0 ? "No biomes match this filter." : "No entries match this filter."), panelX + 4, tab != 2 ? 136 : 119, panelWidth - 8, MUTED);
            String pagination = (page + 1) + " / " + Math.max(1, (rowLabels.size() + Math.max(1, rows.size()) - 1) / Math.max(1, rows.size()));
            g.text(font, pagination, panelX + 44, height - 19, MUTED, false);
            if (!globe && inside(mx, my)) {
                int x = (int) Math.floor(centerX + (mx - midX()) / scale());
                int z = (int) Math.floor(centerZ + (my - midY()) / scale());
                String coordinate = "X " + x + "   Z " + z;
                if (data.loaded().contains(ChunkPos.pack(x >> 4, z >> 4))) coordinate += "   loaded";
                g.text(font, ellipsis(coordinate, mapRight - 12), 8, mapBottom + 5, MUTED, false);
            } else g.text(font, ellipsis(globe ? "Drag to rotate / scroll to zoom" : "Drag to pan / scroll to zoom", mapRight - 12), 8, mapBottom + 5, MUTED, false);
        }
        super.extractRenderState(g, mx, my, delta);
        // Text is rendered independently of navigation widgets, including all read-only entries.
        for (int i = 0; i < rows.size(); i++) {
            int index = page * rows.size() + i;
            if (index >= rowLabels.size()) break;
            int y = rows.get(i).getY();
            if (tab == 0) {
                if (rowPlaces.get(index) == null) g.fill(panelX + 4, y + 6, panelX + 8, y + 7, MUTED);
                else if (rowColors.get(index) == ACCENT) g.fill(panelX + 4, y + 4, panelX + 8, y + 8, ACCENT);
                else g.outline(panelX + 4, y + 4, 4, 4, rowColors.get(index));
                int valueX = width - 16 - font.width(rowValues.get(index));
                g.text(font, ellipsis(rowLabels.get(index), valueX - panelX - 20), panelX + 12, y + 3, TEXT, false);
                g.text(font, rowValues.get(index), valueX, y + 3, MUTED, false);
            } else {
                g.text(font, ellipsis(rowLabels.get(index), width - panelX - 22), panelX + 4, y + 2, tab == 2 ? MUTED : TEXT, false);
                g.text(font, ellipsis(rowValues.get(index), width - panelX - 22), panelX + 4, y + 12, tab == 2 ? TEXT : MUTED, false);
            }
            if (mx >= panelX && mx < width - 12 && my >= y && my < y + rows.get(i).getHeight()) {
                var place = rowPlaces.get(index);
                if (place != null) tip(g, "Select to locate " + rowLabels.get(index), mx, my);
            }
        }
    }
    private void tip(GuiGraphicsExtractor g, String message, int x, int y) {
        g.setTooltipForNextFrame(font, font.split(Component.literal(message), Math.min(180, Math.max(80, width / 3))), x, y);
    }
    private String ellipsis(String value, int available) {
        return font.width(value) <= available ? value : font.plainSubstrByWidth(value, Math.max(0, available - font.width("..."))) + "...";
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    private void marker(GuiGraphicsExtractor g, double x, double z, String label, int color) {
        int px, py;
        if (globe) {
            double[] xyz = DebugMapMath.globe(unitX(x), unitZ(z), yaw, pitch);
            if (xyz[2] <= 0) return;
            px = (int) (midX() + xyz[0] * radius()); py = (int) (midY() - xyz[1] * radius());
        } else { px = sx(x); py = sz(z); }
        if (px >= 8 && px < mapRight && py >= 58 && py < mapBottom) g.text(font, label, px - 2, py - 4, color);
    }
    private List<String[]> stats() {
        return List.of(new String[]{"Preset (matched by width)", data.preset()},
                new String[]{"Dimension", data.dimension()}, new String[]{"Seed", Long.toString(data.seed())},
                new String[]{"World width", data.wrapped() ? String.format(Locale.ROOT, "%,d blocks / %,d chunks", data.span(), data.span() / 16) : "Unwrapped"},
                new String[]{"Loaded / mapped chunks", data.loaded().size() + " / " + data.chunks().size()},
                new String[]{"Observed / sampled biomes", coverage.observed() + " / " + coverage.sampled()},
                new String[]{"Server tick", String.format(Locale.ROOT, "%.1f ms", data.tickMillis())},
                new String[]{"Map refresh", String.format(Locale.ROOT, "%.2f ms", mapTexture.updateMillis())},
                new String[]{"Map texture", mapTexture.resolution()}, new String[]{"Texture uploads", Integer.toString(mapTexture.uploads())},
                new String[]{"Sampling", DebugSurvey.GRID + " x " + DebugSurvey.GRID + " / four heights"},
                new String[]{"Structure positions checked", String.format(Locale.ROOT, "%,d", data.structureChecks())},
                new String[]{"Structure check", data.structureState()},
                new String[]{"Queries completed", String.format(Locale.ROOT, "%,d / %,d", data.surveyDone(), data.surveyTotal())},
                new String[]{"History", data.historyLimited() ? "Capped; old entries evicted" : "Current session"},
                new String[]{"Survey status", data.error().isEmpty() ? "OK / world keeps running" : data.error()});
    }
    private boolean inside(double x, double y) { return x >= 8 && x < mapRight && y >= 58 && y < mapBottom; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y())) {
            dragging = true;
            setDragging(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false; setDragging(false); return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT && data != null) {
            if (globe) { yaw += dx * .015; pitch = Math.clamp(pitch + dy * .015, -Math.PI / 2, Math.PI / 2); }
            else { centerX -= dx / scale(); centerZ -= dy / scale(); }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (inside(x, y) && dy != 0) {
            double before = scale();
            zoom(Math.pow(1.25, Math.clamp(dy, -4, 4)));
            if (!globe) {
                centerX += (x - midX()) * (1 / before - 1 / scale());
                centerZ += (y - midY()) * (1 / before - 1 / scale());
            }
            return true;
        }
        if (x >= panelX && dy != 0) { movePage(dy > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (OrbisDebugClient.mapKey.matches(event)) { onClose(); return true; }
        switch (event.key()) {
            case InputConstants.KEY_W -> move(0, -1);
            case InputConstants.KEY_S -> move(0, 1);
            case InputConstants.KEY_A -> move(-1, 0);
            case InputConstants.KEY_D -> move(1, 0);
            case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> zoom(1.5);
            case InputConstants.KEY_MINUS -> zoom(1 / 1.5);
            case InputConstants.KEY_PAGEUP -> movePage(-1);
            case InputConstants.KEY_PAGEDOWN -> movePage(1);
            case InputConstants.KEY_HOME -> focusPlayer();
            default -> { return super.keyPressed(event); }
        }
        return true;
    }
    private void move(int dx, int dz) {
        if (globe) { yaw += dx * .15; pitch = Math.clamp(pitch + dz * .15, -Math.PI / 2, Math.PI / 2); }
        else { centerX += dx * 24 / scale(); centerZ += dz * 24 / scale(); }
    }
    @Override public Component getNarrationMessage() {
        if (tab == 1 && data != null) return Component.literal("Orbis places. " + data.structureState()
                + ". Current dimension's structure types and End portal room layouts. Predicted locations use the current generator; saved terrain may differ. "
                + "Check places to search, or pause and resume. Unconfirmed is not absent. Select a found place to center it.");
        return Component.literal("Orbis debug atlas. " + coverage.found() + " of " + coverage.total() + " dimension biomes found. "
                + coverage.unknown() + " unconfirmed. W A S D move the map. Plus and minus zoom. Home centers the player. "
                + "Page up and page down browse the list. Tab selects controls. Escape closes. "
                + "Biome symbols: solid square observed, outline square sampled, dash unconfirmed. The world continues running.");
    }
}
