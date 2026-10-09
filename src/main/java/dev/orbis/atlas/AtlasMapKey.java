package dev.orbis.atlas;

/** Only layers consumed by a view participate in its texture cache key. */
record AtlasMapKey(String dimension, long seed, int minX, int minZ, int span, boolean wrapped,
        long terrain, long biomes, long loaded, long places, AtlasMapRaster.View view) {
    static AtlasMapKey of(AtlasSnapshot data, AtlasMapRaster.View view) {
        var layers = data.layers();
        // Flat views do not use orientation; globe views do not use planar centers.
        var effective = new AtlasMapRaster.View(view.width(), view.height(),
                view.globe() ? 0 : view.centerX(), view.globe() ? 0 : view.centerZ(), view.scale(),
                view.globe(), view.globe() ? view.yaw() : 0, view.globe() ? view.pitch() : 0,
                view.chunks(), view.places());
        return new AtlasMapKey(data.dimension(), data.seed(), data.minX(), data.minZ(), data.span(), data.wrapped(),
                layers.terrain(), 0, view.chunks() ? layers.loaded() : 0,
                view.places() ? layers.places() : 0, effective);
    }
}
