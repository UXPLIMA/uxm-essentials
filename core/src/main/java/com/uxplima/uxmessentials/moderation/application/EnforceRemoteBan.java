package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The live reaction to a ban issued on another backend: when a peer reports that a player's ban changed, this
 * re-evaluates the now-authoritative ban for that player <em>on this backend</em> and kicks them if it is in
 * effect and they are connected here. The durable enforcement is already free — every backend re-reads the
 * shared ban row on the next login — so this only closes the live gap: a player a peer already had online is
 * removed at once rather than lingering until they reconnect.
 *
 * <p>The re-evaluation mirrors the ban-on-login check ({@link LoginEnforcement}): it reads the fresh
 * {@link TempbanState} from the shared DB and, if {@link TempbanState.Active} and active now, renders the same
 * {@link ModerationMessageKey#TEMPBAN_KICK} screen and kicks. A still-online player whose ban turns out not to
 * be active (lifted between the frame and this read, or an out-of-order frame) is left alone — the DB, not the
 * frame, is the source of truth. The kick itself hops to the target's entity region thread inside the
 * {@link Sanctions} adapter, so this stays Folia-valid and never touches the Bukkit API from the bus thread.
 *
 * <p>Self-origin frames never reach here: the bus client drops any frame whose origin equals this backend's id
 * before dispatching to a listener, so the player the local {@code /ban} already kicked is not kicked twice.
 */
public final class EnforceRemoteBan {

    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Sanctions sanctions;
    private final ModerationNotifier notifier;
    private final Clock clock;

    public EnforceRemoteBan(
            ModerationRepository repository,
            PlayerLookup players,
            Sanctions sanctions,
            ModerationNotifier notifier,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Re-evaluate {@code target}'s ban here and kick them if it is in effect and they are connected. */
    public void onRemoteBan(UUID target) {
        Objects.requireNonNull(target, "target");
        if (!players.isOnline(target)) {
            // The ban is durable in the shared DB; an offline player is barred by the login check on reconnect.
            return;
        }
        Optional<PlayerRef> ref = players.findByUuid(target);
        ref.ifPresent(this::kickIfBanned);
    }

    private void kickIfBanned(PlayerRef target) {
        Instant now = clock.instant();
        if (repository.loadTempban(target) instanceof TempbanState.Active ban && ban.isActiveAt(now)) {
            MessageKey key = ModerationMessageKey.TEMPBAN_KICK;
            Map<String, String> ph = Map.of(
                    "duration", SanctionDuration.format(Duration.between(now, ban.until())),
                    "reason", ban.reason().orElse(""));
            sanctions.kick(target, key, notifier.render(target, key, ph));
        }
    }
}
