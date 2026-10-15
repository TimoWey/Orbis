package dev.orbis.gametest;

import dev.orbis.OrbisSettingsScreen;
import dev.orbis.OrbisWorldShape;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The Create World screen as a player sees it: the World tab has a World Shape row, Enter on it picks Orbis,
 * Customize opens the Orbis size settings, and a Superflat world cannot be made an Orbis world.
 */
public final class WorldShapeClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen())));
        context.waitFor(mc -> mc.gui.screen() instanceof CreateWorldScreen, 600);
        context.runOnClient(mc -> screen(mc).children().stream().filter(TabNavigationBar.class::isInstance)
                .map(TabNavigationBar.class::cast).findFirst().orElseThrow().selectTab(1, false));
        context.waitTicks(5);
        SeamWorld.check(!context.computeOnClient(mc -> OrbisWorldShape.orbis()), "A fresh world started as Orbis");

        press(context, "gui.orbis.world_shape");
        SeamWorld.check(context.computeOnClient(mc -> OrbisWorldShape.orbis()), "Enter on World Shape did not pick Orbis");
        SeamWorld.check(context.computeOnClient(mc -> customize(mc).active),
                "Customize stays off for an Orbis world");
        context.takeScreenshot("create-world-shape-orbis");

        context.runOnClient(mc -> screen(mc).setFocused(customize(mc)));
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitFor(mc -> mc.gui.screen() instanceof OrbisSettingsScreen, 200);
        context.takeScreenshot("create-world-orbis-size");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(mc -> mc.gui.screen() instanceof CreateWorldScreen, 200);

        // Superflat has no noise to wrap: the row turns off and the world stays Minecraft's own.
        context.runOnClient(mc -> {
            WorldCreationUiState state = screen(mc).getUiState();
            var flat = state.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT);
            state.setWorldType(new WorldCreationUiState.WorldTypeEntry(flat));
        });
        context.waitTicks(5);
        SeamWorld.check(!context.computeOnClient(mc -> OrbisWorldShape.orbis()), "Superflat stayed an Orbis world");
        SeamWorld.check(!context.computeOnClient(mc -> widget(mc, "gui.orbis.world_shape", 0).active),
                "World Shape stays on for Superflat");
        context.takeScreenshot("create-world-superflat");
        context.setScreen(TitleScreen::new);
    }

    private static CreateWorldScreen screen(Minecraft mc) { return (CreateWorldScreen) mc.gui.screen(); }

    /** Focuses the widget whose label uses {@code key} and presses Enter, as a keyboard player would. */
    private static void press(ClientGameTestContext context, String key) {
        context.runOnClient(mc -> screen(mc).setFocused(widget(mc, key, 0)));
        context.getInput().pressKey(InputConstants.KEY_RETURN);
        context.waitTicks(5);
    }

    /** The Customize button beside World Shape. */
    private static AbstractWidget customize(Minecraft mc) {
        var widgets = new java.util.ArrayList<AbstractWidget>();
        widgets(screen(mc), widgets::add);
        return widgets.get(widgets.indexOf(widget(mc, "gui.orbis.world_shape", 0)) + 1);
    }

    private static AbstractWidget widget(Minecraft mc, String key, int index) {
        String label = net.minecraft.network.chat.Component.translatable(key).getString();
        var found = new java.util.ArrayList<AbstractWidget>();
        widgets(screen(mc), widget -> { if (widget.getMessage().getString().startsWith(label)) found.add(widget); });
        if (found.size() <= index) throw new AssertionError("No widget '" + label + "' #" + index + " on the World tab");
        return found.get(index);
    }

    private static void widgets(net.minecraft.client.gui.components.events.ContainerEventHandler parent,
            java.util.function.Consumer<AbstractWidget> visit) {
        for (var child : parent.children()) {
            if (child instanceof AbstractWidget widget) visit.accept(widget);
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) widgets(container, visit);
        }
    }
}
