package dev.orbis.atlas;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/** A north-up heading projected through the same chart as the terrain. */
final class AtlasPlayerMarker {
    static final SpriteId PLAYER = new SpriteId(AtlasIds.MAP_DECORATIONS, Identifier.withDefaultNamespace("player"));
    static final SpriteId OTHER = new SpriteId(AtlasIds.MAP_DECORATIONS, Identifier.withDefaultNamespace("frame"));
    private static final int HEADINGS = 16;
    static double[] direction(boolean globe, double u, double v, double yaw, double pitch, double degrees) {
        double angle = Math.toRadians(degrees);
        double dx = -Math.sin(angle), dy = Math.cos(angle);
        if (globe) {
            double[] from = AtlasMapMath.globe(u, v, yaw, pitch);
            double[] to = AtlasMapMath.globe(u + dx * 1e-5, v + dy * 1e-5, yaw, pitch);
            dx = to[0] - from[0]; dy = from[1] - to[1];
        }
        double length = Math.hypot(dx, dy);
        return length < 1e-12 ? new double[]{0, -1} : new double[]{dx / length, dy / length};
    }

    /** Use the view actually displayed by the texture, including on throttled frames. */
    static double[] project(AtlasSnapshot data, AtlasMapRaster.View view, double x, double z) {
        if (view.globe()) {
            double[] point = AtlasMapMath.globe((x - data.minX()) / data.span(),
                    (z - data.minZ()) / data.span(), view.yaw(), view.pitch());
            if (point[2] <= 0) return null;
            double radius = Math.max(8, Math.min(view.width(), view.height()) * .5 - 4) * view.scale();
            return new double[]{view.width() * .5 + point[0] * radius, view.height() * .5 - point[1] * radius};
        }
        if (data.wrapped()) {
            x = AtlasMapMath.canonical(x, data.minX(), data.span());
            z = AtlasMapMath.canonical(z, data.minZ(), data.span());
        }
        return new double[]{view.width() * .5 + (x - view.centerX()) * view.scale(),
                view.height() * .5 + (z - view.centerZ()) * view.scale()};
    }

    /** Vanilla's 16 headings; the original texture points north before rotation. */
    static int heading(double[] direction) {
        return Math.floorMod((int) Math.round(Math.atan2(direction[0], -direction[1]) * HEADINGS / (2 * Math.PI)), HEADINGS);
    }

    static void draw(GuiGraphicsExtractor g, int x, int y, double[] direction) {
        draw(g, x, y, direction, false);
    }

    static void draw(GuiGraphicsExtractor g, int x, int y, double[] direction, boolean other) {
        // Resolve the actual map-atlas sprite, including resource-pack replacements.
        // No copied artwork, tint, resampling, or reconstructed triangle.
        var sprite = g.getSprite(other ? OTHER : PLAYER);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate((float) (heading(direction) * Math.PI * 2 / HEADINGS));
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, -4, -4, 8, 8);
        g.pose().popMatrix();
    }
    private AtlasPlayerMarker() {}
}
