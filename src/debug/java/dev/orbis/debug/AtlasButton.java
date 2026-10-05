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
            g.fill(getX(), getY(), getRight(), getBottom(), highlight ? 0xff314858 : chosen ? 0xff233f42 : 0xff202e3b);
            if (chosen) g.fill(getX(), getBottom() - 2, getRight(), getBottom(), 0xff70dfbd);
            var font = Minecraft.getInstance().font;
            g.text(font, getMessage(), getX() + (width - font.width(getMessage())) / 2,
                    getY() + (height - 8) / 2, active ? 0xffedf3fa : 0xff7d90a1, false);
        }
        if (isFocused()) g.outline(getX(), getY(), width, height, 0xff70dfbd);
    }
}
