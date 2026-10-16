package dev.orbis.engine.c2me.mixin;

import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.c2me.C2meTorus;
import dev.orbis.engine.c2me.C2meWorlds;
import dev.orbis.engine.mixin.ChunkMapAccessor;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** C2ME runs one chunk scheduler per chunk map; its world's torus is read from that map. */
@Pseudo
@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.TheChunkSystem", remap = false)
public abstract class TheChunkSystemMixin implements C2meTorus {
    @Shadow @Final private ChunkMap tacs;

    @Override public Torus orbis$torus() { return Tori.of(((ChunkMapAccessor) tacs).orbis$level()); }

    /** The scheduling manager's id is what C2ME's chunk locks carry; its type is C2ME's, so it is read reflectively. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void orbis$registerWorld(ChunkMap map, CallbackInfo ci) {
        try {
            var field = ((Object) this).getClass().getDeclaredField("schedulingManager");
            field.setAccessible(true);
            Object manager = field.get(this);
            C2meWorlds.register((int) manager.getClass().getMethod("getId").invoke(manager), map);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("C2ME's chunk system changed; Orbis cannot fold its chunk locks", failure);
        }
    }
}
