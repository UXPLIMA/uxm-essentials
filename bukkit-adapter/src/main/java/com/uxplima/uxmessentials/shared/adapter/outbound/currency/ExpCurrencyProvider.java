package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * The {@code exp} back-end: a player's experience as a currency, native to Paper with no plugin behind it.
 * Balance is the player's total experience points, computed from level + progress (the same vanilla curve the
 * client shows) rather than the unreliable {@code getTotalExperience()} counter, which does not account for spent
 * levels. Deposit and withdraw move points by re-deriving the level and progress for the new total.
 *
 * <p>Experience lives on the online {@link Player}; an offline (or unknown) UUID has no experience to read or
 * change, so every operation on one is a safe no-op. The back-end itself is always {@link #available()} — it needs
 * no plugin — but acts only on online players. All access is on the calling (entity) thread, as the experience API
 * requires.
 */
final class ExpCurrencyProvider implements CurrencyProvider {

    private final String id;
    private final Server server;

    ExpCurrencyProvider(String id, Server server) {
        this.id = Objects.requireNonNull(id, "id");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public double balance(UUID player) {
        Objects.requireNonNull(player, "player");
        Player online = online(player);
        return online == null ? 0 : readTotal(online);
    }

    @Override
    public boolean has(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        Player online = online(player);
        return online != null && readTotal(online) >= points(amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        Player online = online(player);
        if (online == null) {
            return false;
        }
        int current = readTotal(online);
        long take = points(amount);
        if (current < take) {
            return false;
        }
        applyTotal(online, current - (int) take);
        return true;
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        Player online = online(player);
        if (online == null) {
            return false;
        }
        applyTotal(online, readTotal(online) + (int) points(amount));
        return true;
    }

    @Override
    public String format(double amount) {
        return CurrencyAmounts.plain(amount);
    }

    private @Nullable Player online(UUID player) {
        return server.getPlayer(player);
    }

    private static long points(double amount) {
        return Math.max(0, Math.round(amount));
    }

    /** Total experience points for {@code player}, summed from the per-level costs up to the current level. */
    private static int readTotal(Player player) {
        int level = player.getLevel();
        int total = 0;
        for (int l = 0; l < level; l++) {
            total += expToNext(l);
        }
        return total + Math.round(player.getExp() * expToNext(level));
    }

    /** Re-derive the level and within-level progress for {@code total} points and write them back. */
    private static void applyTotal(Player player, int total) {
        int remaining = Math.max(0, total);
        int level = 0;
        // Bounded against absurd inputs; a real experience total never approaches this many levels.
        while (level < 1_000_000 && remaining >= expToNext(level)) {
            remaining -= expToNext(level);
            level++;
        }
        player.setLevel(level);
        player.setExp((float) remaining / expToNext(level));
    }

    /** The points needed to advance from {@code level} to the next, per the vanilla experience curve. */
    private static int expToNext(int level) {
        if (level >= 31) {
            return 9 * level - 158;
        }
        if (level >= 16) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }
}
