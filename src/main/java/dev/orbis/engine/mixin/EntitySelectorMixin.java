package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A selector's box ({@code dx}/{@code dy}/{@code dz}, or the one {@code distance} builds) reaching past the seam
 * matches the entities there: each is tested at its image nearest to the box. The search itself already looks past
 * the seam, and {@code distance} uses the torus distance.
 */
@Mixin(EntitySelector.class)
public abstract class EntitySelectorMixin {
    @WrapMethod(method = "getPredicate")
    private Predicate<Entity> orbis$boxOnTorus(Vec3 pos, @Nullable AABB box, @Nullable FeatureFlagSet features,
                                               Operation<Predicate<Entity>> original) {
        Predicate<Entity> usual = original.call(pos, box, features);
        if (box == null) return usual;
        Predicate<Entity> withoutBox = original.call(pos, null, features);
        double centreX = (box.minX + box.maxX) / 2, centreZ = (box.minZ + box.maxZ) / 2;
        return entity -> {
            Torus torus = Tori.of(entity.level());
            if (torus == null) return usual.test(entity);
            return withoutBox.test(entity) && box.intersects(Fold.nearest(torus, entity.getBoundingBox(), centreX, centreZ));
        };
    }
}
