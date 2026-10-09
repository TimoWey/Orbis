package dev.orbis.atlas;

/** One checklist row keeps its presentation and optional destination together. */
record AtlasRow(String label, String value, String tooltip, int color, AtlasSnapshot.Place place) {}
