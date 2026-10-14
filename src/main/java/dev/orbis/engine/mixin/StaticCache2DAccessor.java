package dev.orbis.engine.mixin;

import net.minecraft.util.StaticCache2D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StaticCache2D.class)
public interface StaticCache2DAccessor {
    @Accessor("minX") int orbis$minX();
    @Accessor("minZ") int orbis$minZ();
    @Accessor("sizeX") int orbis$sizeX();
    @Accessor("sizeZ") int orbis$sizeZ();
}
