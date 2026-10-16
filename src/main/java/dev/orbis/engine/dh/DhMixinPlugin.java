package dev.orbis.engine.dh;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The Distant Horizons mixins apply only with the DH line they were written against (3.3), which reaches into DH's
 * internals: DH has no public way to repeat its terrain. Any other DH runs untouched, without seams.
 */
public final class DhMixinPlugin implements IMixinConfigPlugin {
    private boolean apply;

    @Override public void onLoad(String mixinPackage) {
        var mod = FabricLoader.getInstance().getModContainer("distanthorizons");
        String version = mod.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null);
        // ORBIS_DH_TORUS=off leaves DH alone, to compare against.
        apply = version != null && version.startsWith("3.3.") && !"off".equals(System.getenv("ORBIS_DH_TORUS"));
        if (version != null && !apply)
            // Not Orbis's own logger: loading that class here would load game classes before their mixins.
            LoggerFactory.getLogger("orbis").warn("Distant Horizons {} is untested with Orbis; its far terrain will not continue across the world's edges", version);
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return apply; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
