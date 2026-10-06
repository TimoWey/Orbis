package dev.orbis.debug;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Quiet controls with native keyboard focus, tooltips and narration. */
final class AtlasButton extends Button {
    private final BooleanSupplier selected;
    private final boolean row;

    AtlasButton(int x, int y, int width, int height, String label, OnPress action,
                BooleanSupplier selected, boolean row) {
        super(x, y, width, height, Component.literal(label), action, DEFAULT_NARRATION);
        this.selected = selected;
        this.row = row;
    }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean highlight = active && isHoveredOrFocused();
        boolean chosen = selected.getAsBoolean();
        if (row) {
            if (highlight || chosen) g.fill(getX(), getY(), getRight(), getBottom(), 0xff253b49);
        } else {
            extractDefaultSprite(g);
            extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
            if (chosen) g.outline(getX() + 1, getY() + 1, width - 2, height - 2, 0xffffffff);
        }
        if (isFocused()) g.outline(getX(), getY(), width, height, 0xff70dfbd);
    }
}
