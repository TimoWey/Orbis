package dev.orbis.engine.mixin;

import net.minecraft.world.entity.ai.control.LookControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LookControl.class)
public interface LookControlAccess {
    @Accessor("wantedX") double orbis$wantedX();
    @Accessor("wantedX") void orbis$setWantedX(double x);
    @Accessor("wantedZ") double orbis$wantedZ();
    @Accessor("wantedZ") void orbis$setWantedZ(double z);
}
