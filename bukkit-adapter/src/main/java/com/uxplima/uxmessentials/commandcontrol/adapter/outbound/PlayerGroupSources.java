package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Objects;

import org.bukkit.Server;

import net.luckperms.api.LuckPermsProvider;
import org.jspecify.annotations.NullMarked;

/**
 * Chooses the {@link PlayerGroupSource} for the command gate, keeping LuckPerms a soft dependency exactly as the chat
 * renderer's {@code ChatMetaSources} does: it probes the {@code Server} for the LuckPerms plugin and only reaches the
 * {@code net.luckperms} symbols (via {@link LuckPermsPlayerGroupSource}) past that guard, so a server without
 * LuckPerms never resolves those classes and falls back to {@link PlayerGroupSource#empty()} — every player gated
 * through the {@code default} command list.
 */
@NullMarked
public final class PlayerGroupSources {

    private static final String LUCKPERMS = "LuckPerms";

    private PlayerGroupSources() {}

    /** The LuckPerms-backed source when LuckPerms is installed, otherwise the empty fallback. */
    public static PlayerGroupSource create(Server server) {
        Objects.requireNonNull(server, "server");
        if (server.getPluginManager().getPlugin(LUCKPERMS) == null) {
            return PlayerGroupSource.empty();
        }
        // Loading LuckPermsPlayerGroupSource (and thus the net.luckperms symbols) only happens past the
        // plugin-present guard, so a server without LuckPerms never resolves those classes.
        return new LuckPermsPlayerGroupSource(LuckPermsProvider.get());
    }
}
