package dev.orbis.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.OrbisWorldShape;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The World Shape starts from the world being re-created, and shapes the world when it is created. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void orbis$startShape(CallbackInfo info, @Local(argsOnly = true) WorldCreationContext context) {
        OrbisWorldShape.reset(context.selectedDimensions());
    }

    @WrapOperation(method = "onCreate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContext;selectedDimensions()Lnet/minecraft/world/level/levelgen/WorldDimensions;"))
    private WorldDimensions orbis$shape(WorldCreationContext context, Operation<WorldDimensions> original) {
        return OrbisWorldShape.apply(original.call(context));
    }
}
