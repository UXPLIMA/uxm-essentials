package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.FreezeSync;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Keeps a freeze the same on every backend. The frozen set is memory each backend holds on its own, keyed by player, so
 * a freeze made here is announced through {@link FreezeSync} and every backend that hears it holds the player too: one
 * who switches servers arrives frozen, and a release made anywhere lifts it everywhere.
 *
 * <p>Changes are ordered by when they were made. A peer's change older than the last one this backend applied or made
 * for that player is ignored, so a freeze and its release arriving out of order (the proxy and Redis both carry them)
 * cannot leave the player frozen. A copy of one change delivered twice acts once.
 *
 * <p>Runs on the command thread for a local change and on the bus lane for a peer's; the order and the frozen set are
 * safe on both.
 */
public final class NetworkFreeze {

    /** Far more players than are frozen or released between two changes to one of them. */
    private static final int REMEMBERED = 4096;

    private final Sanctions sanctions;
    private final FreezeSync sync;
    private final NetworkPresence presence;
    private final Notifier notifier;
    private final Clock clock;
    private final RecentIds applied = new RecentIds();
    private final Map<UUID, Instant> lastChange = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Instant> eldest) {
            return size() > REMEMBERED;
        }
    };

    public NetworkFreeze(
            Sanctions sanctions, FreezeSync sync, NetworkPresence presence, Notifier notifier, Clock clock) {
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Freeze ({@code frozen}) or release {@code target} here, and announce it to the network. */
    public void changeHere(PlayerRef target, boolean frozen) {
        Objects.requireNonNull(target, "target");
        Instant at = clock.instant();
        synchronized (lastChange) {
            lastChange.put(target.uuid(), at);
            apply(target, frozen);
        }
        sync.freezeChanged(target, frozen, at);
    }

    /**
     * A peer froze ({@code frozen}) or released {@code target} at {@code at}; {@code frame} tells copies of one change
     * apart. A player connected here is told, since the line they read on the other backend never reached them.
     */
    public void changedElsewhere(UUID frame, PlayerRef target, boolean frozen, Instant at) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(at, "at");
        if (!applied.firstTime(frame)) {
            return;
        }
        synchronized (lastChange) {
            Instant last = lastChange.get(target.uuid());
            if (last != null && at.isBefore(last)) {
                return;
            }
            lastChange.put(target.uuid(), at);
            apply(target, frozen);
        }
        boolean here = presence.find(target.uuid())
                .filter(online -> online.server().equals(presence.localServer()))
                .isPresent();
        if (here) {
            notifier.send(
                    target,
                    frozen ? ModerationMessageKey.FREEZE_NOTIFY_TARGET : ModerationMessageKey.UNFREEZE_NOTIFY_TARGET);
        }
    }

    private void apply(PlayerRef target, boolean frozen) {
        if (frozen) {
            sanctions.freeze(target);
        } else {
            sanctions.unfreeze(target);
        }
    }
}
