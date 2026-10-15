package dev.orbis.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.OrbisSettingsScreen;
import dev.orbis.OrbisShape;
import dev.orbis.OrbisWorldShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a World Shape row to the World tab: Minecraft's own world or Orbis, and a button that opens the Orbis size
 * settings. World types whose Overworld or Nether is not noise-based (Superflat, Debug) cannot become Orbis worlds.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin {
    @Unique private CycleButton<Boolean> orbis$shape;
    @Unique private Button orbis$customize;

    @Inject(method = "<init>", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/layouts/CommonLayouts;labeledElement(Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/gui/layouts/LayoutElement;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/client/gui/layouts/Layout;"))
    private void orbis$addShapeRow(CallbackInfo info, @Local GridLayout.RowHelper row,
            @Local(argsOnly = true) CreateWorldScreen screen) {
        orbis$shape = row.addChild(CycleButton.<Boolean>builder(orbis -> label(orbis), OrbisWorldShape.orbis())
                .withValues(false, true)
                .withTooltip(orbis -> Tooltip.create(Component.translatable(key(orbis) + ".hint")))
                .create(0, 0, 150, 20, Component.translatable("gui.orbis.world_shape"), (button, orbis) -> {
                    OrbisWorldShape.orbis(orbis);
                    orbis$refresh();
                }));
        orbis$customize = row.addChild(Button.builder(Component.translatable("selectWorld.customizeType"), button -> {
            var minecraft = Minecraft.getInstance();
            minecraft.gui.setScreen(new OrbisSettingsScreen(minecraft.gui.screen(), OrbisWorldShape.size(), OrbisWorldShape::size));
        }).build());
        WorldCreationUiState state = screen.getUiState();
        state.addListener(this::orbis$followWorldType);
        orbis$followWorldType(state);
    }

    @Unique
    private void orbis$followWorldType(WorldCreationUiState state) {
        boolean fits = OrbisShape.fits(OrbisShape.strip(state.getSettings().selectedDimensions()));
        if (!fits && OrbisWorldShape.orbis()) {
            OrbisWorldShape.orbis(false);
            orbis$shape.setValue(false);
        }
        orbis$shape.active = fits;
        orbis$refresh();
    }

    @Unique
    private void orbis$refresh() { orbis$customize.active = orbis$shape.active && OrbisWorldShape.orbis(); }

    @Unique
    private static String key(boolean orbis) { return orbis ? "gui.orbis.world_shape.orbis" : "gui.orbis.world_shape.normal"; }

    @Unique
    private static Component label(boolean orbis) { return Component.translatable(key(orbis)); }
}
