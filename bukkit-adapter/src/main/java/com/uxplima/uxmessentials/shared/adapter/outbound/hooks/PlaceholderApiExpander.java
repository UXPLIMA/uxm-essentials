package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import me.clip.placeholderapi.PlaceholderAPI;
import org.jspecify.annotations.NullMarked;

/**
 * The real {@link PlaceholderApiQuery}, backed by a present PlaceholderAPI. This is the one class in the hooks
 * package that imports {@code me.clip.placeholderapi}; it is referenced only from {@link PlaceholderApiHook}'s
 * present branch, so it is loaded only on a server that actually has PlaceholderAPI installed.
 */
@NullMarked
final class PlaceholderApiExpander implements PlaceholderApiQuery {

    private final Server server;

    PlaceholderApiExpander(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String expand(UUID viewer, String text) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(text, "text");
        OfflinePlayer player = server.getOfflinePlayer(viewer);
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
