package dev.orbis.debug;

/** All coordinates are Minecraft GUI units, after window/GUI scaling. */
record AtlasLayout(int left, int top, int right, int bottom, int panelX) {
    static AtlasLayout fit(int width, int height) {
        int left = Math.max(12, (width - Math.min(760, (int) (width * .86))) / 2);
        int top = Math.max(12, (height - Math.min(420, (int) (height * .84))) / 2);
        // At the minimum Minecraft GUI width keep room for two 40-unit map controls.
        left = Math.min(left, Math.max(8, (width - 280) / 2));
        int right = width - left;
        return new AtlasLayout(left, top, right, height - top,
                right - Math.clamp((right - left) / 3, 166, 210) - 8);
    }
    int mapLeft() { return left + 8; }
    int mapRight() { return panelX - 8; }
    boolean narrow() { return mapRight() - mapLeft() < 140; }
    int mapTop() { return top + (narrow() ? 72 : 50); }
    int mapBottom() { return bottom - (narrow() ? 64 : 42); }
    int columns() { return narrow() ? 2 : 4; }
    int controlWidth() { return (mapRight() - mapLeft() - (columns() - 1) * 4) / columns(); }
    int controlX(int index) { return mapLeft() + (index % columns()) * (controlWidth() + 4); }
    int controlTop(int index) { return top + 28 + (index / columns()) * 22; }
    int controlBottom(int index) { return bottom - (narrow() ? 44 : 22) + (index / columns()) * 22; }
}
