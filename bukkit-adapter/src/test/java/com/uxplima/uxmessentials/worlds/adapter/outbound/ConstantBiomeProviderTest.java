package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Biome;
import org.bukkit.generator.WorldInfo;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class ConstantBiomeProviderTest {

    private static ServerMock server;

    // A real (Mock) world is a WorldInfo; the constant provider ignores it, but NullAway requires a
    // non-null handle, so we reuse one mock world across the suite rather than pass null.
    private static WorldInfo worldInfo;

    @BeforeAll
    static void startServer() {
        server = MockBukkit.mock();
        worldInfo = server.addSimpleWorld("constant-biome-test");
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void getBiomeReturnsTheFixedBiomeRegardlessOfCoordinates() {
        ConstantBiomeProvider provider = new ConstantBiomeProvider(Biome.DESERT);

        assertThat(provider.getBiome(worldInfo, 0, 0, 0)).isEqualTo(Biome.DESERT);
        assertThat(provider.getBiome(worldInfo, -512, 64, 9001)).isEqualTo(Biome.DESERT);
    }

    @Test
    void getBiomesReturnsASingletonListContainingTheFixedBiome() {
        ConstantBiomeProvider provider = new ConstantBiomeProvider(Biome.PLAINS);

        assertThat(provider.getBiomes(worldInfo)).containsExactly(Biome.PLAINS);
    }

    @Test
    void getBiomesIsCachedAndReturnsTheSameImmutableInstance() {
        ConstantBiomeProvider provider = new ConstantBiomeProvider(Biome.PLAINS);

        List<Biome> first = provider.getBiomes(worldInfo);
        List<Biome> second = provider.getBiomes(worldInfo);
        assertThat(first).isSameAs(second);
    }

    @Test
    void fromResolvesAKnownBiomeIdThroughTheRegistry() {
        ConstantBiomeProvider provider =
                ConstantBiomeProvider.from(BiomeId.of("minecraft:desert"), new RecordingLogger());

        assertThat(provider.getBiome(worldInfo, 0, 0, 0)).isEqualTo(Biome.DESERT);
    }

    @Test
    void fromFallsBackToPlainsAndWarnsOnceForAnUnknownBiomeId() {
        RecordingLogger logger = new RecordingLogger();

        ConstantBiomeProvider provider = ConstantBiomeProvider.from(BiomeId.of("minecraft:not_a_biome"), logger);

        assertThat(provider.getBiome(worldInfo, 0, 0, 0)).isEqualTo(Biome.PLAINS);
        // Resolution happens once at construction; reading the biome later must not warn again.
        provider.getBiome(worldInfo, 1, 1, 1);
        provider.getBiomes(worldInfo);
        assertThat(logger.warnings).hasSize(1);
        assertThat(logger.warnings.get(0)).contains("not_a_biome");
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
