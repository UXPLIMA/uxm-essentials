package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The single collaborator the ban/mute/warn/kick use cases call to append a {@link SanctionHistoryEntry} the
 * moment a sanction has been saved. Folding the mapping blocks into one focused class keeps each use case's
 * success path a single line ({@code history.ban(...)}) and the action→entry translation in one place.
 *
 * <p>Every method stamps the entry with the recorder's {@link Clock} at the call instant and builds the
 * {@link Issuer} from the acting staff {@link PlayerRef}. The temporary/permanent and IP distinctions are
 * carried by the entry's expiry and IP fields, not the {@link SanctionAction}.
 */
public final class SanctionHistoryRecorder {

    /** The nil UUID an IP ban with no resolved player records as its target. */
    private static final UUID NO_UUID = new UUID(0L, 0L);

    private final SanctionHistory store;
    private final Clock clock;

    public SanctionHistoryRecorder(SanctionHistory store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Record a UUID ban of {@code target} ({@code expiry} present for a tempban, empty for a permanent ban). */
    public void ban(PlayerRef actor, PlayerRef target, Optional<String> reason, Optional<Instant> expiry) {
        Objects.requireNonNull(target, "target");
        append(SanctionAction.BAN, target.uuid(), actor, reason, expiry, Optional.empty());
    }

    /** Record an IP ban ({@code target} the resolved player or empty for {@code /banip <ip>}). */
    public void banIp(
            PlayerRef actor, Optional<UUID> target, String ip, Optional<String> reason, Optional<Instant> expiry) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(ip, "ip");
        append(SanctionAction.BAN, target.orElse(NO_UUID), actor, reason, expiry, Optional.of(ip));
    }

    /** Record a UUID unban of {@code target}. */
    public void unban(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(target, "target");
        append(SanctionAction.UNBAN, target.uuid(), actor, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Record an IP unban of {@code ip} (no resolved player, so the target is the nil UUID). */
    public void unbanIp(PlayerRef actor, String ip) {
        Objects.requireNonNull(ip, "ip");
        append(SanctionAction.UNBAN, NO_UUID, actor, Optional.empty(), Optional.empty(), Optional.of(ip));
    }

    /** Record a mute of {@code target} ({@code expiry} present for a tempmute, empty for a permanent mute). */
    public void mute(PlayerRef actor, PlayerRef target, Optional<String> reason, Optional<Instant> expiry) {
        Objects.requireNonNull(target, "target");
        append(SanctionAction.MUTE, target.uuid(), actor, reason, expiry, Optional.empty());
    }

    /** Record an unmute of {@code target}. */
    public void unmute(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(target, "target");
        append(SanctionAction.UNMUTE, target.uuid(), actor, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Record a warning of {@code target} ({@code expiry} present for a tempwarn's lapse, empty for a standing warn). */
    public void warn(PlayerRef actor, PlayerRef target, Optional<String> reason, Optional<Instant> expiry) {
        Objects.requireNonNull(target, "target");
        append(SanctionAction.WARN, target.uuid(), actor, reason, expiry, Optional.empty());
    }

    /** Record a kick of {@code target} (a live disconnect: never a stored expiry, never IP-scoped). */
    public void kick(PlayerRef actor, PlayerRef target, Optional<String> reason) {
        Objects.requireNonNull(target, "target");
        append(SanctionAction.KICK, target.uuid(), actor, reason, Optional.empty(), Optional.empty());
    }

    /**
     * Record one kick per player of {@code targets} in one batch, every row stamped with the same instant: the rows
     * of one {@code /kickall} share it, which is how {@link PunishmentStats} counts them as one action.
     */
    public void kicks(PlayerRef actor, List<PlayerRef> targets, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(reason, "reason");
        Instant at = clock.instant();
        Issuer issuer = Issuer.of(actor);
        store.appendAll(targets.stream()
                .map(target -> new SanctionHistoryEntry(
                        SanctionAction.KICK, target.uuid(), issuer, reason, at, Optional.empty(), Optional.empty()))
                .toList());
    }

    private void append(
            SanctionAction action,
            UUID target,
            PlayerRef actor,
            Optional<String> reason,
            Optional<Instant> expiry,
            Optional<String> ip) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(expiry, "expiry");
        store.append(new SanctionHistoryEntry(action, target, Issuer.of(actor), reason, clock.instant(), expiry, ip));
    }
}
