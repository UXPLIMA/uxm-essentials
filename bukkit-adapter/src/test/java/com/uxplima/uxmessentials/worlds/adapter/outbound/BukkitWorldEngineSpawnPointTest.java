package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Pins the engine's spawn-point read: a loaded world yields the {@link Position} of its live
 * {@code getSpawnLocation()}, and an unknown (unloaded) world name yields {@link Optional#empty()}.
 */
class BukkitWorldEngineSpawnPointTest {

    private ServerMock server;
    private BukkitWorldEngine engine;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        server.addSimpleWorld("w");
        engine = new BukkitWorldEngine(server, new NoOpLogger(), resolver());
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void loadedWorldYieldsItsSpawnPosition() {
        World world = server.getWorld("w");
        Location spawn = world.getSpawnLocation();

        Position position = engine.spawnPoint(WorldName.of("w")).orElseThrow();

        assertThat(position.x()).isEqualTo(spawn.getX());
        assertThat(position.y()).isEqualTo(spawn.getY());
        assertThat(position.z()).isEqualTo(spawn.getZ());
        assertThat(position.world().name()).isEqualTo("w");
    }

    @Test
    void unknownWorldYieldsEmpty() {
        assertThat(engine.spawnPoint(WorldName.of("missing"))).isEmpty();
    }

    private static WorldGeneratorResolver resolver() {
        return new WorldGeneratorResolver(
                FlatLayerPlan.defaults(), BiomeId.of("plains"), BiomeId.of("plains"), new NoOpLogger());
    }

    private static final class NoOpLogger implements Logger {
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
