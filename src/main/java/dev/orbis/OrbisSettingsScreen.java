package dev.orbis;

import java.util.Locale;
import java.util.EnumMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OrbisSettingsScreen extends Screen {
    private final Screen parent;
    private final Consumer<WorldSize> onDone;
    private String chosen;
    private WorldSize valid;
    private HeaderAndFooterLayout layout;
    private ScrollableLayout scroll;
    private MultiLineTextWidget inputHint;
    private MultiLineTextWidget summary;
    private MultiLineTextWidget dimensions;
    private MultiLineTextWidget warning;
    private final EnumMap<WorldSize.Preset, Button> presetButtons = new EnumMap<>(WorldSize.Preset.class);
    private EditBox widthInput;
    private SizeSlider slider;
    private WorldSize lastValid;

    public OrbisSettingsScreen(Screen parent, WorldSize current, Consumer<WorldSize> onDone) {
        super(Component.literal("Orbis world settings"));
        this.parent = parent;
        this.onDone = onDone;
        this.chosen = Integer.toString(current.overworldChunks());
        this.lastValid = current;
    }

    @Override protected void init() {
        scroll = null;
        presetButtons.clear();
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        boolean wide = width >= 620;
        int controlsWidth = Math.min(320, Math.max(220, width - 40));
        int summaryWidth = wide ? 220 : controlsWidth;
        LinearLayout contents = wide ? LinearLayout.horizontal().spacing(28) : LinearLayout.vertical().spacing(20);
        contents.defaultCellSetting().alignVerticallyTop().alignHorizontallyLeft();
        LinearLayout controls = contents.addChild(LinearLayout.vertical().spacing(8));
        controls.defaultCellSetting().alignHorizontallyLeft();
        controls.addChild(SpacerElement.width(controlsWidth));
        controls.addChild(heading("Choose a size"));
        slider = controls.addChild(new SizeSlider(lastValid, controlsWidth));
        controls.addChild(text("Tiny minimum · Earth maximum", controlsWidth, ChatFormatting.GRAY));
        LinearLayout presets = controls.addChild(LinearLayout.horizontal().spacing(4));
        int presetWidth = (controlsWidth - 16) / 5;
        for (WorldSize.Preset preset : WorldSize.Preset.values()) {
            var button = presets.addChild(Button.builder(Component.literal(preset.label),
                    b -> widthInput.setValue(Integer.toString(preset.size.overworldChunks())))
                    .width(presetWidth).build());
            presetButtons.put(preset, button);
        }
        controls.addChild(SpacerElement.height(4));
        controls.addChild(heading("Exact side length"));
        widthInput = controls.addChild(new EditBox(font, controlsWidth, 20, Component.literal("Exact side length in chunks")));
        widthInput.setMaxLength(7);
        widthInput.setValue(chosen);
        inputHint = controls.addChild(text("", controlsWidth, ChatFormatting.GRAY));

        LinearLayout overview = contents.addChild(LinearLayout.vertical().spacing(8));
        overview.defaultCellSetting().alignHorizontallyLeft();
        overview.addChild(SpacerElement.width(summaryWidth));
        overview.addChild(heading("World overview"));
        summary = overview.addChild(text("", summaryWidth, ChatFormatting.WHITE));
        overview.addChild(SpacerElement.height(4));
        dimensions = overview.addChild(text("", summaryWidth, ChatFormatting.GRAY));
        overview.addChild(SpacerElement.height(4));
        overview.addChild(text("Opposite edges connect. Keep exploring in any direction.", summaryWidth, ChatFormatting.GRAY));
        warning = overview.addChild(text("", summaryWidth, ChatFormatting.YELLOW));
        scroll = new ScrollableLayout(minecraft, contents, layout.getContentHeight());
        layout.addToContents(scroll);
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        int actionWidth = Math.min(150, (width - 40 - 8) / 2);
        footer.addChild(Button.builder(Component.literal("Done"), b -> {
            validate(chosen);
            if (valid != null) {
                onDone.accept(valid);
                onClose();
            } else {
                setInitialFocus(widthInput);
                triggerImmediateNarration(false);
            }
        }).width(actionWidth).build());
        footer.addChild(Button.builder(Component.literal("Cancel"), b -> onClose()).width(actionWidth).build());
        widthInput.setResponder(this::validate);
        validate(chosen);
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        setInitialFocus(slider);
    }

    private StringWidget heading(String label) {
        return new StringWidget(Component.literal(label).withStyle(ChatFormatting.BOLD), font);
    }

    private MultiLineTextWidget text(String message, int maxWidth, ChatFormatting color) {
        return new MultiLineTextWidget(Component.literal(message).withStyle(color), font).setMaxWidth(maxWidth);
    }

    private void validate(String value) {
        chosen = value;
        try {
            valid = WorldSize.select(Integer.parseInt(value));
            lastValid = valid;
            slider.show(valid);
            widthInput.setMessage(Component.literal("Exact side length in chunks"));
            inputHint.setMessage(Component.literal(String.format(Locale.US,
                    "%,d–%,d chunks · Steps of 16\n1 chunk = 16 × 16 blocks",
                    WorldSize.MIN_CHUNKS, WorldSize.EARTH_CHUNKS)).withStyle(ChatFormatting.GRAY));
            summary.setMessage(Component.literal(valid.label()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(Component.literal(String.format(Locale.US, "\n%,.2f km²", valid.areaSquareKilometres()))
                            .withStyle(ChatFormatting.WHITE)));
            dimensions.setMessage(Component.literal(String.format(Locale.US,
                    "Overworld side\n%,d chunks · %,d blocks\n\nNether side\n%,d chunks · %,d blocks\n\nEnd · Unchanged",
                    valid.overworldChunks(), valid.overworldBlocks(), valid.netherChunks(), valid.netherBlocks()))
                    .withStyle(ChatFormatting.GRAY));
            warning.setMessage(Component.literal(valid.equals(WorldSize.Preset.EARTH.size)
                    ? "Earth is experimental. Biomes and structures are not guaranteed."
                    : "Some biomes and structures may be missing.").withStyle(ChatFormatting.YELLOW));
        } catch (IllegalArgumentException bad) {
            valid = null;
            Component error = Component.literal(String.format(Locale.US,
                    "Enter %,d–%,d chunks in steps of 16, or choose a preset.",
                    WorldSize.MIN_CHUNKS, WorldSize.EARTH_CHUNKS));
            widthInput.setMessage(Component.literal("Exact side length in chunks. ").append(error));
            inputHint.setMessage(error.copy().withStyle(ChatFormatting.YELLOW));
            summary.setMessage(Component.literal("Choose a valid size").withStyle(ChatFormatting.YELLOW));
            dimensions.setMessage(Component.literal("Adjust the side length or choose a preset to see its area and dimensions.")
                    .withStyle(ChatFormatting.GRAY));
            warning.setMessage(Component.empty());
        }
        presetButtons.forEach((preset, button) -> {
            boolean selected = preset.size.equals(valid);
            button.setMessage(selected ? Component.literal(preset.label).withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                    : Component.literal(preset.label));
            button.setTooltip(Tooltip.create(Component.literal(String.format(Locale.US,
                    "%s%s\n%,d chunks per side\n%,.2f km²%s", preset.label, selected ? " · Selected" : "",
                    preset.size.overworldChunks(), preset.size.areaSquareKilometres(),
                    preset == WorldSize.Preset.NORMAL ? "\nDefault size" : ""))));
        });
        if (scroll != null) repositionElements();
    }

    private final class SizeSlider extends AbstractSliderButton {
        private WorldSize displayed;

        SizeSlider(WorldSize initial, int width) {
            super(0, 0, width, 20, Component.empty(), initial.sliderPosition());
            displayed = initial.overworldChunks() < WorldSize.MIN_CHUNKS ? WorldSize.Preset.TINY.size : initial;
            updateMessage();
            setTooltip(Tooltip.create(Component.literal(
                    "Adjust world size from Tiny to Earth. Type a side length below for an exact value.")));
        }

        void show(WorldSize size) {
            displayed = size;
            value = size.sliderPosition();
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Component.literal(String.format(Locale.US, "%s · %,d chunks",
                    displayed.label(), displayed.overworldChunks())));
        }

        @Override protected void applyValue() {
            // Responder snaps the handle without recursively calling setValue.
            widthInput.setValue(Integer.toString(WorldSize.fromSlider(value).overworldChunks()));
        }
    }

    @Override protected void repositionElements() {
        if (scroll == null) return;
        scroll.arrangeElements();
        scroll.setMaxHeight(layout.getContentHeight());
        layout.arrangeElements();
    }

    @Override public void resize(int width, int height) {
        super.resize(width, height);
        rebuildWidgets();
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
