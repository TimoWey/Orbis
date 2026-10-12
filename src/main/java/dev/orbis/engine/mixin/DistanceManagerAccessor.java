package dev.orbis.engine.mixin;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.server.level.SimulationChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DistanceManager.class)
public interface DistanceManagerAccessor {
    @Accessor("loadingChunkTracker") LoadingChunkTracker orbis$loading();
    @Accessor("simulationChunkTracker") SimulationChunkTracker orbis$simulation();
    @Accessor("naturalSpawnChunkCounter") DistanceManager.FixedPlayerDistanceChunkTracker orbis$naturalSpawn();
    @Accessor("playerTicketManager") DistanceManager.PlayerTicketTracker orbis$playerTickets();
}
