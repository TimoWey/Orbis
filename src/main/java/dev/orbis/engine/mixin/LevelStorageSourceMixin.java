package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.LegacyWorldGen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Worlds from Orbis alphas before the engine switch keep their seed and size instead of opening as a default world. */
@Mixin(LevelStorageSource.class)
public abstract class LevelStorageSourceMixin {
    @WrapOperation(method = "readExistingSavedData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompoundTag;getCompoundOrEmpty(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;"))
    private static CompoundTag orbis$migrateLegacyWorldGen(CompoundTag file, String key, Operation<CompoundTag> original,
            @Local(argsOnly = true) SavedDataType<?> type) {
        CompoundTag data = original.call(file, key);
        if (type == WorldGenSettings.TYPE) LegacyWorldGen.migrate(data);
        return data;
    }
}
