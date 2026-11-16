package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is joining or connected to this server, which is when a player's data file belongs to the server: it reads the
 * file at the end of the login and writes it when the connection closes. The online player list alone misses a
 * player who is still being let in.
 *
 * <p>A login is <em>joining</em> from its pre-login until the server validates it. A pre-login can end without a
 * validation and without any later event (the connection drops before the server gets that far), so a joining mark
 * lapses after {@link #JOINING_FOR}. A validated login is <em>connected</em> until its connection closes, which the
 * server reports after it saved the player's data.
 *
 * <p>The server reports a connection closing by player, not by connection, and also for a login refused at
 * validation, which never counted. So a refusal there makes the next close of that player count for nothing, and
 * the player who is connected keeps counting. Which of the two closes is skipped does not matter: once both have
 * closed, nobody counts.
 *
 * <p>{@link #shared()} is the one record the plugin keeps, so a module reload does not forget who is in the middle of
 * a login.
 */
public final class PlayerPresence {

    /** How long a pre-login counts as joining without being validated; validation follows it within a tick. */
    static final Duration JOINING_FOR = Duration.ofSeconds(60);

    private static final PlayerPresence SHARED = new PlayerPresence(Clock.systemUTC());

    private final Clock clock;
    private final Map<UUID, Instant> joining = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> connected = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> uncounted = new ConcurrentHashMap<>();

    public PlayerPresence(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The record the running plugin uses. */
    public static PlayerPresence shared() {
        return SHARED;
    }

    /** A login of {@code player} passed its pre-login. */
    void joining(UUID player) {
        joining.put(player, clock.instant());
    }

    /** The server validated a login of {@code player}: they count until that connection closes. */
    public void validated(UUID player) {
        connected.merge(player, 1, Integer::sum);
        joining.remove(player);
    }

    /** A login of {@code player} was refused before it was validated. */
    public void refused(UUID player) {
        joining.remove(player);
    }

    /** A validation refused a login of {@code player}: its connection reports closing, but it never counted. */
    public void turnedAway(UUID player) {
        joining.remove(player);
        uncounted.merge(player, 1, Integer::sum);
    }

    /** One of {@code player}'s connections closed; a validated one is reported after the server saved their data. */
    public void closed(UUID player) {
        boolean[] skipped = {false};
        uncounted.computeIfPresent(player, (id, closes) -> {
            skipped[0] = true;
            return closes > 1 ? closes - 1 : null;
        });
        if (!skipped[0]) {
            connected.computeIfPresent(player, (id, connections) -> connections > 1 ? connections - 1 : null);
        }
    }

    /** {@code player} was already connected when this started; counting them twice is avoided. */
    public void adopt(UUID player) {
        connected.merge(Objects.requireNonNull(player, "player"), 1, Math::max);
    }

    /** Whether {@code player}'s file belongs to the server now. */
    public boolean present(UUID player) {
        if (connected.containsKey(player)) {
            return true;
        }
        Instant since = joining.get(player);
        if (since == null) {
            return false;
        }
        if (since.plus(JOINING_FOR).isAfter(clock.instant())) {
            return true;
        }
        joining.remove(player, since);
        return false;
    }
}
