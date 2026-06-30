package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PluginHook} for NBT-API: it integrates with the {@code NBTAPI} plugin and resolves to an
 * {@link NbtApplier}. It names {@link NbtApiService} only inside {@link #whenPresent}, and that service reaches
 * NBT-API purely by reflection; so on a server without NBT-API the service is never constructed and the SDK is
 * never loaded — {@code Hooks} hands callers the no-op {@link NbtApplier#ABSENT}, which carries no
 * {@code de.tr7zw} type.
 *
 * <p>Presence here is stricter than plugin-enabled alone: NBT-API must be enabled <em>and</em> its
 * {@code NBTItem} class must be loadable, so a present-but-broken install degrades to the safe no-op rather
 * than to a real applier that cannot reach the SDK.
 */
@NullMarked
public final class NbtApiHook implements PluginHook<NbtApplier> {

    private static final String PLUGIN_NAME = "NBTAPI";

    private final Logger log;

    public NbtApiHook(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public Class<NbtApplier> capability() {
        return NbtApplier.class;
    }

    @Override
    public NbtApplier whenAbsent() {
        return NbtApplier.ABSENT;
    }

    @Override
    public NbtApplier whenPresent(Server server) {
        Objects.requireNonNull(server, "server");
        // The single reference to the SDK-touching service, reached only past Hooks' present-guard.
        return new NbtApiService(log);
    }

    @Override
    public boolean isPresent(Server server) {
        Objects.requireNonNull(server, "server");
        return server.getPluginManager().isPluginEnabled(PLUGIN_NAME) && NbtApiService.nbtApiLoadable();
    }
}
