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
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The live side of a jail or release written on another backend: when the player is connected here, a jail is
 * applied at once (they are told, then put in the cell, here or on the jail's own backend) and a release teleports
 * them out, without waiting for their next join. The shared database is the source of truth: a jail frame acts only
 * on a sentence still in force, and a release frame only on a player who is no longer jailed, so a frame overtaken by
 * a later change does nothing. A frame delivered twice (the proxy and Redis both carry it) acts once.
 *
 * <p>Runs on the bus lane, off the tick: whether the player is here comes from network presence (a memory read), the
 * database read is safe there, and every move hops to the player's own thread.
 */
public final class ApplyRemoteJail {

    private final ModerationRepository repository;
    private final JailTransfer transfer;
    private final Sanctions sanctions;
    private final NetworkPresence network;
    private final Notifier notifier;
    private final Clock clock;
    private final RecentIds applied = new RecentIds();

    public ApplyRemoteJail(
            ModerationRepository repository,
            JailTransfer transfer,
            Sanctions sanctions,
            NetworkPresence network,
            Notifier notifier,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.network = Objects.requireNonNull(network, "network");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A peer jailed ({@code jailed}) or released {@code target}; {@code frame} tells copies of one change apart. */
    public void onJailChanged(UUID frame, UUID target, boolean jailed) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(target, "target");
        if (!applied.firstTime(frame)) {
            return;
        }
        Optional<PlayerRef> connected = network.find(target)
                .filter(online -> online.server().equals(network.localServer()))
                .map(OnlinePlayer::player);
        if (connected.isEmpty()) {
            return;
        }
        PlayerRef who = connected.get();
        JailState state = repository.loadJail(who);
        Instant now = clock.instant();
        if (jailed && state instanceof JailState.Active sentence && sentence.isActiveAt(now)) {
            notifier.send(who, ModerationMessageKey.JAIL_NOTIFY_TARGET, notice(who, sentence, now));
            transfer.send(who, sentence.jail());
        } else if (!jailed && !state.isActiveAt(now)) {
            sanctions.releaseFromJail(who);
        }
    }

    /** The placeholders of the line a player jailed on this backend reads, rebuilt from the stored sentence. */
    private Map<String, String> notice(PlayerRef who, JailState.Active sentence, Instant now) {
        String duration;
        if (sentence.remaining().isPresent()) {
            duration = SanctionDuration.format(sentence.remaining().get());
        } else if (sentence.until().isPresent()) {
            duration = SanctionDuration.format(
                    Duration.between(now, sentence.until().get()));
        } else {
            duration = notifier.render(who, ModerationMessageKey.MOD_GUI_VALUE_PERMANENT, Map.of());
        }
        return Map.of(
                "jail",
                sentence.jail(),
                "duration",
                duration,
                "reason",
                sentence.reason().orElse(""));
    }
}
