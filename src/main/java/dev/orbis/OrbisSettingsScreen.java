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
import net.minecraft.network.chat.CommonComponents;

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
        super(Component.translatable("gui.orbis.settings.title"));
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
        controls.addChild(heading("choose_size"));
        slider = controls.addChild(new SizeSlider(lastValid, controlsWidth));
        controls.addChild(text("range", controlsWidth, ChatFormatting.GRAY));
        LinearLayout presets = controls.addChild(LinearLayout.horizontal().spacing(4));
        int presetWidth = (controlsWidth - 16) / 5;
        for (WorldSize.Preset preset : WorldSize.Preset.values()) {
            var button = presets.addChild(Button.builder(sizeLabel(preset.size),
                    b -> widthInput.setValue(Integer.toString(preset.size.overworldChunks())))
                    .width(presetWidth).build());
            presetButtons.put(preset, button);
        }
        controls.addChild(SpacerElement.height(4));
        controls.addChild(heading("exact_side"));
        widthInput = controls.addChild(new EditBox(font, controlsWidth, 20, tr("exact_chunks")));
        widthInput.setMaxLength(7);
        widthInput.setValue(chosen);
        inputHint = controls.addChild(text("", controlsWidth, ChatFormatting.GRAY));

        LinearLayout overview = contents.addChild(LinearLayout.vertical().spacing(8));
        overview.defaultCellSetting().alignHorizontallyLeft();
        overview.addChild(SpacerElement.width(summaryWidth));
        overview.addChild(heading("overview"));
        summary = overview.addChild(text("", summaryWidth, ChatFormatting.WHITE));
        overview.addChild(SpacerElement.height(4));
        dimensions = overview.addChild(text("", summaryWidth, ChatFormatting.GRAY));
        overview.addChild(SpacerElement.height(4));
        overview.addChild(text("edges", summaryWidth, ChatFormatting.GRAY));
        warning = overview.addChild(text("", summaryWidth, ChatFormatting.YELLOW));
        scroll = new ScrollableLayout(minecraft, contents, layout.getContentHeight());
        layout.addToContents(scroll);
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        int actionWidth = Math.min(150, (width - 40 - 8) / 2);
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> {
            validate(chosen);
            if (valid != null) {
                onDone.accept(valid);
                onClose();
            } else {
                setInitialFocus(widthInput);
                triggerImmediateNarration(false);
            }
        }).width(actionWidth).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(actionWidth).build());
        widthInput.setResponder(this::validate);
        validate(chosen);
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        setInitialFocus(slider);
    }

    private StringWidget heading(String label) {
        return new StringWidget(tr(label).withStyle(ChatFormatting.BOLD), font);
    }

    private MultiLineTextWidget text(String message, int maxWidth, ChatFormatting color) {
        return new MultiLineTextWidget((message.isEmpty() ? Component.empty() : tr(message)).withStyle(color), font).setMaxWidth(maxWidth);
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("gui.orbis.settings." + key, arguments);
    }

    private static net.minecraft.network.chat.MutableComponent sizeLabel(WorldSize size) {
        return Component.translatable("gui.orbis.size." + size.label().toLowerCase(Locale.ROOT));
    }

    private static String number(long value) { return String.format(Locale.ROOT, "%,d", value); }
    private static String area(WorldSize size) { return String.format(Locale.ROOT, "%,.2f", size.areaSquareKilometres()); }

    private void validate(String value) {
        chosen = value;
        try {
            valid = WorldSize.select(Integer.parseInt(value));
            lastValid = valid;
            slider.show(valid);
            widthInput.setMessage(tr("exact_chunks"));
            inputHint.setMessage(tr("input_hint", number(WorldSize.MIN_CHUNKS), number(WorldSize.EARTH_CHUNKS))
                    .withStyle(ChatFormatting.GRAY));
            summary.setMessage(sizeLabel(valid).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(tr("area", area(valid)).withStyle(ChatFormatting.WHITE)));
            dimensions.setMessage(tr("dimensions", number(valid.overworldChunks()), number(valid.overworldBlocks()),
                    number(valid.netherChunks()), number(valid.netherBlocks())).withStyle(ChatFormatting.GRAY));
            warning.setMessage(tr(valid.equals(WorldSize.Preset.EARTH.size) ? "earth_warning" : "biome_warning")
                    .withStyle(ChatFormatting.YELLOW));
        } catch (IllegalArgumentException bad) {
            valid = null;
            Component error = tr("invalid", number(WorldSize.MIN_CHUNKS), number(WorldSize.EARTH_CHUNKS));
            widthInput.setMessage(tr("exact_chunks").append(". ").append(error));
            inputHint.setMessage(error.copy().withStyle(ChatFormatting.YELLOW));
            summary.setMessage(tr("choose_valid").withStyle(ChatFormatting.YELLOW));
            dimensions.setMessage(tr("invalid_hint").withStyle(ChatFormatting.GRAY));
            warning.setMessage(Component.empty());
        }
        presetButtons.forEach((preset, button) -> {
            boolean selected = preset.size.equals(valid);
            button.setMessage(selected ? sizeLabel(preset.size).withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                    : sizeLabel(preset.size));
            button.setTooltip(Tooltip.create(tr("preset_hint", sizeLabel(preset.size),
                    selected ? tr("selected") : Component.empty(), number(preset.size.overworldChunks()), area(preset.size),
                    preset == WorldSize.Preset.NORMAL ? tr("default_size") : Component.empty())));
        });
        if (scroll != null) repositionElements();
    }
    private final class SizeSlider extends AbstractSliderButton {
        private WorldSize displayed;

        SizeSlider(WorldSize initial, int width) {
            super(0, 0, width, 20, Component.empty(), initial.sliderPosition());
            displayed = initial.overworldChunks() < WorldSize.MIN_CHUNKS ? WorldSize.Preset.TINY.size : initial;
            updateMessage();
            setTooltip(Tooltip.create(tr("slider_hint")));
        }

        void show(WorldSize size) {
            displayed = size;
            value = size.sliderPosition();
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(tr("slider", sizeLabel(displayed), number(displayed.overworldChunks())));
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
