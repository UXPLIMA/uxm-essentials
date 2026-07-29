package com.uxplima.uxmessentials.bootstrap.health;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.NetworkConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.integration.Integration;
import com.uxplima.uxmessentials.shared.adapter.outbound.integration.IntegrationCatalog;
import com.uxplima.uxmessentials.shared.adapter.outbound.integration.IntegrationFamily;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Reports the optional integrations for {@code /uxmess doctor}: which soft-depend plugins are present, and
 * whether a dependency the operator configured is actually reachable. The present/absent lines are
 * informational; a <em>configured-but-absent</em> dependency is the warning case (the classic silent failure
 * where redis is enabled in config but the server cannot reach it, or the plugin is simply not installed).
 *
 * <p>The integrations line is derived from {@link IntegrationCatalog}, so every plugin we integrate with is
 * covered the moment it is catalogued and no shortlist can fall behind. Presence means installed <em>and</em>
 * enabled: a plugin that failed its own startup is not something we can call into. Redis is probed only when
 * the network bus is enabled with a Redis transport ({@code network.enabled = true} and {@code network.transport}
 * is {@code redis} or {@code both}): a short-timeout TCP connect to the configured {@code network.redis}
 * host/port decides reachable vs not. Aggregates to {@code WARN} when any configured dependency is unreachable,
 * {@code OK} otherwise.
 */
@NullMarked
public final class SoftDependencyHealthCheck implements HealthCheck {

    private static final int REDIS_PROBE_TIMEOUT_MILLIS = 750;

    private final PluginManager plugins;
    private final ConfigStore config;

    public SoftDependencyHealthCheck(PluginManager plugins, ConfigStore config) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public String name() {
        return "soft-dependencies";
    }

    @Override
    public HealthResult check() {
        List<String> notes = new ArrayList<>();
        notes.add(integrationSummary());
        boolean warn = appendRedis(notes);
        String summary = String.join(", ", notes);
        return warn ? HealthResult.warn(summary) : HealthResult.ok(summary);
    }

    /**
     * The integrations line: how many of the catalogued plugins this server actually has, then the present ones
     * named per family. Absent ones are counted rather than listed, since a server running none of the
     * thirty-odd optional plugins should not be told so thirty times.
     */
    private String integrationSummary() {
        int present = 0;
        List<String> families = new ArrayList<>();
        for (Map.Entry<IntegrationFamily, List<Integration>> family :
                IntegrationCatalog.byFamily().entrySet()) {
            List<String> installed = family.getValue().stream()
                    .map(Integration::plugin)
                    .filter(this::installed)
                    .toList();
            present += installed.size();
            if (!installed.isEmpty()) {
                families.add(family.getKey().label() + ": " + String.join(", ", installed));
            }
        }
        String counted = present + "/" + IntegrationCatalog.all().size() + " integrations present";
        return families.isEmpty() ? counted : counted + " (" + String.join("; ", families) + ")";
    }

    /** True when {@code pluginName} is installed and enabled: a plugin that failed its own startup is not usable. */
    private boolean installed(String pluginName) {
        Plugin plugin = plugins.getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    private boolean appendRedis(List<String> notes) {
        NetworkConfig network = NetworkConfig.from(config);
        if (!network.enabled() || !usesRedis(network.transport())) {
            return false;
        }
        String host = network.redis().host();
        int port = network.redis().port();
        if (redisReachable(host, port)) {
            notes.add("Redis reachable at " + host + ":" + port);
            return false;
        }
        notes.add("Redis configured but unreachable at " + host + ":" + port);
        return true;
    }

    private static boolean usesRedis(NetworkConfig.Transport transport) {
        return transport == NetworkConfig.Transport.REDIS || transport == NetworkConfig.Transport.BOTH;
    }

    private static boolean redisReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), REDIS_PROBE_TIMEOUT_MILLIS);
            return true;
        } catch (IOException unreachable) {
            return false;
        }
    }
}
