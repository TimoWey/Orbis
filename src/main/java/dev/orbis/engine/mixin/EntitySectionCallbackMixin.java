package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** An entity briefly past the seam stays in the section of its folded position instead of moving out and back. */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public abstract class EntitySectionCallbackMixin {
    @Shadow @Final private EntityAccess entity;

    @ModifyExpressionValue(method = "onMove", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J"))
    private long orbis$foldedSection(long section) {
        return entity instanceof Entity moving ? Fold.section(Tori.of(moving.level()), section) : section;
    }
}
