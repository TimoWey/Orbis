package dev.orbis.debug;

/** One checklist row keeps its presentation and optional destination together. */
record AtlasRow(String label, String value, String tooltip, int color, DebugSnapshot.Place place) {}
