package com.uxplima.uxmessentials.worlds.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.ForceGamemodeListener;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.Test;

class WorldsWiringSmokeTest {

    @Test
    void settingsReadDefaultsTrueAndOverride() {
        ConfigStore defaults = new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                return fallback;
            }
        };
        WorldsSettings settings = new WorldsSettings(defaults);
        assertThat(settings.protectDefaultWorld()).isTrue();
        assertThat(settings.autoAdoptLoaded()).isTrue();
        assertThat(settings.autoLoadRegistered()).isTrue();

        ConfigStore off = new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return path.endsWith("protect-default-world") ? false : fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                return fallback;
            }
        };
        assertThat(new WorldsSettings(off).protectDefaultWorld()).isFalse();
    }

    @Test
    void wiredCarriesTheForceGamemodeListener() {
        List<Listener> listeners = List.of(new ForceGamemodeListener(new NoOpRepository(), new NoOpScheduler()));

        WorldsWiring.Wired wired = new WorldsWiring.Wired(List.of(), listeners, () -> {}, () -> {});

        assertThat(wired.listeners()).hasAtLeastOneElementOfType(ForceGamemodeListener.class);
    }

    private static final class NoOpRepository implements WorldRepository {
        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.empty();
        }

        @Override
        public List<ManagedWorld> all() {
            return List.of();
        }

        @Override
        public boolean exists(WorldName name) {
            return false;
        }

        @Override
        public void save(ManagedWorld world) {}

        @Override
        public void delete(WorldName name) {}
    }

    private static final class NoOpScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }
}
