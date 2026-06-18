package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.domain.BlockId;
import com.uxplima.uxmessentials.worlds.domain.FlatLayer;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class FlatChunkGeneratorTest {

    private static ServerMock server;
    private static World world;

    @BeforeAll
    static void startServer() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("flat-generator-test");
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static BiomeProvider provider() {
        return new ConstantBiomeProvider(Biome.PLAINS);
    }

    @Test
    void fromTranslatesTheDefaultPlanOncePerLayerIntoResolvedBlockData() {
        FlatChunkGenerator generator =
                FlatChunkGenerator.from(FlatLayerPlan.defaults(), provider(), new RecordingLogger());

        List<FlatChunkGenerator.ResolvedLayer> resolved = generator.resolvedPlan();
        assertThat(resolved).hasSize(3);
        assertThat(resolved.get(0).height()).isEqualTo(1);
        assertThat(resolved.get(0).block().getMaterial()).isEqualTo(Material.BEDROCK);
        assertThat(resolved.get(1).height()).isEqualTo(3);
        assertThat(resolved.get(1).block().getMaterial()).isEqualTo(Material.DIRT);
        assertThat(resolved.get(2).height()).isEqualTo(1);
        assertThat(resolved.get(2).block().getMaterial()).isEqualTo(Material.GRASS_BLOCK);
    }

    @Test
    void fromFallsBackToStoneAndWarnsOnceForAnUnknownBlockId() {
        RecordingLogger logger = new RecordingLogger();
        FlatLayerPlan plan = new FlatLayerPlan(List.of(new FlatLayer(BlockId.of("minecraft:not_a_block"), 2)));

        FlatChunkGenerator generator = FlatChunkGenerator.from(plan, provider(), logger);

        assertThat(generator.resolvedPlan()).hasSize(1);
        assertThat(generator.resolvedPlan().get(0).block().getMaterial()).isEqualTo(Material.STONE);
        assertThat(logger.warnings).hasSize(1);
        assertThat(logger.warnings.get(0)).contains("not_a_block");
    }

    @Test
    void suppressesEveryVanillaGenerationStageSoOnlyTheCustomBandsAreWritten() {
        FlatChunkGenerator generator =
                FlatChunkGenerator.from(FlatLayerPlan.defaults(), provider(), new RecordingLogger());

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
        BiomeProvider injected = provider();

        FlatChunkGenerator generator =
                FlatChunkGenerator.from(FlatLayerPlan.defaults(), injected, new RecordingLogger());

        assertThat(generator.getDefaultBiomeProvider(world)).isSameAs(injected);
    }

    @Test
    void generateNoiseWritesTheBandsBottomToTopFromMinHeight() {
        FlatChunkGenerator generator =
                FlatChunkGenerator.from(FlatLayerPlan.defaults(), provider(), new RecordingLogger());
        ChunkGenerator.ChunkData data = server.createChunkData(world);
        int min = data.getMinHeight();

        generator.generateNoise(world, new Random(), 0, 0, data);

        // bedrock×1, dirt×3, grass×1 (bottom→top), then air above.
        assertThat(data.getType(0, min, 0)).isEqualTo(Material.BEDROCK);
        assertThat(data.getType(8, min + 1, 8)).isEqualTo(Material.DIRT);
        assertThat(data.getType(15, min + 3, 15)).isEqualTo(Material.DIRT);
        assertThat(data.getType(0, min + 4, 0)).isEqualTo(Material.GRASS_BLOCK);
        assertThat(data.getType(0, min + 5, 0)).isEqualTo(Material.AIR);
    }

    @Test
    void generateNoiseClampsAnOverTallPlanToTheBuildHeight() {
        World tall = server.addSimpleWorld("flat-tall-test");
        ChunkGenerator.ChunkData data = server.createChunkData(tall);
        int span = data.getMaxHeight() - data.getMinHeight();
        FlatLayerPlan plan = new FlatLayerPlan(List.of(new FlatLayer(BlockId.of("minecraft:stone"), span + 64)));
        FlatChunkGenerator generator = FlatChunkGenerator.from(plan, provider(), new RecordingLogger());

        // Must not throw despite the plan exceeding the build height; the top is clamped.
        generator.generateNoise(tall, new Random(), 0, 0, data);

        assertThat(data.getType(0, data.getMinHeight(), 0)).isEqualTo(Material.STONE);
        assertThat(data.getType(0, data.getMaxHeight() - 1, 0)).isEqualTo(Material.STONE);
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
