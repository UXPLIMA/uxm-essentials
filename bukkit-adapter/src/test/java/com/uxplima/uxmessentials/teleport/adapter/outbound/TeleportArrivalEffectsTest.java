package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TeleportArrivalEffectsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void playingAnEnabledEffectForAnOnlinePlayerDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        TeleportArrivalEffects effects = effects(Map.of(
                "arrival-effects",
                true,
                "effects.home.enabled",
                true,
                "effects.home.sound",
                "ENTITY_ENDERMAN_TELEPORT",
                "effects.home.particle",
                "END_ROD",
                "effects.home.particle-count",
                10,
                "effects.home.particle-spread",
                0.3));

        assertThatCode(() -> effects.arrived(ref(player), TeleportKind.HOME)).doesNotThrowAnyException();
    }

    @Test
    void aDisabledVerbIsANoOp() {
        PlayerMock player = server.addPlayer();
        TeleportArrivalEffects effects = effects(Map.of("arrival-effects", true));

        assertThatCode(() -> effects.arrived(ref(player), TeleportKind.HOME)).doesNotThrowAnyException();
    }

    @Test
    void anUnknownSoundOrParticleNamePlaysNothingRatherThanThrowing() {
        PlayerMock player = server.addPlayer();
        TeleportArrivalEffects effects = effects(Map.of(
                "arrival-effects",
                true,
                "effects.home.enabled",
                true,
                "effects.home.sound",
                "NOT_A_REAL_SOUND",
                "effects.home.particle",
                "NOT_A_REAL_PARTICLE"));

        assertThatCode(() -> effects.arrived(ref(player), TeleportKind.HOME)).doesNotThrowAnyException();
    }

    @Test
    void anOfflinePlayerIsANoOp() {
        TeleportArrivalEffects effects = effects(
                Map.of("arrival-effects", true, "effects.home.enabled", true, "effects.home.particle", "END_ROD"));

        assertThatCode(() -> effects.arrived(new PlayerRef(UUID.randomUUID(), "Ghost"), TeleportKind.HOME))
                .doesNotThrowAnyException();
    }

    private TeleportArrivalEffects effects(Map<String, Object> values) {
        TeleportSettings settings = new TeleportSettings(new FixedConfig(new HashMap<>(values)));
        return new TeleportArrivalEffects(server, settings, new InlineScheduler());
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }

    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Number n ? n.intValue() : fallback;
        }

        @Override
        public double getDouble(String path, double fallback) {
            return values.get(path) instanceof Number n ? n.doubleValue() : fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return fallback;
        }
    }
}
