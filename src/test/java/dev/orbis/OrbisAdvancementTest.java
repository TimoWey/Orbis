package dev.orbis;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbisAdvancementTest {
    @Test void cartographyProgressionDecodesWithMinecraftRegistryAndCriterionCodecs() throws Exception {
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        for (String name : new String[]{"bearings", "world_on_paper", "field_kit"}) {
            try (var stream = getClass().getResourceAsStream("/data/orbis/advancement/exploration/" + name + ".json")) {
                assertNotNull(stream);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var decoded = Advancement.CODEC.parse(ops, json);
                assertTrue(decoded.isSuccess(), () -> name + ": " + decoded.error());
                assertFalse(decoded.getOrThrow().criteria().isEmpty());
            }
        }
    }
}
