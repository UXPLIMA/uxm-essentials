package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarker;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerKind;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerPublisher;
import com.uxplima.uxmessentials.shared.application.mapmarker.MapMarkerSettings;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * The map-plugin discoverer's plugin-present guard: with neither Dynmap nor squaremap installed the integration
 * binds the no-op publisher and reads no map-plugin SDK, so the plugin runs fully without either map plugin —
 * the same soft-depend contract the economy and PlaceholderAPI adapters honour. Loading a real map plugin into
 * the test would pull in classes a stock server never has, so this exercises the absent-plugin path directly.
 */
class MapMarkerPublishersTest {

    @Test
    void anyPresent_isFalse_whenNoMapPluginInstalled() {
        Server server = serverWithoutMapPlugins();
        assertThat(MapMarkerPublishers.anyPresent(server)).isFalse();
    }

    @Test
    void discover_returnsNoOpPublisher_whenNoMapPluginInstalled() {
        Server server = serverWithoutMapPlugins();
        MapMarkerPublisher publisher = MapMarkerPublishers.discover(server, settings(), noOpLog());

        // The no-op publisher renders nothing and never touches a map-plugin SDK; calling it must not throw.
        publisher.publish(MapMarker.of(MapMarkerKind.WARP, "shop", "shop", "world", 1, 64, 2));
        publisher.publishAll(List.of());
        publisher.remove("warp:shop");
        publisher.clear();
        assertThat(publisher).isSameAs(MapMarkerPublisher.noOp());
    }

    private static Server serverWithoutMapPlugins() {
        Server server = mock(Server.class);
        PluginManager pm = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pm);
        when(pm.getPlugin("squaremap")).thenReturn(null);
        when(pm.getPlugin("dynmap")).thenReturn(null);
        return server;
    }

    private static MapMarkerSettings settings() {
        return MapMarkerSettings.from(new ConfigStore() {
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
        });
    }

    private static Logger noOpLog() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }
}
