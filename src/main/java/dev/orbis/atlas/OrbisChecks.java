package dev.orbis.atlas;

import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Operator-only, bounded generator checks; executes on the server thread without loading terrain. */
public final class OrbisChecks {
    private static final Map<MinecraftServer, Job> JOBS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, List<String>> RESULTS = new IdentityHashMap<>();
    private static final int STRUCTURE_LIMIT = 2048, TICK_LIMIT = 2400;
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("orbis").then(Commands.literal("check").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                    .executes(ctx -> start(ctx.getSource(), true, true))
                    .then(Commands.literal("biomes").executes(ctx -> start(ctx.getSource(), true, false)))
                    .then(Commands.literal("structures").executes(ctx -> start(ctx.getSource(), false, true)))
                    .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                    .then(Commands.literal("cancel").executes(ctx -> {
                        var job = JOBS.remove(ctx.getSource().getServer());
                        reply(ctx.getSource(), job == null ? "Kein Check läuft." : "Check abgebrochen."); return job == null ? 0 : 1;
                    })))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var job = JOBS.get(server);
            if (job == null) return;
            try {
                if (job.tick()) { JOBS.remove(server); RESULTS.put(server, List.copyOf(job.messages)); }
            } catch (RuntimeException failure) {
                JOBS.remove(server);
                reply(job.source, "Check fehlgeschlagen; keine Aussage über Vollständigkeit. Details im Serverlog.");
                dev.orbis.Orbis.LOGGER.error("Orbis server check failed", failure);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { JOBS.remove(server); RESULTS.remove(server); });
    }
    private static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("Orbis: " + message), false);
        // Keep a reviewable server record even when invoked from chat.
        dev.orbis.Orbis.LOGGER.info("Check: {}", message);
    }
    private static int start(CommandSourceStack source, boolean biomes, boolean structures) {
        var server = source.getServer();
        if (JOBS.containsKey(server)) { reply(source, "Ein Check läuft bereits. /orbis check status oder cancel"); return 0; }
        List<DimensionCheck> dimensions = new ArrayList<>();
        for (var key : List.of(Level.OVERWORLD, Level.NETHER)) {
            var level = server.getLevel(key);
            var shape = level == null ? AtlasShape.OPEN : AtlasShape.server(level);
            if (!shape.wrapped()) {
                reply(source, "Check benötigt eine endliche Orbis-Oberwelt und einen endlichen Nether."); return 0;
            }
            dimensions.add(new DimensionCheck(level, shape.minX(), shape.minZ(), shape.span(), biomes, structures));
        }
        JOBS.put(server, new Job(source, dimensions)); RESULTS.remove(server);
        reply(source, "Check gestartet: Oberwelt + Nether. Läuft im Hintergrund; Status mit /orbis check status.");
        return 1;
    }
    private static int status(CommandSourceStack source) {
        var job = JOBS.get(source.getServer());
        if (job != null) { var d = job.dimensions.get(job.index);
            reply(source, d.name + ": " + d.search.save().cursor() + " Biome-Abfragen, " + d.audit.checks() + " Struktur-Kandidaten.");
        } else {
            var lines = RESULTS.get(source.getServer());
            if (lines == null) reply(source, "Kein Check läuft. Start: /orbis check");
            else lines.forEach(line -> reply(source, line));
        }
        return 1;
    }
    private static final class Job {
        final CommandSourceStack source;
        final List<DimensionCheck> dimensions;
        final List<String> messages = new ArrayList<>();
        int index;
        Job(CommandSourceStack source, List<DimensionCheck> dimensions) { this.source = source; this.dimensions = dimensions; }
        boolean tick() {
            var current = dimensions.get(index);
            if (!current.tick()) return false;
            for (String line : current.result()) { messages.add(line); reply(source, line); }
            if (++index < dimensions.size()) return false;
            String note = "Fertig. Nicht gefunden bedeutet unbestätigt, nicht nachweislich fehlend. Strukturen: Generator-Vorhersage, keine Prüfung aller gespeicherten Blöcke.";
            messages.add(note); reply(source, note); return true;
        }
    }
    private static final class DimensionCheck {
        final String name;
        final boolean biomes, structures, generateStructures;
        final SortedSet<String> expected = new TreeSet<>(), found = new TreeSet<>();
        final AtlasBiomeSampler sampler;
        final AtlasSearch search;
        final StructureAudit audit;
        int ticks;
        DimensionCheck(ServerLevel level, int x, int z, int span, boolean biomes, boolean structures) {
            name = level.dimension().equals(Level.OVERWORLD) ? "Oberwelt" : "Nether";
            this.biomes = biomes; this.structures = structures;
            generateStructures = level.structureManager().shouldGenerateStructures();
            var source = level.getChunkSource().getGenerator().getBiomeSource();
            source.possibleBiomes().forEach(b -> expected.add(b.unwrapKey().orElseThrow().identifier().toString()));
            sampler = AtlasBiomeSampler.create(source, level.getChunkSource().randomState());
            search = new AtlasSearch(x,z,span,level.getMinY(),level.getMaxY(),
                    new int[]{level.dimensionType().hasCeiling() ? 32 : level.getSeaLevel(), level.getMinY()+16,32,128}, biomes);
            search.restore(new AtlasSearch.Saved(0, AtlasSearch.MAX, biomes,0,256,false));
            audit = new StructureAudit(level,true,x,z,span);
            if (structures && generateStructures) audit.startQuick();
        }
        boolean tick() {
            long deadline = System.nanoTime() + 3_000_000;
            if (biomes) search.search(Math.min(deadline, System.nanoTime()+1_500_000), () -> true,
                    () -> found.containsAll(expected), sampler::query, (x,y,z,id) -> found.add(id));
            if (structures && generateStructures && audit.checks() < STRUCTURE_LIMIT && !audit.finished() && !StructureAudit.allFound(audit.entries())) {
                if (!audit.running()) audit.startQuick();
                audit.tick(deadline, () -> true);
            }
            boolean biomeDone = !biomes || found.containsAll(expected) || search.save().cursor() >= AtlasSearch.MAX;
            boolean structureDone = !structures || !generateStructures || audit.finished() || audit.checks() >= STRUCTURE_LIMIT || StructureAudit.allFound(audit.entries());
            return ++ticks >= TICK_LIMIT || biomeDone && structureDone;
        }
        List<String> result() {
            var lines = new ArrayList<String>();
            if (biomes) { var missing = new TreeSet<>(expected); missing.removeAll(found);
                lines.add(name + " – Biome: " + (expected.size()-missing.size()) + "/" + expected.size() + " gefunden.");
                appendMissing(lines, name + " – Biome nicht gefunden: ", missing);
            }
            if (structures) {
                var entries = audit.entries();
                var missing = entries.stream().filter(e -> !e.found()).map(StructureAudit.Entry::id).toList();
                lines.add(name + " – Strukturen: " + (entries.size()-missing.size()) + "/" + entries.size() + " gefunden."
                        + (generateStructures ? "" : " Strukturgenerierung ist deaktiviert."));
                appendMissing(lines, name + " – Strukturen nicht gefunden: ", missing);
                if (audit.state().startsWith("Check stopped:")) lines.add(name + " – " + audit.state());
            }
            if (ticks >= TICK_LIMIT) lines.add(name + " – Zeitbudget erreicht; Check unvollständig.");
            return lines;
        }
    }
    static void appendMissing(List<String> lines, String prefix, Collection<String> missing) {
        var batch = new ArrayList<String>();
        for (var id : missing) { batch.add(id); if (batch.size() == 5) { lines.add(prefix + String.join(", ", batch)); batch.clear(); } }
        if (!batch.isEmpty()) lines.add(prefix + String.join(", ", batch));
    }
    private OrbisChecks() {}
}
