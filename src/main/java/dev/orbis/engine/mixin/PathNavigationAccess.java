package dev.orbis.engine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigation.class)
public interface PathNavigationAccess {
    @Accessor("speedModifier") double orbis$speedModifier();
    @Accessor("targetPos") @Nullable BlockPos orbis$targetPos();
    @Accessor("targetPos") void orbis$setTargetPos(@Nullable BlockPos pos);
}
