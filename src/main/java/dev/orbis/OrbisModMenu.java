package dev.orbis;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.toroidalworld.client.settings.SettingsScreen;

/** The engine screen uses Orbis-owned translations and the orbis-engine.json settings file. */
public final class OrbisModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
