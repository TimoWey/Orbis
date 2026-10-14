package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A player reaches the image of an entity nearest to them, so hits and clicks across the seam count on the server. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @ModifyVariable(method = {
            "isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z",
            "isWithinAttackRange(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/AABB;D)Z"},
            at = @At("HEAD"), argsOnly = true)
    private AABB orbis$nearestBox(AABB box) {
        Player self = (Player) (Object) this;
        return Fold.nearest(Tori.of(self.level()), box, self.getX(), self.getZ());
    }
}
