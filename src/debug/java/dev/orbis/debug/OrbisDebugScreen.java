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

/** Client atlas window: navigation and local waypoints; the world keeps running. */
final class OrbisDebugScreen extends Screen {
    private static final int TEXT = 0xffedf3fa, MUTED = 0xffafbecf, ACCENT = 0xff70dfbd;
    private DebugSnapshot data;
    private String dimension;
    private int mapRight, mapBottom, panelX, page, tab;
    private int left, top, right, bottom, mapLeft, mapTop;
    private final AtlasWaypoints waypoints = AtlasWaypoints.SHARED;
    private double centerX, centerZ, zoom = 1, yaw, pitch;
    private boolean globe, dragging, initialized;
    private boolean showChunks, showPlaces;
    private double globeZoom = 1;
    private Button scanButton, detailButton, resetButton, viewButton, filterButton, sortButton, previousButton, nextButton;
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

    OrbisDebugScreen() { super(Component.literal("Orbis atlas")); }

    @Override protected void init() {
        AtlasLayout layout = AtlasLayout.fit(width, height);
        left = layout.left(); top = layout.top(); right = layout.right(); bottom = layout.bottom(); panelX = layout.panelX();
        mapLeft = layout.mapLeft(); mapTop = layout.mapTop(); mapRight = layout.mapRight(); mapBottom = layout.mapBottom();
        int panelWidth = right - panelX - 8;
        rows.clear();
        int controlWidth = layout.controlWidth();
        button(layout.controlX(0), layout.controlTop(0), controlWidth, "Flat", b -> setGlobe(false), () -> !globe);
        viewButton = button(layout.controlX(1), layout.controlTop(1), controlWidth, "Globe", b -> setGlobe(true), () -> globe);
        button(layout.controlX(2), layout.controlTop(2), controlWidth, "-", b -> zoom(1 / 1.5));
        button(layout.controlX(3), layout.controlTop(3), controlWidth, "+", b -> zoom(1.5));
        button(layout.controlX(0), layout.controlBottom(0), controlWidth, "You", b -> focusPlayer());
        button(layout.controlX(1), layout.controlBottom(1), controlWidth, "All", b -> resetView());
        button(layout.controlX(2), layout.controlBottom(2), controlWidth, "Grid", b -> { showChunks = !showChunks; showPlaces = showChunks; }, () -> showChunks);
        var cornerButton = button(layout.controlX(3), layout.controlBottom(3), controlWidth, cornerLabel(), b -> {
            AtlasPreferences.cycleCorner(); b.setMessage(Component.literal(cornerLabel()));
        });
        cornerButton.setTooltip(Tooltip.create(Component.literal("Minimap corner: TR, TL, BR, BL. Click to move.")));
        button(panelX, top + 28, 44, "Biomes", b -> changeTab(0), () -> tab == 0);
        button(panelX + 48, top + 28, panelWidth - 90, Component.translatable("orbis.atlas.structures").getString(), b -> changeTab(1), () -> tab == 1);
        button(right - 46, top + 28, 38, "Stats", b -> changeTab(2), () -> tab == 2);
        var sizeButton = button(right - 119, top + 5, 66, sizeLabel(), b -> {
            AtlasPreferences.cycleSize(); b.setMessage(Component.literal(sizeLabel()));
        });
        sizeButton.setTooltip(Tooltip.create(Component.translatable("orbis.atlas.size_tip")));
        scanButton = button(right - 68, bottom - 22, 60, "Sample", b -> {
            if (data != null) {
                DebugSurvey.act(tab == 1 ? DebugSurvey.Action.STRUCTURES : DebugSurvey.Action.BIOMES);
            }
        });
        scanButton.setTooltip(Tooltip.create(Component.literal("Check this world without loading terrain.")));
        int half = (panelWidth - 4) / 2;
        detailButton = button(panelX, bottom - 44, half, "", b -> {
            if (data != null) DebugSurvey.act(tab == 1 ? DebugSurvey.Action.COUNT : DebugSurvey.Action.MAP);
        });
        resetButton = button(panelX + half + 4, bottom - 44, panelWidth - half - 4, "Resample", b -> {
            if (data != null) DebugSurvey.act(tab == 1 ? DebugSurvey.Action.RESET_STRUCTURES : DebugSurvey.Action.RESAMPLE);
        });
        filterButton = button(panelX, top + 86, half, "All", b -> {
            filter = BiomeOverview.Filter.values()[(filter.ordinal() + 1) % BiomeOverview.Filter.values().length];
            page = 0; updateRows();
        });
        filterButton.setTooltip(Tooltip.create(Component.literal("Filter the checklist.")));
        sortButton = button(panelX + half + 4, top + 86, panelWidth - half - 4, "Name", b -> {
            sort = sort == BiomeOverview.Sort.NAME ? BiomeOverview.Sort.HITS : BiomeOverview.Sort.NAME;
            page = 0; updateRows();
        });
        sortButton.setTooltip(Tooltip.create(Component.literal("Sort by name, sample hits or discovery status.")));
        int rowHeight = tab != 2 ? 16 : 23;
        for (int y = top + (tab != 2 ? 116 : 110); y + rowHeight <= bottom - 48; y += rowHeight) {
            final int row = rows.size();
            rows.add(addRenderableWidget(new AtlasButton(panelX, y, panelWidth, rowHeight - 1, "", b -> {
                int index = page * rows.size() + row;
                if (index < rowPlaces.size() && rowPlaces.get(index) != null) focus(rowPlaces.get(index));
            }, () -> {
                int index = page * rows.size() + row;
                return selected != null && index < rowPlaces.size() && selected.equals(rowPlaces.get(index));
            }, true)));
        }
        previousButton = button(panelX, bottom - 22, 18, "<", b -> movePage(-1));
        nextButton = button(panelX + 20, bottom - 22, 18, ">", b -> movePage(1));
        button(right - 49, top + 5, 41, "Close", b -> onClose());
        if (minecraft.level != null && minecraft.player != null) {
            String current = minecraft.level.dimension().identifier().toString();
            if (!current.equals(dimension)) {
                dimension = current;
                globe = AtlasPreferences.globe(dimension);
                waypoints.open(minecraft, dimension);
                data = null;
                initialized = false;
            }
        }
        updateRows();
    }

    private static String sizeLabel() { return Component.translatable("orbis.atlas.size").getString() + ": " + new String[]{"S", "M", "L"}[AtlasPreferences.sizeIndex()]; }
    private static String cornerLabel() { return new String[]{"TR", "TL", "BR", "BL"}[AtlasPreferences.corner()]; }
    private void setGlobe(boolean value) { globe = value; AtlasPreferences.setGlobe(dimension, value); }
    private Button button(int x, int y, int w, String label, Button.OnPress action) {
        return button(x, y, w, label, action, () -> false);
    }
    private Button button(int x, int y, int w, String label, Button.OnPress action, java.util.function.BooleanSupplier selected) {
        return addRenderableWidget(new AtlasButton(x, y, w, 18, label, action, selected, false));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { mapTexture.close(); }
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
            var work = data.search().progress();
            boolean allFound = data.search().allFound();
            scanButton.setMessage(Component.literal(tab == 1 ? data.structuresScanning() ? "Pause" : "Check"
                    : work.running() ? "Pause" : allFound ? "Found" : work.cursor() < work.limit() ? "Resume"
                    : work.limit() < AtlasSearch.MAX ? "Search+" : "Done"));
            if (tab != 1 && !work.running() && (allFound || work.cursor() == AtlasSearch.MAX)) scanButton.active = false;
            scanButton.setTooltip(Tooltip.create(Component.literal(tab == 1
                    ? "Check existence: up to 256 more candidate positions. Stop when all types, including portal rooms, are found. Counts remain lower bounds."
                    : "Quick biome check: at most 1,024 queries. Search+ adds up to 4,096 queries for missing types. Stops when all expected types are found.")));
            detailButton.active = data.wrapped() && data.error().isEmpty();
            detailButton.setMessage(Component.literal(tab == 1 ? data.structureCounting() && data.structuresScanning() ? "Counting" : "Count all"
                    : work.mapping() ? "Pause map" : work.mapCursor() < work.mapLimit() ? "Map: go"
                    : work.mapCursor() < AtlasSearch.MAP_MAX ? "Map+" : "Map done"));
            if (tab == 1 && data.structureCounting() && data.structuresScanning()
                    || tab != 1 && work.mapCursor() == AtlasSearch.MAP_MAX) detailButton.active = false;
            detailButton.setTooltip(Tooltip.create(Component.literal(tab == 1
                    ? "Explicit full count across this finite dimension. Can take a long time in large worlds. Pause with Pause; Count all resumes it. Completed counts restart."
                    : "Independent map preview: 256 coarse queries automatically. Map+ refines it with 65,536 surface queries. This does not require completing the biome checklist.")));
            resetButton.active = data.wrapped() && data.error().isEmpty();
            resetButton.setTooltip(Tooltip.create(Component.literal(tab == 1
                    ? "Reset predicted counts and candidate cursors, retain observations, then run a bounded existence check."
                    : "Restart the small biome search. Retains observations and map detail; does not start full map refinement.")));
            viewButton.active = data.wrapped();
            if (!data.wrapped() && globe) setGlobe(false);
            if (tab == 0) for (var biome : BiomeOverview.select(data.biomes(), filter, sort)) {
                labels.add(displayName(biome.id()));
                values.add(biome.observed() ? "O" : biome.found() ? "~" : "?");
                String status = biome.observed() ? "Observed in a loaded chunk" : biome.found() ? "Found by generator sampling" : "Not found yet; existence unconfirmed";
                tips.add(biome.id() + "\n" + status + "\n" + String.format(Locale.ROOT, "%,d sample queries", biome.sampleHits())
                        + " across biome search and map preview. Not a chunk count or area measurement.");
                colors.add(biome.observed() ? ACCENT : biome.found() ? 0xff8dbffa : MUTED);
                places.add(biome.location());
            }
            if (tab == 1) for (var entry : data.structures().stream().sorted(sort == BiomeOverview.Sort.NAME
                    ? Comparator.comparing(StructureAudit.Entry::id)
                    : Comparator.comparing(StructureAudit.Entry::found).reversed().thenComparing(StructureAudit.Entry::id)).toList()) {
                if (filter == BiomeOverview.Filter.FOUND && !entry.found()) continue;
                if (filter == BiomeOverview.Filter.UNCONFIRMED && entry.found()) continue;
                labels.add(entry.id().equals(StructureAudit.PORTAL) ? "Stronghold / portal room" : displayName(entry.id()));
                values.add(entry.countLabel() + " " + (entry.observed() ? "O" : entry.found() ? "~" : "?"));
                tips.add((entry.id().equals(StructureAudit.PORTAL) ? "Stronghold portal room: counts layouts, not active portals or proof of intact frame blocks.\n" : "") + entry.id() + "\n" + entry.status() + "\n" + entry.countDetails() + (entry.observed() ? "\nObserved in loaded terrain." : "\nGenerator prediction; saved terrain may differ. Not found does not prove absence."));
                colors.add(entry.observed() ? ACCENT : entry.found() ? 0xff8dbffa : MUTED);
                places.add(entry.location());
            }
            // Block-entity sightings supplement structures; they are not expected structure types.
            if (tab == 1 && filter != BiomeOverview.Filter.UNCONFIRMED) for (var place : data.places()) {
                if (!place.label().equals("End gateway (seen)") && !place.label().equals("End portal block (seen)")) continue;
                labels.add(place.label()); values.add("O"); colors.add(ACCENT); places.add(place);
                tips.add(place.label() + "\nAdditional portal observation from a loaded chunk. This is a block observation, not a generated structure type.\nX "
                        + place.x() + " Y " + place.y() + " Z " + place.z());
            }
            if (tab == 2) for (String[] entry : stats()) {
                labels.add(entry[0]); values.add(entry[1]); tips.add(entry[0] + ": " + entry[1]);
                places.add(null); colors.add(MUTED);
            }
        } else { scanButton.active = false; detailButton.active = false; resetButton.active = false; }
        rowLabels = labels; rowValues = values; rowTips = tips; rowColors = colors; rowPlaces = places;
        page = Math.clamp(page, 0, Math.max(0, (labels.size() - 1) / Math.max(1, rows.size())));
        filterButton.visible = tab != 2;
        sortButton.visible = tab != 2;
        filterButton.setMessage(Component.literal(switch (filter) {
            case ALL -> "Show: all"; case UNCONFIRMED -> "Unconfirmed"; case FOUND -> "Found";
        }));
        sortButton.setMessage(Component.literal(sort == BiomeOverview.Sort.NAME ? "Name A-Z" : tab == 1 ? "Found first" : "Samples"));
        previousButton.active = page > 0;
        nextButton.active = (page + 1) * rows.size() < labels.size();
        for (int i = 0; i < rows.size(); i++) {
            int index = page * rows.size() + i;
            Button row = rows.get(i);
            // Only locations are interactive. Unknown biomes and statistics are plain text.
            row.visible = index < labels.size();
            if (row.visible) {
                var place = places.get(index);
                row.setMessage(Component.literal(tips.get(index) + (place == null ? "" : ". Center map at " + place.x() + ", " + place.y() + ", " + place.z())));
                row.setTooltip(Tooltip.create(Component.literal(tips.get(index))));
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
        if (data != null && minecraft.player != null) focus(new DebugSnapshot.Place("Player", minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ()));
    }
    private void focus(DebugSnapshot.Place place) {
        if (data == null) return;
        selected = place;
        centerX = place.x(); centerZ = place.z();
        yaw = -(unitX(place.x()) - .5) * Math.PI * 2;
        pitch = (.5 - unitZ(place.z())) * Math.PI;
        if (data.wrapped()) {
            centerX = DebugMapMath.canonical(centerX, data.minX(), data.span());
            centerZ = DebugMapMath.canonical(centerZ, data.minZ(), data.span());
        }
        if (!globe) zoom = Math.max(zoom, data.span() / 512.0);
    }
    private void zoom(double factor) {
        if (globe) globeZoom = Math.clamp(globeZoom * factor, .5, 12);
        else zoom = Math.clamp(zoom * factor, 1, data == null ? 1 : Math.max(1, 8.0 * data.span() / Math.max(16, Math.min(mapRight - mapLeft, mapBottom - mapTop))));
        constrainView();
    }
    private void constrainView() {
        if (data == null || !data.wrapped() || globe) return;
        centerX = DebugMapMath.clampCenter(centerX, data.minX(), data.span(), (mapRight - mapLeft) / scale());
        centerZ = DebugMapMath.clampCenter(centerZ, data.minZ(), data.span(), (mapBottom - mapTop) / scale());
    }
    private double scale() { return Math.max(16, Math.min(mapRight - mapLeft, mapBottom - mapTop)) * zoom / (data == null ? 4096 : data.span()); }
    private double midX() { return (mapLeft + mapRight) / 2.0; }
    private double midY() { return (mapTop + mapBottom) / 2.0; }
    private double radius() { return Math.max(8, Math.min(mapRight - mapLeft, mapBottom - mapTop) / 2.0 - 4) * globeZoom; }
    private double unitX(double x) { return (x - data.minX()) / data.span(); }
    private double unitZ(double z) { return (z - data.minZ()) / data.span(); }
    private int sx(double x) { return (int) Math.clamp(midX() + (x - centerX) * scale(), -1000000, 1000000); }
    private int sz(double z) { return (int) Math.clamp(midY() + (z - centerZ) * scale(), -1000000, 1000000); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        constrainView();
        g.fill(left, top, right, bottom, 0xe0282828);
        g.outline(left, top, right - left, bottom - top, 0xff777777);
        g.fill(panelX - 8, top + 25, right - 1, bottom - 1, 0x80333333);
        g.text(font, "ORBIS", mapLeft, top + 10, ACCENT, false);
        g.text(font, "Atlas", mapLeft + 38, top + 10, TEXT, false);
        if (right - left > 560 && data != null) g.text(font, data.preset(), mapLeft + 78, top + 10, MUTED, false);
        // Transparent map margins reuse the window's surface, including outside the globe.
        if (data == null) {
            g.textWithWordWrap(font, Component.literal("Waiting for the integrated server..."), mapLeft + 6, mapTop + 6, Math.max(40, mapRight - mapLeft - 16), MUTED);
        } else {
            g.enableScissor(mapLeft, mapTop, mapRight, mapBottom);
            mapTexture.draw(g, data, new DebugMapRaster.View(mapRight - mapLeft, mapBottom - mapTop, centerX, centerZ,
                    globe ? globeZoom : scale(), globe, yaw, pitch, showChunks, showPlaces), mapLeft, mapTop);
            for (var waypoint : waypoints.points()) marker(g, waypoint.x(), waypoint.z(), "+", 0xffffd279);
            if (minecraft.player != null) marker(g, minecraft.player.getX(), minecraft.player.getZ(), "P", 0xffffffff);
            if (selected != null) marker(g, selected.x(), selected.z(), "X", ACCENT);
            if (selected != null) {
                g.fill(mapLeft, mapBottom - 25, mapRight, mapBottom, 0xe0101923);
                g.text(font, ellipsis(displayName(selected.label()), mapRight - mapLeft - 12), mapLeft + 4, mapBottom - 22, ACCENT, false);
                g.text(font, "X " + selected.x() + "  Y " + selected.y() + "  Z " + selected.z(), mapLeft + 4, mapBottom - 11, TEXT, false);
            } else if (data.surveyDone() == 0) {
                g.text(font, ellipsis("Building coarse biome preview", mapRight - mapLeft - 12), mapLeft + 6, mapTop + 6, MUTED, false);
            }
            g.disableScissor();
            int panelWidth = right - panelX - 8;
            g.text(font, ellipsis(data.dimension(), panelWidth), panelX, top + 48, MUTED, true);
            if (tab == 1) {
                long found = data.structures().stream().filter(e -> !e.id().equals(StructureAudit.PORTAL) && e.found()).count();
                long expected = data.structures().stream().filter(e -> !e.id().equals(StructureAudit.PORTAL)).count();
                g.text(font, found + " / " + expected + " structure types", panelX, top + 59, ACCENT, false);
                g.text(font, ellipsis(data.structureState(), panelWidth), panelX, top + 71, MUTED, false);
                g.text(font, "N+ = partial / ~ predicted", panelX, top + 106, MUTED, false);
                g.fill(panelX, top + 82, right - 8, top + 83, 0xff777777);
                if (expected > 0) g.fill(panelX, top + 82, panelX + (int) (panelWidth * found / expected), top + 83, ACCENT);
            } else {
                g.text(font, coverage.found() + " / " + coverage.total() + " biomes found", panelX, top + 59, ACCENT, false);
                String state = !data.error().isEmpty() ? "Error" : data.search().label();
                g.text(font, ellipsis(coverage.unknown() + " unconfirmed / " + state, panelWidth), panelX, top + 71, MUTED, false);
                g.fill(panelX, top + 82, right - 8, top + 83, 0xff777777);
                g.fill(panelX, top + 82, panelX + (int) ((long) panelWidth * data.surveyDone() / data.surveyTotal()), top + 83, ACCENT);
            }
            if (tab == 2) g.text(font, "World / " + data.preset(), panelX, top + 92, TEXT, false);
            if (tab == 0) {
                g.text(font, "O: observed / ~: predicted", panelX + 4, top + 106, MUTED, false);
                if (mx >= panelX && my >= top + 48 && my < top + 84)
                    tip(g, "Current dimension: " + data.dimension() + "\nExpected biomes come from this dimension's generator, including mod biomes. A completed sample does not prove completeness. Unconfirmed does not mean absent.", mx, my);
            }
            g.fill(panelX, top + (tab != 2 ? 114 : 107), right - 8, top + (tab != 2 ? 115 : 108), 0xff777777);
            if (rowLabels.isEmpty()) g.textWithWordWrap(font, Component.literal(tab == 0 ? "No biomes match this filter." : "No entries match this filter."), panelX + 4, top + (tab != 2 ? 122 : 115), panelWidth - 8, MUTED);
            String pagination = (page + 1) + " / " + Math.max(1, (rowLabels.size() + Math.max(1, rows.size()) - 1) / Math.max(1, rows.size()));
            if (font.width(pagination) < panelWidth - 112) g.text(font, pagination, panelX + 44, bottom - 17, MUTED, false);
            if (!globe && inside(mx, my)) {
                int x = (int) Math.floor(centerX + (mx - midX()) / scale());
                int z = (int) Math.floor(centerZ + (my - midY()) / scale());
                String coordinate = "X " + x + "   Z " + z;
                if (data.loaded().contains(ChunkPos.pack(x >> 4, z >> 4))) coordinate += "   loaded";
                g.text(font, ellipsis(coordinate, mapRight - mapLeft - 4), mapLeft, mapBottom + 5, MUTED, false);
            } else g.text(font, ellipsis(globe ? "Right-click: waypoint / drag: rotate" : "Right-click: waypoint / drag: pan", mapRight - mapLeft - 4), mapLeft, mapBottom + 5, MUTED, false);
        }
        super.extractRenderState(g, mx, my, delta);
        // Text is rendered independently of navigation widgets, including all read-only entries.
        for (int i = 0; i < rows.size(); i++) {
            int index = page * rows.size() + i;
            if (index >= rowLabels.size()) break;
            int y = rows.get(i).getY();
            if (tab != 2) {
                int color = rowColors.get(index);
                g.outline(panelX + 3, y + 3, 8, 8, color);
                if (rowPlaces.get(index) != null) {
                    // Pixel checkmark works with every language/font resource pack.
                    g.fill(panelX + 4, y + 6, panelX + 6, y + 8, color);
                    g.fill(panelX + 6, y + 7, panelX + 7, y + 9, color);
                    g.fill(panelX + 7, y + 5, panelX + 8, y + 8, color);
                    g.fill(panelX + 8, y + 4, panelX + 10, y + 6, color);
                }
                int valueX = right - 12 - font.width(rowValues.get(index));
                g.text(font, ellipsis(rowLabels.get(index), valueX - panelX - 20), panelX + 15, y + 3, TEXT, false);
                g.text(font, rowValues.get(index), valueX, y + 3, color, false);
            } else {
                g.text(font, ellipsis(rowLabels.get(index), right - panelX - 18), panelX + 4, y + 2, tab == 2 ? MUTED : TEXT, false);
                g.text(font, ellipsis(rowValues.get(index), right - panelX - 18), panelX + 4, y + 12, tab == 2 ? TEXT : MUTED, false);
            }
            if (mx >= panelX && mx < right - 8 && my >= y && my < y + rows.get(i).getHeight()) {
                var place = rowPlaces.get(index);
                tip(g, rowTips.get(index) + (place == null ? "" : "\nSelect to locate " + rowLabels.get(index)), mx, my);
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

    /** Rendering and hit testing share exactly the same projection and visibility rules. */
    private int[] markerPosition(double x, double z) {
        int px, py;
        if (globe) {
            double[] xyz = DebugMapMath.globe(unitX(x), unitZ(z), yaw, pitch);
            if (xyz[2] <= 0) return null;
            px = (int) (midX() + xyz[0] * radius()); py = (int) (midY() - xyz[1] * radius());
        } else {
            px = sx(data.wrapped() ? DebugMapMath.canonical(x, data.minX(), data.span()) : x);
            py = sz(data.wrapped() ? DebugMapMath.canonical(z, data.minZ(), data.span()) : z);
        }
        if (!inside(px, py) || selected != null && py >= mapBottom - 25) return null;
        return new int[]{px, py};
    }
    private void marker(GuiGraphicsExtractor g, double x, double z, String label, int color) {
        int[] position = markerPosition(x, z);
        if (position != null) g.text(font, label, position[0] - 2, position[1] - 4, color);
    }
    private DebugSnapshot.Place waypointAt(double x, double y) {
        DebugSnapshot.Place nearest = null;
        double distance = 6;
        for (var point : waypoints.points()) {
            int[] position = markerPosition(point.x(), point.z());
            if (position == null) continue;
            double candidate = Math.hypot(position[0] - x, position[1] - y);
            if (candidate <= distance) { nearest = point; distance = candidate; }
        }
        return nearest;
    }
    private void toggleWaypoint(DebugSnapshot.Place point) {
        if (!waypoints.toggle(point, 0) && minecraft.player != null)
            minecraft.player.sendSystemMessage(Component.literal("Waypoint could not be saved (limit: 128 per dimension)."));
    }
    private List<String[]> stats() {
        return List.of(new String[]{"Preset (matched by width)", data.preset()},
                new String[]{"Dimension", data.dimension()}, new String[]{"Seed", Long.toString(data.seed())},
                new String[]{"World width", data.wrapped() ? String.format(Locale.ROOT, "%,d blocks / %,d chunks", data.span(), data.span() / 16) : "Unwrapped"},
                new String[]{"Loaded / mapped chunks", data.loaded().size() + " / " + data.chunks().size()},
                new String[]{"Observed / sampled biomes", coverage.observed() + " / " + coverage.sampled()},
                new String[]{"Server tick", String.format(Locale.ROOT, "%.1f ms", data.tickMillis())},
                new String[]{"Map refresh", String.format(Locale.ROOT, "%.2f ms", mapTexture.updateMillis())},
                new String[]{"Terrain layer", minecraft.level != null && minecraft.level.dimensionType().hasCeiling() ? "Y 32 / biome-tinted" : "Surface"},
                new String[]{"Map texture", mapTexture.resolution()}, new String[]{"Texture uploads", Integer.toString(mapTexture.uploads())},
                new String[]{"Biome search", data.surveyDone() + " / " + data.surveyTotal() + " queries / " + data.search().label()},
                new String[]{"Map preview", data.search().progress().mapCursor() + " / " + data.search().progress().mapLimit() + " queries"},
                new String[]{"Structure positions checked", String.format(Locale.ROOT, "%,d", data.structureChecks())},
                new String[]{"Structure check", data.structureState()},
                new String[]{"Queries completed", String.format(Locale.ROOT, "%,d / %,d", data.surveyDone(), data.surveyTotal())},
                new String[]{"Cache", DebugSurvey.cacheStatus},
                new String[]{"History", data.historyLimited() ? "Capped; old entries evicted" : "Persistent (bounded)"},
                new String[]{"Survey status", data.error().isEmpty() ? "OK / world keeps running" : data.error()});
    }
    private boolean inside(double x, double y) { return x >= mapLeft && x < mapRight && y >= mapTop && y < mapBottom; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && inside(event.x(), event.y()) && data != null) {
            var existing = waypointAt(event.x(), event.y());
            if (existing != null) { toggleWaypoint(existing); return true; }
            if (selected != null && event.y() >= mapBottom - 25) return true;
            double x, z;
            if (globe) {
                double[] uv = DebugMapMath.unproject((event.x() - midX()) / radius(), (midY() - event.y()) / radius(), yaw, pitch);
                if (uv == null) return true;
                x = data.minX() + uv[0] * data.span();
                z = data.minZ() + Math.min(Math.nextDown(1.0), uv[1]) * data.span();
            } else {
                x = centerX + (event.x() - midX()) / scale();
                z = centerZ + (event.y() - midY()) / scale();
                if (data.wrapped() && (x < data.minX() || x >= (double) data.minX() + data.span()
                        || z < data.minZ() || z >= (double) data.minZ() + data.span())) return true;
            }
            if (data.wrapped()) {
                x = data.minX() + DebugMapMath.unit((x - data.minX()) / data.span()) * data.span();
                z = data.minZ() + DebugMapMath.unit((z - data.minZ()) / data.span()) * data.span();
            }
            int y = minecraft.player == null ? data.playerY() : minecraft.player.getBlockY();
            toggleWaypoint(new DebugSnapshot.Place("Waypoint", (int) Math.floor(x), y, (int) Math.floor(z)));
            return true;
        }
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
            if (globe) { yaw += dx * .015; pitch += dy * .0075; }
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
        if (x >= panelX && x < right && y >= top && y < bottom && dy != 0) { movePage(dy > 0 ? -1 : 1); return true; }
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
        if (globe) { yaw -= dx * .15; pitch -= dz * .075; }
        else { centerX += dx * 24 / scale(); centerZ += dz * 24 / scale(); }
    }
    @Override public Component getNarrationMessage() {
        if (tab == 1 && data != null) return Component.literal("Orbis structures. " + data.structureState()
                + ". Current dimension's structure types and End portal room layouts. Predicted locations use the current generator; saved terrain may differ. "
                + "Check structures to search, or pause and resume. Unconfirmed is not absent. Select a found place to center it.");
        return Component.literal("Orbis debug atlas. " + coverage.found() + " of " + coverage.total() + " dimension biomes found. "
                + coverage.unknown() + " unconfirmed. W A S D move the map. Plus and minus zoom. Home centers the player. "
                + "Page up and page down browse the list. Right click adds or removes a waypoint at the player height. Tab selects controls. Escape closes. "
                + "Biome symbols: checkmark found, O observed, tilde predicted, question mark unconfirmed. The world continues running.");
    }
}
