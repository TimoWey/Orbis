package dev.orbis.atlas;

/** Symmetric sidebars keep the map at the centre of the complete GUI. */
record AtlasLayout(int left, int top, int right, int bottom, int panelX) {
    static AtlasLayout fit(int width, int height) {
        int left = Math.max(8, (width - 1180) / 2);
        int top = Math.max(12, (height - 660) / 2);
        int right = width - left;
        int side = Math.clamp((right - left) / 4, 80, 190);
        return new AtlasLayout(left, top, right, height - top, right - side);
    }
    int sideWidth() { return right - panelX; }
    int mapLeft() { return left + sideWidth() + 8; }
    int mapRight() { return panelX - 8; }
    int mapTop() { return top + 28; }
    int mapBottom() { return bottom - 28; }
    int controlWidth() { return (sideWidth() - 12) / 2; }
    int controlX(int index) { return left + 8 + (index % 2) * (controlWidth() + 4); }
    int controlTop(int index) { return top + 28 + (index / 2) * 22; }
    int controlBottom(int index) { return top + 74 + index * 22; }
}
