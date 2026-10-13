package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** {@code execute facing} turns toward the image of the spot nearest to the source. */
@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin {
    @Shadow @Final private Vec3 worldPosition;
    @Shadow @Final private ServerLevel level;

    @ModifyVariable(method = "facing(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/commands/CommandSourceStack;", at = @At("HEAD"), argsOnly = true)
    private Vec3 orbis$faceNearest(Vec3 pos) {
        return Fold.nearest(Tori.of(level), pos, worldPosition);
    }
}
