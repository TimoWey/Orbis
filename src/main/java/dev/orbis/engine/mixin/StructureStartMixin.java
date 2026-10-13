package dev.orbis.engine.mixin;

import dev.orbis.engine.StructureFrames;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(StructureStart.class)
public abstract class StructureStartMixin implements StructureFrames.Holder {
    @Unique private final Map<Long, StructureStart> orbis$images = new ConcurrentHashMap<>(2);

    @Override public Map<Long, StructureStart> orbis$images() { return orbis$images; }
}
