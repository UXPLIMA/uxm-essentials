package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.generator.ChunkGenerator;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class WorldGeneratorResolverTest {

    // Both generators resolve their biome through Registry.BIOME and the flat plan through
    // Material.matchMaterial, so a running (mock) server is required at construction time.
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static WorldGeneratorResolver resolver() {
        return new WorldGeneratorResolver(
                FlatLayerPlan.defaults(), BiomeId.of("plains"), BiomeId.of("plains"), new RecordingLogger());
    }

    @Test
    void resolvesTheVoidIdToAVoidChunkGenerator() {
        Optional<ChunkGenerator> resolved = resolver().resolve("void");

        assertThat(resolved).get().isInstanceOf(VoidChunkGenerator.class);
    }

    @Test
    void resolvesTheFlatIdCaseInsensitivelyToAFlatChunkGenerator() {
        Optional<ChunkGenerator> resolved = resolver().resolve("FLAT");

        assertThat(resolved).get().isInstanceOf(FlatChunkGenerator.class);
    }

    @Test
    void returnsEmptyForAnUnknownGeneratorId() {
        assertThat(resolver().resolve("nope")).isEmpty();
    }

    @Test
    void returnsEmptyForABlankId() {
        assertThat(resolver().resolve("")).isEmpty();
    }

    @Test
    void reusesTheSameGeneratorInstanceAcrossCalls() {
        WorldGeneratorResolver resolver = resolver();

        assertThat(resolver.resolve("void"))
                .get()
                .isSameAs(resolver.resolve("VOID").orElseThrow());
        assertThat(resolver.resolve("flat"))
                .get()
                .isSameAs(resolver.resolve("Flat").orElseThrow());
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String rendered = message;
            for (Object arg : args) {
                rendered = rendered.replaceFirst("\\{}", String.valueOf(arg));
            }
            warnings.add(rendered);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
