package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * A read-only {@link ConfigStore} over one loaded Configurate {@link ConfigurationNode}, so the proxy can
 * reuse the {@code :core} {@code CommandControlConfig.from(ConfigStore)} parser verbatim instead of
 * re-implementing the config shape. Dotted paths ({@code command-spam.max-per-window}) are navigated by
 * splitting on {@code .} and descending through {@link ConfigurationNode#node(Object...)}; an absent or
 * virtual node yields the caller's fallback.
 *
 * <p>The proxy loads its config once on init and never hot-reloads it, so {@link #reload()} is the
 * inherited no-op and there is no {@code AtomicReference} swap; the node is captured at construction. The
 * default {@link ConfigStore#scoped(String)} view is used to root the store at {@code command-control}.
 */
public final class NodeConfigStore implements ConfigStore {

    private final ConfigurationNode root;

    public NodeConfigStore(ConfigurationNode root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        return at(path).getBoolean(fallback);
    }

    @Override
    public String getString(String path, String fallback) {
        return at(path).getString(fallback);
    }

    @Override
    public int getInt(String path, int fallback) {
        return at(path).getInt(fallback);
    }

    @Override
    public long getLong(String path, long fallback) {
        return at(path).getLong(fallback);
    }

    @Override
    public double getDouble(String path, double fallback) {
        return at(path).getDouble(fallback);
    }

    @Override
    public List<String> getStringList(String path, List<String> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        ConfigurationNode node = at(path);
        if (node.virtual() || !node.isList()) {
            return List.copyOf(fallback);
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    @Override
    public List<String> getKeys(String path) {
        ConfigurationNode node = at(path);
        if (node.virtual() || !node.isMap()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Object key : node.childrenMap().keySet()) {
            if (key != null) {
                keys.add(key.toString());
            }
        }
        return List.copyOf(keys);
    }

    private ConfigurationNode at(String path) {
        Objects.requireNonNull(path, "path");
        return root.node((Object[]) path.split("\\."));
    }
}
