package dev.orbis.engine.mixin;

import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The village distance map of a point-of-interest store knows its level's torus, for {@link SectionTrackerMixin}. */
@Mixin(targets = "net.minecraft.world.entity.ai.village.poi.PoiManager$DistanceTracker")
public abstract class PoiDistanceTrackerMixin implements TorusHolder {
    @Shadow(aliases = "this$0") @Final private PoiManager field_this;

    @Override
    public @Nullable Torus orbis$torus() { return ((TorusHolder) field_this).orbis$torus(); }

    @Override
    public void orbis$setTorus(@Nullable Torus torus) {}
}
