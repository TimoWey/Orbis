package dev.orbis.engine.c2me.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.c2me.C2meWorlds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A C2ME generation step locks the chunks it may write, at their plain positions. A chunk past the seam is the chunk
 * on the other side of the world, so its lock is folded too: two steps that write the same chunk never run at once.
 */
@Pseudo
@Mixin(targets = "com.ishland.c2me.base.common.scheduler.LockTokenImpl", remap = false)
public abstract class LockTokenImplMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static long orbis$foldLock(long pos, @Local(argsOnly = true) int ownerTag) {
        return Fold.chunk(C2meWorlds.torus(ownerTag), pos);
    }
}
