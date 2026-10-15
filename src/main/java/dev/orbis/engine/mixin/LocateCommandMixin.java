package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@code /locate} reports the distance the short way round; the coordinates it shows stay inside the world. */
@Mixin(LocateCommand.class)
public abstract class LocateCommandMixin {
    private static final String SHOW = "showLocateResult(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/BlockPos;"
            + "Lcom/mojang/datafixers/util/Pair;Ljava/lang/String;ZLjava/lang/String;Ljava/time/Duration;)I";

    @WrapOperation(method = SHOW, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private static double orbis$distSqr(BlockPos from, Vec3i to, Operation<Double> original, @Local(argsOnly = true) CommandSourceStack source) {
        Torus torus = Tori.of(source.getLevel());
        return original.call(from, torus == null ? to : Fold.nearest(torus, new BlockPos(to.getX(), to.getY(), to.getZ()), from.getX(), from.getZ()));
    }

    @WrapOperation(method = SHOW, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/LocateCommand;dist(IIII)F"))
    private static float orbis$dist(int x1, int z1, int x2, int z2, Operation<Float> original, @Local(argsOnly = true) CommandSourceStack source) {
        Torus torus = Tori.of(source.getLevel());
        if (torus == null) return original.call(x1, z1, x2, z2);
        double dx = torus.delta(x1, x2), dz = torus.delta(z1, z2);
        return Mth.sqrt((float) (dx * dx + dz * dz));
    }
}
