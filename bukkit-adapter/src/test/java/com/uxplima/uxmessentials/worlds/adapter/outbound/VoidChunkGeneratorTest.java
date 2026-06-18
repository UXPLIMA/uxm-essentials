package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class VoidChunkGeneratorTest {

    private static ServerMock server;

    // getDefaultBiomeProvider takes a WorldInfo; the void generator ignores it, but NullAway needs a
    // non-null handle, so we reuse one mock world across the suite rather than pass null.
    private static WorldInfo worldInfo;

    @BeforeAll
    static void startServer() {
        server = MockBukkit.mock();
        worldInfo = server.addSimpleWorld("void-generator-test");
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static VoidChunkGenerator generator() {
        return new VoidChunkGenerator(new ConstantBiomeProvider(Biome.PLAINS));
    }

    @Test
    void suppressesEveryVanillaGenerationStage() {
        VoidChunkGenerator generator = generator();

        assertThat(generator.shouldGenerateNoise()).isFalse();
        assertThat(generator.shouldGenerateSurface()).isFalse();
        assertThat(generator.shouldGenerateBedrock()).isFalse();
        assertThat(generator.shouldGenerateCaves()).isFalse();
        assertThat(generator.shouldGenerateDecorations()).isFalse();
        assertThat(generator.shouldGenerateMobs()).isFalse();
        assertThat(generator.shouldGenerateStructures()).isFalse();
    }

    @Test
    void returnsTheInjectedBiomeProvider() {
        BiomeProvider injected = new ConstantBiomeProvider(Biome.DESERT);

        VoidChunkGenerator generator = new VoidChunkGenerator(injected);

        assertThat(generator.getDefaultBiomeProvider(worldInfo)).isSameAs(injected);
    }

    @Test
    void hasNoFixedSpawnLocationSoOperatorsSetItViaWorldsSetspawn() {
        VoidChunkGenerator generator = generator();

        assertThat(generator.getFixedSpawnLocation(server.addSimpleWorld("void-spawn-test"), new Random()))
                .isNull();
    }
}
