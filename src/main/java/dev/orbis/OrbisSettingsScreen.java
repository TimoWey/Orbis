package dev.orbis;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OrbisSettingsScreen extends Screen {
    private final Screen parent;
    private final Consumer<WorldSize> onDone;
    private String chosen;
    private WorldSize valid;
    private HeaderAndFooterLayout layout;
    private ScrollableLayout scroll;
    private StringWidget details;
    private Button done;

    public OrbisSettingsScreen(Screen parent, WorldSize current, Consumer<WorldSize> onDone) {
        super(Component.literal("Orbis world settings"));
        this.parent = parent;
        this.onDone = onDone;
        this.chosen = Integer.toString(current.overworldChunks());
    }

    @Override protected void init() {
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        LinearLayout contents = LinearLayout.vertical().spacing(8);
        contents.addChild(new MultiLineTextWidget(Component.literal(
                "Development presets — biome coverage is not guaranteed. Nether width is always 1/8; the End stays unwrapped."), font).setMaxWidth(300));
        EditBox widthInput = new EditBox(font, 300, 20, Component.literal("Overworld width in chunks"));
        widthInput.setMaxLength(7);
        widthInput.setValue(chosen);
        for (WorldSize.Preset preset : WorldSize.Preset.values()) {
            contents.addChild(Button.builder(Component.literal(preset.label + " — " + preset.size.overworldChunks() + " chunks"),
                    b -> widthInput.setValue(Integer.toString(preset.size.overworldChunks()))).width(300).build());
        }
        contents.addChild(new StringWidget(Component.literal("Custom Overworld width (chunks)"), font));
        contents.addChild(widthInput);
        details = contents.addChild(new StringWidget(Component.empty(), font));
        contents.addChild(new MultiLineTextWidget(Component.literal(
                "Biomes: NOT CHECKED in Overworld or Nether. Small worlds may miss many biomes. Earth is experimental and does not pre-generate the planet. Use new test worlds; conversion is not implemented."), font).setMaxWidth(300));
        scroll = new ScrollableLayout(minecraft, contents, layout.getContentHeight());
        layout.addToContents(scroll);
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        done = footer.addChild(Button.builder(Component.literal("Done"), b -> {
            if (valid != null) { onDone.accept(valid); onClose(); }
        }).build());
        footer.addChild(Button.builder(Component.literal("Cancel"), b -> onClose()).build());
        widthInput.setResponder(this::validate);
        validate(chosen);
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    private void validate(String value) {
        chosen = value;
        try {
            valid = new WorldSize(Integer.parseInt(value));
            details.setMessage(Component.literal("Nether: " + valid.netherChunks() + " chunks | Ratio 1:8"));
        } catch (IllegalArgumentException bad) {
            valid = null;
            details.setMessage(Component.literal("Use 128–1411456, in steps of 16."));
        }
        done.active = valid != null;
    }

    @Override protected void repositionElements() {
        scroll.arrangeElements();
        scroll.setMaxHeight(layout.getContentHeight());
        layout.arrangeElements();
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
