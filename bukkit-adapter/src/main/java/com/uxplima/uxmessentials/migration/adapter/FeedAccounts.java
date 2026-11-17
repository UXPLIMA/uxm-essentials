package com.uxplima.uxmessentials.migration.adapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.jspecify.annotations.NullMarked;

/**
 * The accounts a live balance feed asks its economy about: every player this server holds data for, and every account
 * in the shared name list, which holds the players who only ever played on another server sharing the database.
 *
 * <p>Each comes with the name it last played under: the server's own record first, then the shared list. Only an
 * account neither knows by name goes by its uuid.
 */
@NullMarked
final class FeedAccounts {

    private final Server server;
    private final Supplier<List<PlayerName>> stored;

    /** @param stored every account in the shared name list; read once per feed, off the tick */
    FeedAccounts(Server server, Supplier<List<PlayerName>> stored) {
        this.server = Objects.requireNonNull(server, "server");
        this.stored = Objects.requireNonNull(stored, "stored");
    }

    /** Every account, once each, this server's players first. */
    Stream<Account> all() {
        Map<UUID, String> names = new HashMap<>();
        for (PlayerName row : stored.get()) {
            names.putIfAbsent(row.uuid(), row.name());
        }
        Set<UUID> seen = new HashSet<>();
        Stream<Account> here = Stream.of(server.getOfflinePlayers())
                .filter(player -> seen.add(player.getUniqueId()))
                .map(player -> new Account(player, nameOf(player, names)));
        // An account that never played here is named by the shared list, the record of the server it played on.
        Stream<Account> elsewhere = names.entrySet().stream()
                .filter(entry -> !seen.contains(entry.getKey()))
                .map(entry -> new Account(server.getOfflinePlayer(entry.getKey()), entry.getValue()));
        return Stream.concat(here, elsewhere);
    }

    private static String nameOf(OfflinePlayer player, Map<UUID, String> names) {
        String own = player.getName();
        if (own != null) {
            return own;
        }
        String known = names.get(player.getUniqueId());
        return known != null ? known : player.getUniqueId().toString();
    }

    /** One account and the name it goes by. */
    record Account(OfflinePlayer player, String name) {}
}
