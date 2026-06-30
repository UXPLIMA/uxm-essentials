package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import org.bukkit.Server;

import org.jspecify.annotations.NullMarked;

/**
 * The worked example {@link PluginHook}: it integrates with PlaceholderAPI and resolves to a
 * {@link PlaceholderApiQuery}. It proves the pattern the real integrations (Vault, HeadDatabase, the item
 * providers) follow, and the {@code HookHarness} reuses it to test any hook's present/absent paths.
 *
 * <p>This class names {@link PlaceholderApiExpander} only inside {@link #whenPresent}, and that expander is
 * the single class that imports {@code me.clip.placeholderapi}. So when PlaceholderAPI is absent — and its
 * SDK is a {@code compileOnly} dependency, hence off the runtime and test classpaths — the expander is never
 * constructed and {@code me.clip.placeholderapi} is never loaded: no {@link NoClassDefFoundError} path exists.
 */
@NullMarked
public final class PlaceholderApiHook implements PluginHook<PlaceholderApiQuery> {

    private static final String PLUGIN_NAME = "PlaceholderAPI";

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public Class<PlaceholderApiQuery> capability() {
        return PlaceholderApiQuery.class;
    }

    @Override
    public PlaceholderApiQuery whenAbsent() {
        return PlaceholderApiQuery.ABSENT;
    }

    @Override
    public PlaceholderApiQuery whenPresent(Server server) {
        // The single reference to the SDK-touching class, reached only past Hooks' present-guard.
        return new PlaceholderApiExpander(server);
    }
}
