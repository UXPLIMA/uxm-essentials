package com.uxplima.uxmessentials.bootstrap.health;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.PluginManager;

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
 * <p>PlaceholderAPI, Vault, and Treasury are detected by plugin presence. Redis is only probed when the economy
 * module has {@code redis.enabled = true}: a short-timeout TCP connect to the configured host/port decides
 * reachable vs not. Aggregates to {@code WARN} when any configured dependency is unreachable, {@code OK}
 * otherwise.
 */
@NullMarked
public final class SoftDependencyHealthCheck implements HealthCheck {

    private static final int REDIS_PROBE_TIMEOUT_MILLIS = 750;

    private final PluginManager plugins;
    private final ConfigStore economyConfig;

    public SoftDependencyHealthCheck(PluginManager plugins, ConfigStore economyConfig) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.economyConfig = Objects.requireNonNull(economyConfig, "economyConfig");
    }

    @Override
    public String name() {
        return "soft-dependencies";
    }

    @Override
    public HealthResult check() {
        List<String> notes = new ArrayList<>();
        notes.add("PlaceholderAPI " + presence("PlaceholderAPI"));
        notes.add("Vault " + presence("Vault"));
        notes.add("Treasury " + presence("Treasury"));
        boolean warn = appendRedis(notes);
        String summary = String.join(", ", notes);
        return warn ? HealthResult.warn(summary) : HealthResult.ok(summary);
    }

    private String presence(String pluginName) {
        return plugins.getPlugin(pluginName) != null ? "present" : "absent";
    }

    private boolean appendRedis(List<String> notes) {
        if (!economyConfig.getBoolean("redis.enabled", false)) {
            return false;
        }
        String host = economyConfig.getString("redis.host", "localhost");
        int port = economyConfig.getInt("redis.port", 6379);
        if (redisReachable(host, port)) {
            notes.add("Redis reachable at " + host + ":" + port);
            return false;
        }
        notes.add("Redis configured but unreachable at " + host + ":" + port);
        return true;
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
