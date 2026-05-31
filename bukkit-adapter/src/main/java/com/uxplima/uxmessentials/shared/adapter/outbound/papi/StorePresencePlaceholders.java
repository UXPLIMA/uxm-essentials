package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link PresencePlaceholders} over the presence context's in-memory {@link PresenceStore}. Presence is
 * per-session state seeded on join and dropped on quit, so this seam reads the live store the AFK sweep and
 * the vanish lookups already share.
 *
 * <p>The AFK duration is measured from the player's last-activity stamp, which an AFK transition preserves —
 * so for an AFK player {@code now - lastActivity} is how long they have been idle. The read is via {@link
 * PresenceStore#current}, which never mutates the map; a player with no tracked presence (offline, or
 * presence not yet seeded) yields an empty snapshot.
 */
@NullMarked
public final class StorePresencePlaceholders implements PresencePlaceholders {

    private final PresenceStore store;
    private final Clock clock;

    public StorePresencePlaceholders(PresenceStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<Snapshot> snapshot(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        PlayerPresence presence = store.current(who);
        Duration afkFor = presence.afk() ? afkDuration(presence.lastActivity()) : Duration.ZERO;
        return Optional.of(new Snapshot(presence.afk(), afkFor, presence.vanished()));
    }

    private Duration afkDuration(Instant since) {
        Duration elapsed = Duration.between(since, clock.instant());
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }
}
