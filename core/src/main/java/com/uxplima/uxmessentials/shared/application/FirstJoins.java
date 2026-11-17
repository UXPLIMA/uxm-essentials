package com.uxplima.uxmessentials.shared.application;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.KnownAccounts;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * Whether a joining player is new to the whole network, not only to this server. A server knows only its own players,
 * so a player moving to a server they never played on would be greeted as new there; the shared name index knows
 * every account that joined any server of the database. It is read during the login, off the tick, before this join
 * adds the account to it, and the answer waits for the join.
 *
 * <p>A player this server knows is never new. When the index could not be read the player counts as known, since
 * greeting a returning player as new (and handing out a starter kit again) is the worse mistake. A player whose login
 * was not read (one already online when the plugin started) is judged by this server alone.
 */
public final class FirstJoins {

    private final KnownAccounts accounts;
    private final Logger log;
    private final ConcurrentHashMap<UUID, Boolean> knownAtLogin = new ConcurrentHashMap<>();

    public FirstJoins(KnownAccounts accounts, Logger log) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** A login was let in: read whether the network knows the account. Reads storage: call it off the tick. */
    public void loggingIn(UUID player) {
        Objects.requireNonNull(player, "player");
        boolean known;
        try {
            known = accounts.known(player);
        } catch (RuntimeException unreachable) {
            log.warn(
                    "event=first_join_unread player={} reason={}; the player counts as known",
                    player,
                    String.valueOf(unreachable.getMessage()));
            known = true;
        }
        knownAtLogin.put(player, known);
    }

    /** Whether {@code player}, who just joined, is new to the network; {@code playedHere} is this server's answer. */
    public boolean firstJoin(UUID player, boolean playedHere) {
        Objects.requireNonNull(player, "player");
        if (playedHere) {
            return false;
        }
        Boolean known = knownAtLogin.get(player);
        return known == null || !known;
    }

    /** The player left, or their login closed without a join. */
    public void forget(UUID player) {
        knownAtLogin.remove(Objects.requireNonNull(player, "player"));
    }
}
