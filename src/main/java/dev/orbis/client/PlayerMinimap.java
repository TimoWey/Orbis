package dev.orbis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Local terrain only: no server access, seed, surveys, structure search or chunk requests. */
public final class PlayerMinimap implements ClientModInitializer {
    private static final int PIXELS = 64, BLOCKS_PER_PIXEL = 2;
    private static final int UNKNOWN = 0xff202832;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("orbis", "player_minimap");
    private final int[] pixels = new int[PIXELS * PIXELS];
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private DynamicTexture texture;
    private boolean visible = true, dirty;
    private int originX, originZ, ticks;

    @Override public void onInitializeClient() {
        var map = new PlayerMinimap();
        var key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orbis.minimap",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("orbis", "minimap"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) map.visible = !map.visible;
            map.tick(client);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, TEXTURE,
                (graphics, delta) -> map.draw(graphics));
    }

    private void tick(Minecraft client) {
        if (client.level != level) {
            close(client);
            level = client.level;
            ticks = 0;
        }
        if (!visible || level == null || client.player == null) return;
        // A fixed 4096-column budget, five times per second, independent of world size.
        if (ticks++ % 4 != 0) return;
        originX = Math.floorDiv(client.player.getBlockX(), BLOCKS_PER_PIXEL) * BLOCKS_PER_PIXEL - PIXELS;
        originZ = Math.floorDiv(client.player.getBlockZ(), BLOCKS_PER_PIXEL) * BLOCKS_PER_PIXEL - PIXELS;
        for (int z = 0; z < PIXELS; z++) for (int x = 0; x < PIXELS; x++) {
            int wx = originX + x * BLOCKS_PER_PIXEL, wz = originZ + z * BLOCKS_PER_PIXEL;
            // false is essential: missing terrain stays unknown rather than loading anything.
            var chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
            int color = UNKNOWN;
            if (chunk != null) {
                boolean ceiling = level.dimensionType().hasCeiling();
                int y = ceiling ? Math.clamp(32, chunk.getMinY(), chunk.getMaxY())
                        : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
                for (int depth = 0; depth < (ceiling ? 1 : 16) && y >= chunk.getMinY(); depth++, y--) {
                    pos.set(wx, y, wz);
                    var mapColor = chunk.getBlockState(pos).getMapColor(chunk, pos);
                    if (mapColor != MapColor.NONE) { color = 0xff000000 | mapColor.col; break; }
                }
            }
            pixels[z * PIXELS + x] = color;
        }
        dirty = true;
    }

    private void draw(GuiGraphicsExtractor g) {
        var client = Minecraft.getInstance();
        if (!visible || level == null || level != client.level || client.player == null
                || client.gui.screen() != null || client.gui.hud.isHidden()) return;
        if (texture == null) {
            texture = new DynamicTexture(() -> "Orbis minimap", new NativeImage(PIXELS, PIXELS, false));
            client.getTextureManager().register(TEXTURE, texture);
            dirty = true;
        }
        if (dirty) {
            for (int z = 0; z < PIXELS; z++) for (int x = 0; x < PIXELS; x++)
                texture.getPixels().setPixel(x, z, pixels[z * PIXELS + x]);
            texture.upload();
            dirty = false;
        }
        int size = Math.max(32, Math.min(112, Math.min(g.guiWidth() / 3, g.guiHeight() - 60)));
        int left = g.guiWidth() - size - 12, top = 18;
        g.fill(left - 3, top - 3, left + size + 3, top + size + 3, 0xdd101820);
        g.blit(TEXTURE, left, top, left + size, top + size, 0, 1, 0, 1);
        double scale = size / (double) (PIXELS * BLOCKS_PER_PIXEL);
        int px = left + (int) ((client.player.getX() - originX) * scale);
        int pz = top + (int) ((client.player.getZ() - originZ) * scale);
        g.enableScissor(left, top, left + size, top + size);
        g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xff101820);
        g.fill(px - 1, pz - 1, px + 2, pz + 2, 0xffffffff);
        double yaw = Math.toRadians(client.player.getYRot());
        int dx = px - (int) (Math.sin(yaw) * 7), dz = pz + (int) (Math.cos(yaw) * 7);
        g.fill(dx - 1, dz - 1, dx + 2, dz + 2, 0xff70dfbd);
        g.disableScissor();
        label(g, "N", left + size / 2, top - 12);
        label(g, client.player.getBlockX() + " / " + client.player.getBlockY() + " / " + client.player.getBlockZ(),
                left + size / 2, top + size + 6);
    }

    private static void label(GuiGraphicsExtractor g, String text, int center, int y) {
        var font = Minecraft.getInstance().font;
        g.text(font, text, center - font.width(text) / 2, y, 0xffffffff, true);
    }

    private void close(Minecraft client) {
        if (texture != null) client.getTextureManager().release(TEXTURE);
        texture = null;
        java.util.Arrays.fill(pixels, UNKNOWN);
        dirty = false;
    }
}
