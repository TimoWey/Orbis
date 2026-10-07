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
    private int overlayMode; // Off, chunk grid, structures, both.
    private double globeZoom = 1;
    private Button scanButton, detailButton, resetButton, viewButton, filterButton, sortButton, previousButton, nextButton;
    private final DebugMapTexture mapTexture = new DebugMapTexture();
    private BiomeOverview.Filter filter = BiomeOverview.Filter.ALL;
    private BiomeOverview.Sort sort = BiomeOverview.Sort.NAME;
    private BiomeOverview.Counts coverage = new BiomeOverview.Counts(0, 0, 0, 0);
    private final List<Button> rows = new ArrayList<>();
    private List<AtlasRow> entries = List.of();
    private DebugSnapshot.Place selected;

    OrbisDebugScreen() { super(Component.translatable("orbis.atlas.title")); }

    @Override protected void init() {
        AtlasLayout layout = AtlasLayout.fit(width, height);
        left = layout.left(); top = layout.top(); right = layout.right(); bottom = layout.bottom(); panelX = layout.panelX();
        mapLeft = layout.mapLeft(); mapTop = layout.mapTop(); mapRight = layout.mapRight(); mapBottom = layout.mapBottom();
        int panelWidth = right - panelX - 8;
        rows.clear();
        int controlWidth = layout.controlWidth();
        button(layout.controlX(0), layout.controlTop(0), controlWidth, tr("flat"), b -> setGlobe(false), () -> !globe);
        viewButton = button(layout.controlX(1), layout.controlTop(1), controlWidth, tr("globe"), b -> setGlobe(true), () -> globe);
        button(layout.controlX(2), layout.controlTop(2), controlWidth, "-", b -> zoom(1 / 1.5));
        button(layout.controlX(3), layout.controlTop(3), controlWidth, "+", b -> zoom(1.5));
        button(layout.controlX(0), layout.controlBottom(0), controlWidth, tr("you"), b -> focusPlayer());
        button(layout.controlX(1), layout.controlBottom(1), controlWidth, tr("all"), b -> resetView());
        var overlayButton = button(layout.controlX(2), layout.controlBottom(2), controlWidth, overlayLabel(), b -> {
            overlayMode = (overlayMode + 1) % 4; b.setMessage(Component.literal(overlayLabel()));
        }, () -> overlayMode != 0);
        overlayButton.setTooltip(Tooltip.create(Component.translatable("orbis.atlas.layers_tip")));
        var cornerButton = button(layout.controlX(3), layout.controlBottom(3), controlWidth, cornerLabel(), b -> {
            AtlasPreferences.cycleCorner(); b.setMessage(Component.literal(cornerLabel()));
        });
        cornerButton.setTooltip(Tooltip.create(Component.literal(tr("corner_tip"))));
        button(panelX, top + 28, 44, tr("biomes"), b -> changeTab(0), () -> tab == 0);
        button(panelX + 48, top + 28, panelWidth - 90, Component.translatable("orbis.atlas.structures").getString(), b -> changeTab(1), () -> tab == 1);
        button(right - 46, top + 28, 38, tr("stats"), b -> changeTab(2), () -> tab == 2);
        var sizeButton = button(right - 119, top + 5, 66, sizeLabel(), b -> {
            AtlasPreferences.cycleSize(); b.setMessage(Component.literal(sizeLabel()));
        });
        sizeButton.setTooltip(Tooltip.create(Component.translatable("orbis.atlas.size_tip")));
        scanButton = button(right - 68, bottom - 22, 60, tr("sample"), b -> {
            if (data != null) {
                DebugSurvey.act(tab == 1 ? DebugSurvey.Action.STRUCTURES : DebugSurvey.Action.BIOMES);
            }
        });
        scanButton.setTooltip(Tooltip.create(Component.literal(tr("check_tip"))));
        int half = (panelWidth - 4) / 2;
        detailButton = button(panelX, bottom - 44, half, "", b -> {
            if (data != null) DebugSurvey.act(tab == 1 ? DebugSurvey.Action.COUNT : DebugSurvey.Action.MAP);
        });
        resetButton = button(panelX + half + 4, bottom - 44, panelWidth - half - 4, tr("resample"), b -> {
            if (data != null) DebugSurvey.act(tab == 1 ? DebugSurvey.Action.RESET_STRUCTURES : DebugSurvey.Action.RESAMPLE);
        });
        filterButton = button(panelX, top + 86, half, tr("all"), b -> {
            filter = BiomeOverview.Filter.values()[(filter.ordinal() + 1) % BiomeOverview.Filter.values().length];
            page = 0; updateRows();
        });
        filterButton.setTooltip(Tooltip.create(Component.literal(tr("filter_tip"))));
        sortButton = button(panelX + half + 4, top + 86, panelWidth - half - 4, tr("name"), b -> {
            sort = sort == BiomeOverview.Sort.NAME ? BiomeOverview.Sort.HITS : BiomeOverview.Sort.NAME;
            page = 0; updateRows();
        });
        sortButton.setTooltip(Tooltip.create(Component.literal(tr("sort_tip"))));
        int rowHeight = tab != 2 ? 16 : 23;
        for (int y = top + (tab != 2 ? 116 : 110); y + rowHeight <= bottom - 48; y += rowHeight) {
            final int row = rows.size();
            rows.add(addRenderableWidget(new AtlasButton(panelX, y, panelWidth, rowHeight - 1, "", b -> {
                int index = page * rows.size() + row;
                if (index < entries.size() && entries.get(index).place() != null) focus(entries.get(index).place());
            }, () -> {
                int index = page * rows.size() + row;
                return selected != null && index < entries.size() && selected.equals(entries.get(index).place());
            }, true)));
        }
        previousButton = button(panelX, bottom - 22, 18, "<", b -> movePage(-1));
        nextButton = button(panelX + 20, bottom - 22, 18, ">", b -> movePage(1));
        button(right - 49, top + 5, 41, tr("close"), b -> onClose());
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

    private String overlayLabel() { return tr(new String[]{"layers_off", "layers_chunks", "layers_places", "layers_both"}[overlayMode]); }
    private static String tr(String key, Object... args) { return Component.translatable("orbis.atlas." + key, args).getString(); }
    private static String sizeLabel() { return Component.translatable("orbis.atlas.size").getString() + ": " + new String[]{"S", "M", "L"}[AtlasPreferences.sizeIndex()]; }
    private static String cornerLabel() { return tr(new String[]{"corner_tr", "corner_tl", "corner_br", "corner_bl"}[AtlasPreferences.corner()]); }
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
        page = Math.clamp(page + step, 0, Math.max(0, (entries.size() - 1) / Math.max(1, rows.size())));
        updateRows();
    }
    private void updateRows() {
        List<AtlasRow> nextEntries = new ArrayList<>();
        if (data != null) {
            coverage = BiomeOverview.counts(data.biomes());
            scanButton.active = data.wrapped() && data.error().isEmpty();
            var work = data.search().progress();
            boolean allFound = data.search().allFound();
            scanButton.setMessage(Component.literal(tab == 1 ? data.structuresScanning() ? tr("pause") : tr("check")
                    : work.running() ? tr("pause") : allFound ? tr("found") : work.cursor() < work.limit() ? tr("resume")
                    : work.limit() < AtlasSearch.MAX ? tr("search_more") : tr("done")));
            if (tab != 1 && !work.running() && (allFound || work.cursor() == AtlasSearch.MAX)) scanButton.active = false;
            scanButton.setTooltip(Tooltip.create(Component.literal(tab == 1
                    ? tr("check_structures_tip")
                    : tr("check_biomes_tip"))));
            detailButton.active = data.wrapped() && data.error().isEmpty();
            detailButton.setMessage(Component.literal(tab == 1 ? data.structureCounting() && data.structuresScanning() ? tr("counting") : tr("count_all")
                    : work.mapping() ? tr("pause_map") : work.mapCursor() < work.mapLimit() ? tr("map_go")
                    : work.mapCursor() < AtlasSearch.MAP_MAX ? tr("map_more") : tr("map_done")));
            if (tab == 1 && data.structureCounting() && data.structuresScanning()
                    || tab != 1 && work.mapCursor() == AtlasSearch.MAP_MAX) detailButton.active = false;
            detailButton.setTooltip(Tooltip.create(Component.literal(tab == 1
                    ? tr("count_tip")
                    : tr("map_tip"))));
            resetButton.active = data.wrapped();
            resetButton.setTooltip(Tooltip.create(Component.literal(tab == 1
                    ? tr("reset_structures_tip")
                    : tr("reset_biomes_tip"))));
            viewButton.active = data.wrapped();
            if (!data.wrapped() && globe) setGlobe(false);
            if (tab == 0) for (var biome : BiomeOverview.select(data.biomes(), filter, sort)) {
                String value = biome.observed() ? "O" : biome.found() ? "~" : "?";
                String status = biome.observed() ? tr("observed") : biome.found() ? tr("sampled") : tr("unconfirmed_tip");
                String tip = biome.id() + "\n" + status + "\n" + tr("biome_queries", String.format(Locale.ROOT, "%,d", biome.sampleHits()));
                nextEntries.add(new AtlasRow(displayName(biome.id()), value, tip,
                        biome.observed() ? ACCENT : biome.found() ? 0xff8dbffa : MUTED, biome.location()));
            }
            if (tab == 1) for (var entry : data.structures().stream().sorted(sort == BiomeOverview.Sort.NAME
                    ? Comparator.comparing(StructureAudit.Entry::id)
                    : Comparator.comparing(StructureAudit.Entry::found).reversed().thenComparing(StructureAudit.Entry::id)).toList()) {
                if (filter == BiomeOverview.Filter.FOUND && !entry.found()) continue;
                if (filter == BiomeOverview.Filter.UNCONFIRMED && entry.found()) continue;
                String label = entry.id().equals(StructureAudit.PORTAL) ? tr("portal_room") : displayName(entry.id());
                String value = entry.countLabel() + " " + (entry.observed() ? "O" : entry.found() ? "~" : "?");
                String tip = (entry.id().equals(StructureAudit.PORTAL) ? tr("portal_tip") + "\n" : "")
                        + entry.id() + "\n" + entry.status() + "\n" + entry.countDetails() + "\n"
                        + tr(entry.observed() ? "structure_observed" : "structure_predicted");
                nextEntries.add(new AtlasRow(label, value, tip, entry.observed() ? ACCENT : entry.found() ? 0xff8dbffa : MUTED, entry.location()));
            }
            // Block-entity sightings supplement structures; they are not expected structure types.
            if (tab == 1 && filter != BiomeOverview.Filter.UNCONFIRMED) for (var place : data.places()) {
                if (!place.label().equals("End gateway (seen)") && !place.label().equals("End portal block (seen)")) continue;
                nextEntries.add(new AtlasRow(place.label(), "O", place.label() + "\n" + tr("portal_seen_tip") + "\nX "
                        + place.x() + " Y " + place.y() + " Z " + place.z(), ACCENT, place));
            }
            if (tab == 2) for (String[] entry : stats()) {
                nextEntries.add(new AtlasRow(entry[0], entry[1], entry[0] + ": " + entry[1], MUTED, null));
            }
        } else { scanButton.active = false; detailButton.active = false; resetButton.active = false; }
        entries = List.copyOf(nextEntries);
        page = Math.clamp(page, 0, Math.max(0, (entries.size() - 1) / Math.max(1, rows.size())));
        filterButton.visible = tab != 2;
        sortButton.visible = tab != 2;
        filterButton.setMessage(Component.literal(switch (filter) {
            case ALL -> tr("show_all"); case UNCONFIRMED -> tr("unconfirmed"); case FOUND -> tr("found");
        }));
        sortButton.setMessage(Component.literal(sort == BiomeOverview.Sort.NAME ? tr("name_sort") : tab == 1 ? tr("found_sort") : tr("samples")));
        previousButton.active = page > 0;
        nextButton.active = (page + 1) * rows.size() < entries.size();
        for (int i = 0; i < rows.size(); i++) {
            int index = page * rows.size() + i;
            Button row = rows.get(i);
            // Only locations are interactive. Unknown biomes and statistics are plain text.
            row.visible = index < entries.size();
            if (row.visible) {
                var place = entries.get(index).place();
                row.active = place != null;
                row.setMessage(Component.literal(entries.get(index).tooltip() + (place == null ? "" : "\n" + tr("center_at", place.x(), place.y(), place.z()))));
                row.setTooltip(Tooltip.create(Component.literal(entries.get(index).tooltip())));
            }
        }
    }
    private String searchLabel() {
        var work = data.search().progress();
        if (!data.wrapped()) return tr("unwrapped");
        return tr(data.search().allFound() ? "state_found" : work.running() ? "state_searching"
                : work.cursor() < work.limit() ? "state_paused" : work.limit() == AtlasSearch.MAX ? "state_limit" : "state_pass");
    }
    private static String displayName(String id) {
        String value = id.replace("minecraft:", "").replace("orbis:", "").replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    private void resetView() {
        if (data == null) return;
        selected = null;
        centerX = data.minX() + data.span() / 2.0;
        centerZ = data.minZ() + data.span() / 2.0;
        zoom = 1;
        if (!data.wrapped()) {
            var bounds = knownBounds();
            centerX = bounds.centerX(); centerZ = bounds.centerZ();
            zoom = bounds.zoom(data.span(), mapRight - mapLeft, mapBottom - mapTop);
        }
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
        else zoom = Math.clamp(zoom * factor, minimumZoom(), data == null ? 1 : Math.max(1, 8.0 * data.span() / Math.max(16, Math.min(mapRight - mapLeft, mapBottom - mapTop))));
        constrainView();
    }
    private AtlasViewBounds knownBounds() {
        int x = minecraft.player == null ? data.playerX() : minecraft.player.getBlockX();
        int z = minecraft.player == null ? data.playerZ() : minecraft.player.getBlockZ();
        return AtlasViewBounds.known(data.chunks(), waypoints.points(), x, z);
    }
    private double minimumZoom() {
        if (data == null || data.wrapped()) return 1;
        return Math.min(1, knownBounds().zoom(data.span(), mapRight - mapLeft, mapBottom - mapTop));
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
        if (data != null) g.text(font, ellipsis(data.wrapped() ? tr("blocks_chunks", String.format(Locale.ROOT, "%,d", data.span()), String.format(Locale.ROOT, "%,d", data.span() / 16)) : tr("unwrapped"), Math.max(0, right - 123 - (mapLeft + 78))), mapLeft + 78, top + 10, MUTED, false);
        // Transparent map margins reuse the window's surface, including outside the globe.
        if (data == null) {
            g.textWithWordWrap(font, Component.literal(tr("waiting")), mapLeft + 6, mapTop + 6, Math.max(40, mapRight - mapLeft - 16), MUTED);
        } else {
            g.enableScissor(mapLeft, mapTop, mapRight, mapBottom);
            mapTexture.draw(g, data, new DebugMapRaster.View(mapRight - mapLeft, mapBottom - mapTop, centerX, centerZ,
                    globe ? globeZoom : scale(), globe, yaw, pitch, (overlayMode & 1) != 0, (overlayMode & 2) != 0), mapLeft, mapTop);
            for (var waypoint : waypoints.points()) marker(g, waypoint.x(), waypoint.z(), "+", 0xffffd279);
            if (minecraft.player != null) marker(g, minecraft.player.getX(), minecraft.player.getZ(), "P", 0xffffffff);
            if (selected != null) marker(g, selected.x(), selected.z(), "X", ACCENT);
            if (selected != null) {
                g.fill(mapLeft, mapBottom - 25, mapRight, mapBottom, 0xe0101923);
                g.text(font, ellipsis(displayName(selected.label()), mapRight - mapLeft - 12), mapLeft + 4, mapBottom - 22, ACCENT, false);
                g.text(font, "X " + selected.x() + "  Y " + selected.y() + "  Z " + selected.z(), mapLeft + 4, mapBottom - 11, TEXT, false);
            } else if (data.wrapped() && data.search().progress().mapping() && data.search().progress().mapCursor() < AtlasSearch.MAP_COARSE) {
                g.text(font, ellipsis(tr("building_preview"), mapRight - mapLeft - 12), mapLeft + 6, mapTop + 6, MUTED, false);
            }
            g.disableScissor();
            int panelWidth = right - panelX - 8;
            g.text(font, ellipsis(data.dimension(), panelWidth), panelX, top + 48, MUTED, true);
            if (tab == 1) {
                long found = data.structures().stream().filter(e -> !e.id().equals(StructureAudit.PORTAL) && e.found()).count();
                long expected = data.structures().stream().filter(e -> !e.id().equals(StructureAudit.PORTAL)).count();
                g.text(font, ellipsis(tr("structures_found", found, expected), panelWidth), panelX, top + 59, ACCENT, false);
                g.text(font, ellipsis(data.structureState(), panelWidth), panelX, top + 71, MUTED, false);
                g.text(font, ellipsis(tr("structure_legend"), panelWidth), panelX, top + 106, MUTED, false);
                g.fill(panelX, top + 82, right - 8, top + 83, 0xff777777);
                if (expected > 0) g.fill(panelX, top + 82, panelX + (int) (panelWidth * found / expected), top + 83, ACCENT);
            } else {
                g.text(font, ellipsis(tr("biomes_found", coverage.found(), coverage.total()), panelWidth), panelX, top + 59, ACCENT, false);
                String state = !data.error().isEmpty() ? tr("error") : searchLabel();
                g.text(font, ellipsis(tr("unknown_state", coverage.unknown(), state), panelWidth), panelX, top + 71, MUTED, false);
                g.fill(panelX, top + 82, right - 8, top + 83, 0xff777777);
                g.fill(panelX, top + 82, panelX + (int) ((long) panelWidth * data.search().progress().cursor() / data.search().progress().limit()), top + 83, ACCENT);
            }
            if (tab == 2) g.text(font, ellipsis(tr("world", data.preset()), panelWidth), panelX, top + 92, TEXT, false);
            if (tab == 0) {
                g.text(font, ellipsis(tr("biome_legend"), panelWidth - 4), panelX + 4, top + 106, MUTED, false);
                if (mx >= panelX && my >= top + 48 && my < top + 84)
                    tip(g, tr("dimension_tip", data.dimension()), mx, my);
            }
            g.fill(panelX, top + (tab != 2 ? 114 : 107), right - 8, top + (tab != 2 ? 115 : 108), 0xff777777);
            if (entries.isEmpty()) g.textWithWordWrap(font, Component.literal(tab == 0 ? tr("no_biomes") : tr("no_entries")), panelX + 4, top + (tab != 2 ? 122 : 115), panelWidth - 8, MUTED);
            String pagination = (page + 1) + " / " + Math.max(1, (entries.size() + Math.max(1, rows.size()) - 1) / Math.max(1, rows.size()));
            if (font.width(pagination) < panelWidth - 112) g.text(font, pagination, panelX + 44, bottom - 17, MUTED, false);
            if (!globe && inside(mx, my)) {
                int x = (int) Math.floor(centerX + (mx - midX()) / scale());
                int z = (int) Math.floor(centerZ + (my - midY()) / scale());
                String coordinate = "X " + x + "   Z " + z;
                if (data.loaded().contains(ChunkPos.pack(x >> 4, z >> 4))) coordinate += "   " + tr("loaded");
                g.text(font, ellipsis(coordinate, mapRight - mapLeft - 4), mapLeft, mapBottom + 5, MUTED, false);
            } else g.text(font, ellipsis(globe ? tr("drag_globe") : tr("drag_flat"), mapRight - mapLeft - 4), mapLeft, mapBottom + 5, MUTED, false);
        }
        super.extractRenderState(g, mx, my, delta);
        // Text is rendered independently of navigation widgets, including all read-only entries.
        for (int i = 0; i < rows.size(); i++) {
            int index = page * rows.size() + i;
            if (index >= entries.size()) break;
            int y = rows.get(i).getY();
            if (tab != 2) {
                int color = entries.get(index).color();
                g.outline(panelX + 3, y + 3, 8, 8, color);
                if (entries.get(index).place() != null) {
                    // Pixel checkmark works with every language/font resource pack.
                    g.fill(panelX + 4, y + 6, panelX + 6, y + 8, color);
                    g.fill(panelX + 6, y + 7, panelX + 7, y + 9, color);
                    g.fill(panelX + 7, y + 5, panelX + 8, y + 8, color);
                    g.fill(panelX + 8, y + 4, panelX + 10, y + 6, color);
                }
                int valueX = right - 12 - font.width(entries.get(index).value());
                g.text(font, ellipsis(entries.get(index).label(), valueX - panelX - 20), panelX + 15, y + 3, TEXT, false);
                g.text(font, entries.get(index).value(), valueX, y + 3, color, false);
            } else {
                g.text(font, ellipsis(entries.get(index).label(), right - panelX - 18), panelX + 4, y + 2, tab == 2 ? MUTED : TEXT, false);
                g.text(font, ellipsis(entries.get(index).value(), right - panelX - 18), panelX + 4, y + 12, tab == 2 ? TEXT : MUTED, false);
            }
            if (mx >= panelX && mx < right - 8 && my >= y && my < y + rows.get(i).getHeight()) {
                var place = entries.get(index).place();
                tip(g, entries.get(index).tooltip() + (place == null ? "" : "\n" + tr("select_locate", entries.get(index).label())), mx, my);
            }
        }
    }
    private void tip(GuiGraphicsExtractor g, String message, int x, int y) {
        g.setTooltipForNextFrame(font, font.split(Component.literal(message), Math.min(180, Math.max(80, width / 3))), x, y);
    }
    private String ellipsis(String value, int available) {
        if (available < font.width("...")) return "";
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
            minecraft.player.sendSystemMessage(Component.literal(tr("waypoint_failed")));
    }
    private List<String[]> stats() {
        return List.of(new String[]{tr("preset"), data.preset()},
                new String[]{tr("dimension"), data.dimension()},
                new String[]{tr("bounds"), data.wrapped() ? tr("bounds_value", data.minX(), (long) data.minX() + data.span(), data.minZ(), (long) data.minZ() + data.span()) : tr("unwrapped")}, new String[]{tr("seed"), Long.toString(data.seed())},
                new String[]{tr("world_width"), data.wrapped() ? tr("blocks_chunks", String.format(Locale.ROOT, "%,d", data.span()), String.format(Locale.ROOT, "%,d", data.span() / 16)) : tr("unwrapped")},
                new String[]{tr("mapped_chunks"), data.loaded().size() + " / " + data.chunks().size()},
                new String[]{tr("observed_biomes"), coverage.observed() + " / " + coverage.sampled()},
                new String[]{tr("server_tick"), String.format(Locale.ROOT, "%.1f ms", data.tickMillis())},
                new String[]{tr("map_refresh"), String.format(Locale.ROOT, "%.2f ms", mapTexture.updateMillis())},
                new String[]{tr("terrain_layer"), minecraft.level != null && minecraft.level.dimensionType().hasCeiling() ? tr("terrain_ceiling") : tr("surface")},
                new String[]{tr("map_texture"), mapTexture.resolution()}, new String[]{tr("uploads"), Integer.toString(mapTexture.uploads())},
                new String[]{tr("biome_search"), tr("queries", data.search().progress().cursor(), data.search().progress().limit()) + " / " + searchLabel()},
                new String[]{tr("map_preview"), tr("queries", data.search().progress().mapCursor(), data.search().progress().mapLimit())},
                new String[]{tr("structure_positions"), String.format(Locale.ROOT, "%,d", data.structureChecks())},
                new String[]{tr("structure_check"), data.structureState()},
                new String[]{tr("cache"), DebugSurvey.cacheStatus},
                new String[]{tr("preferences"), AtlasPreferences.status().isEmpty() ? tr("saved") : AtlasPreferences.status()},
                new String[]{tr("waypoints"), waypoints.status().isEmpty() ? tr("saved") : waypoints.status()},
                new String[]{tr("history"), data.historyLimited() ? tr("history_capped") : tr("persistent")},
                new String[]{tr("survey_status"), data.error().isEmpty() ? tr("status_ok") : data.error()});
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
        if (tab == 1 && data != null) return Component.translatable("orbis.atlas.narration_structures", data.structureState());
        return Component.translatable("orbis.atlas.narration_atlas", coverage.found(), coverage.total(), coverage.unknown());
    }
}
