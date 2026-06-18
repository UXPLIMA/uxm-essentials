package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.generator.ChunkGenerator;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldGeneratorResolver;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Exercises the {@code getDefaultWorldGenerator} resolve logic through the testable static seam, so the
 * server.properties / foreign-plugin path serves {@code uxmEssentials:void|flat} and degrades to vanilla
 * (null) for unknown ids or when worlds is disabled (null resolver).
 */
class UxmEssentialsPluginGeneratorTest {

    // The resolver builds both generators against Registry.BIOME / Material at construction, so a (mock)
    // server must be running.
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
                FlatLayerPlan.defaults(), BiomeId.of("plains"), BiomeId.of("plains"), new NoopLogger());
    }

    @Test
    void servesTheResolverGeneratorForAKnownBuiltInId() {
        WorldGeneratorResolver resolver = resolver();

        ChunkGenerator gen = UxmEssentialsPlugin.resolveGenerator(resolver, "void");

        assertThat(gen).isSameAs(resolver.resolve("void").orElseThrow());
    }

    @Test
    void returnsNullForAnUnknownId() {
        assertThat(UxmEssentialsPlugin.resolveGenerator(resolver(), "nope")).isNull();
    }

    @Test
    void returnsNullForANullId() {
        assertThat(UxmEssentialsPlugin.resolveGenerator(resolver(), null)).isNull();
    }

    @Test
    void returnsNullWhenWorldsIsDisabled() {
        assertThat(UxmEssentialsPlugin.resolveGenerator(null, "void")).isNull();
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
