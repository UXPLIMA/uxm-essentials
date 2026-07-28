package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Objects;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.adapter.outbound.permission.LuckPermsAccess;
import net.luckperms.api.LuckPermsProvider;
import org.jspecify.annotations.NullMarked;

/**
 * Chooses the {@link PlayerGroupSource} for the command gate. LuckPerms stays a soft dependency: the shared
 * {@link LuckPermsAccess} guard answers whether it is usable, and the {@code net.luckperms} symbols (via
 * {@link LuckPermsPlayerGroupSource}) are reached only past that guard, so a server without LuckPerms never resolves
 * those classes and falls back to {@link PlayerGroupSource#empty()}, every player gated through the {@code default}
 * command list.
 */
@NullMarked
public final class PlayerGroupSources {

    private PlayerGroupSources() {}

    /** The LuckPerms-backed source when LuckPerms is installed, otherwise the empty fallback. */
    public static PlayerGroupSource create(Server server) {
        Objects.requireNonNull(server, "server");
        if (!LuckPermsAccess.isPresent(server)) {
            return PlayerGroupSource.empty();
        }
        // Loading LuckPermsPlayerGroupSource (and thus the net.luckperms symbols) only happens past the
        // plugin-present guard, so a server without LuckPerms never resolves those classes.
        return new LuckPermsPlayerGroupSource(LuckPermsProvider.get());
    }
}
