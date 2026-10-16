package dev.orbis.atlas;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Quiet controls with native keyboard focus, tooltips and narration. */
final class AtlasButton extends Button {
    private final BooleanSupplier selected;
    private final boolean row;
    /** A small pixel picture drawn instead of the label, one GUI pixel per character; '#' is ink. */
    private String[] picture;

    AtlasButton(int x, int y, int width, int height, String label, OnPress action,
                BooleanSupplier selected, boolean row) {
        super(x, y, width, height, Component.literal(label), action, DEFAULT_NARRATION);
        this.selected = selected;
        this.row = row;
    }

    /** Shows {@code picture} instead of the label; the label stays as the narrated name. */
    AtlasButton picture(String... picture) { this.picture = picture; return this; }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean highlight = active && isHoveredOrFocused();
        boolean chosen = selected.getAsBoolean();
        if (row) {
            if (highlight || chosen) g.fill(getX(), getY(), getRight(), getBottom(), 0xff253b49);
        } else {
            extractDefaultSprite(g);
            if (picture != null) draw(g, active ? highlight ? 0xffffffa0 : 0xffffffff : 0xffa0a0a0);
            else extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
            if (chosen) g.outline(getX() + 1, getY() + 1, width - 2, height - 2, 0xffffffff);
        }
        if (isFocused()) g.outline(getX(), getY(), width, height, 0xff70dfbd);
    }

    /** The picture centred on the button, with a one-pixel shadow like the game's button text. */
    private void draw(GuiGraphicsExtractor g, int color) {
        int x0 = getX() + (width - picture[0].length()) / 2, y0 = getY() + (height - picture.length) / 2;
        for (int pass = 0; pass < 2; pass++) for (int y = 0; y < picture.length; y++) for (int x = 0; x < picture[y].length(); x++) {
            if (picture[y].charAt(x) != '#') continue;
            int px = x0 + x + (pass == 0 ? 1 : 0), py = y0 + y + (pass == 0 ? 1 : 0);
            g.fill(px, py, px + 1, py + 1, pass == 0 ? 0xff3f3f3f : color);
        }
    }
}
