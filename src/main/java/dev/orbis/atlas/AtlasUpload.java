package dev.orbis.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;

/** Writes a whole ARGB picture into a native RGBA image at once, instead of one bounds-checked call per pixel. */
final class AtlasUpload {
    static void copy(int[] argb, NativeImage image) {
        int count = image.getWidth() * image.getHeight();
        if (argb.length < count) throw new IllegalArgumentException("Picture smaller than its image");
        IntBuffer target = MemoryUtil.memIntBuffer(image.getPointer(), count);
        for (int i = 0; i < count; i++) {
            int c = argb[i];
            // ARGB to the image's ABGR: swap red and blue.
            target.put(i, c & 0xff00ff00 | c >> 16 & 0xff | (c & 0xff) << 16);
        }
    }
    private AtlasUpload() {}
}
