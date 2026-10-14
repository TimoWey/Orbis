package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Section data kept beside the chunks (points of interest) lives once, at the folded section. A look-up past the seam
 * reads that section; positions inside it keep their place within the section, so they need no change.
 */
@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;

    @ModifyVariable(method = {"get", "getOrLoad", "getOrCreate"}, at = @At("HEAD"), argsOnly = true)
    private long orbis$foldSection(long section) {
        return levelHeightAccessor instanceof Level level ? Fold.section(Tori.of(level), section) : section;
    }
}
