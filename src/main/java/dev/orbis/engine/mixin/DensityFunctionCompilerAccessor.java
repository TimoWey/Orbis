package dev.orbis.engine.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DensityFunctionCompiler.class)
public interface DensityFunctionCompilerAccessor {
    @Accessor("context") DensityFunction.CompileContext orbis$context();
}
