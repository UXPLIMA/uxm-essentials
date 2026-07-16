package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;

import org.bukkit.Server;

import net.luckperms.api.LuckPermsProvider;
import org.jspecify.annotations.NullMarked;

/**
 * Chooses the {@link ChatMetaSource} for the chat renderer, keeping LuckPerms a soft dependency exactly as the
 * kernel's {@code MetaSource} binding does: it probes the {@code Server} for the LuckPerms plugin and only reaches
 * the {@code net.luckperms} symbols (via {@link LuckPermsChatMetaSource}) past that guard, so a server without
 * LuckPerms never resolves those classes and falls back to {@link ChatMetaSource#empty()} — empty affixes and the
 * default (non-group) format.
 */
@NullMarked
public final class ChatMetaSources {

    private static final String LUCKPERMS = "LuckPerms";

    private ChatMetaSources() {}

    /** The LuckPerms-backed source when LuckPerms is installed, otherwise the empty fallback. */
    public static ChatMetaSource create(Server server) {
        Objects.requireNonNull(server, "server");
        if (server.getPluginManager().getPlugin(LUCKPERMS) == null) {
            return ChatMetaSource.empty();
        }
        // Loading LuckPermsChatMetaSource (and thus the net.luckperms symbols) only happens past the
        // plugin-present guard, so a server without LuckPerms never resolves those classes.
        return new LuckPermsChatMetaSource(LuckPermsProvider.get());
    }
}
