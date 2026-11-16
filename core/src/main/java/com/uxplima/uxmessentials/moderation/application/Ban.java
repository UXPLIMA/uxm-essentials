package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerTempbanned;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * {@code /ban <player> [reason]}: a permanent UUID ban. A permanent ban reuses the tempban row: it is a
 * {@link TempbanState.Active} whose expiry is so far out ({@link #PERMANENT_SPAN}) that the ban-on-login
 * listener's {@code isActiveAt} check stays true for any realistic reconnection, so the existing login
 * enforcement bars the player unchanged. The expiry is a far-future sentinel computed from the clock rather
 * than {@code Instant.MAX} so the value still fits the epoch-milli column the tempban row stores. An exempt
 * target is refused, and so is one whose running ban would outlast this one; on success an online target is kicked
 * immediately, {@code PlayerTempbanned} is published and the action is audit-logged with the {@code permanent}
 * label.
 *
 * <p>A permanent ban requires the unlimited duration tier: the requested span is the far-future
 * {@link #PERMANENT_SPAN}, so an actor whose {@code ban.maxduration} cap is finite has the ban reduced to that
 * cap (a timed ban) and is told {@code MOD_DURATION_CAPPED}. An actor with no cap (the default) keeps the
 * permanent ban. Unless the ban is silent ({@code /ban -s}), the staff broadcast announces it.
 *
 * <p>Address-strictness: under {@link AddressStrictness#STRICT} (opt-in; default {@link AddressStrictness#NORMAL}
 * is zero behaviour change) a successful UUID ban additionally IP-bans every address the target is known to
 * have connected from, so a banned account cannot return on a fresh one from the same connection. The fan-out
 * is fail-safe (if no IP is on record the UUID ban still succeeds) and never runs against an exempt target
 * (the exempt gate already refused them above; this is a second guard so STRICT never IP-bans an exempt
 * player's shared connection collaterally).
 */
public final class Ban {

    /**
     * The far-future span that makes a tempban effectively permanent without overflowing the stored expiry. A ban
     * stored with this span is a permanent one; read it back with {@link #endOf}, not by comparing dates.
     */
    public static final Duration PERMANENT_SPAN = Duration.ofDays(365_000L);

    private static final String PERMANENT_LABEL = "permanent";

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final SanctionDurationLimit limit;
    private final SanctionBroadcast broadcast;
    private final SanctionSync sync;
    private final AddressStrictness strictness;
    private final IpHistoryStore ipHistory;
    private final Clock clock;

    public Ban(
            ModerationRepository repository,
            Sanctions sanctions,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            SanctionDurationLimit limit,
            SanctionBroadcast broadcast,
            SanctionSync sync,
            AddressStrictness strictness,
            IpHistoryStore ipHistory,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.limit = Objects.requireNonNull(limit, "limit");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.strictness = Objects.requireNonNull(strictness, "strictness");
        this.ipHistory = Objects.requireNonNull(ipHistory, "ipHistory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * When {@code ban} ends, or empty for a permanent ban. Anything that shows a ban outside this package (the
     * published API, a placeholder) reads it here, so a permanent ban never surfaces as a date a thousand years
     * out.
     */
    public static Optional<Instant> endOf(TempbanState.Active ban) {
        return SanctionStrength.endOf(ban);
    }

    /** {@link #endOf(TempbanState.Active)} for a row of the active-ban list. */
    public static Optional<Instant> endOf(BanEntry ban) {
        Objects.requireNonNull(ban, "ban");
        return SanctionStrength.endOf(ban.issuedAt(), ban.until());
    }

    /**
     * Permanently ban {@code target}, or refuse when the target is exempt. A capped actor's ban is timed, and a ban
     * already in force that would outlast it is never cut short: the request is refused with
     * {@link ModerationError#LONGER_BAN_RUNNING} and the actor is told which ban is running.
     */
    public Result<TempbanState.Active, ModerationError> ban(
            PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            return refuseExempt(actor, actor, target, reason);
        }
        Instant now = clock.instant();
        TempbanState.Active ban = proposal(actor, reason, Optional.empty(), now);
        Predicate<TempbanState.Active> longer =
                GuardedWrite.outlasting(SanctionStrength.endOf(ban), SanctionStrength::endOf);
        return switch (GuardedWrite.storeBan(repository, target, ban, now, longer)) {
            case GuardedWrite.Kept<TempbanState.Active> kept -> {
                audit.tempbanned(actor, target, label(ban), false, SanctionStrength.HELD);
                // A capped permanent ban became a timed one; say so, or the refusal reads as a request to shorten.
                if (capped(ban)) {
                    notifier.send(actor, ModerationMessageKey.MOD_DURATION_CAPPED, capLabel(spanOf(ban)));
                }
                GuardedWrite.tellBanKept(notifier, actor, target, kept.running(), now);
                yield Result.err(ModerationError.LONGER_BAN_RUNNING);
            }
            case GuardedWrite.Stored<TempbanState.Active> stored -> {
                announce(actor, actor, target, ban, silent, now);
                yield Result.ok(ban);
            }
        };
    }

    /**
     * Leave {@code target} banned at least as long as a ban by {@code actor} would: the warn escalation's way
     * in. A timed ban already in force is replaced by this one (stored, recorded, announced and synced like any
     * ban); a permanent ban already in force, or a timed one that outlasts what {@code actor}'s tier allows, is
     * kept and the rung is audited as held. The ban is issued under {@code actor}, the server rule, while what the
     * use case says about it goes to {@code told}, the staff member whose warning set it off. A {@code drawn} reason is
     * the catalog line {@code reason} was drawn from for the target, stored with the ban (and the addresses a STRICT
     * ban reaches) so every reader reads it in their own language.
     */
    Result<EscalationOutcome, ModerationError> escalate(
            PlayerRef actor,
            PlayerRef told,
            PlayerRef target,
            Optional<String> reason,
            Optional<DrawnReason> drawn,
            boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(told, "told");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            return refuseExempt(actor, told, target, reason);
        }
        Instant now = clock.instant();
        TempbanState.Active ban = proposal(actor, reason, drawn, now);
        Predicate<TempbanState.Active> atLeastAsLong =
                GuardedWrite.notOutlastedBy(SanctionStrength.endOf(ban), SanctionStrength::endOf);
        return switch (GuardedWrite.storeBan(repository, target, ban, now, atLeastAsLong)) {
            case GuardedWrite.Kept<TempbanState.Active> kept -> {
                audit.tempbanned(actor, target, label(ban), false, SanctionStrength.HELD);
                yield Result.ok(EscalationOutcome.ALREADY_STRONGER);
            }
            case GuardedWrite.Stored<TempbanState.Active> stored -> {
                announce(actor, told, target, ban, silent, now);
                yield Result.ok(stored.replacedRunning() ? EscalationOutcome.EXTENDED : EscalationOutcome.APPLIED);
            }
        };
    }

    private <T> Result<T, ModerationError> refuseExempt(
            PlayerRef actor, PlayerRef told, PlayerRef target, Optional<String> reason) {
        audit.tempbanned(actor, target, PERMANENT_LABEL, false, reason);
        notifier.send(told, ModerationError.TARGET_EXEMPT.messageKey());
        return Result.err(ModerationError.TARGET_EXEMPT);
    }

    /** The ban {@code actor} may issue: permanent, or a timed ban of their tier's length when it caps bans. */
    private TempbanState.Active proposal(
            PlayerRef actor, Optional<String> reason, Optional<DrawnReason> drawn, Instant now) {
        Duration effective = limit.cap(actor, "ban", PERMANENT_SPAN);
        return (TempbanState.Active) TempbanState.active(now.plus(effective), Issuer.of(actor), reason, now, drawn);
    }

    private static Duration spanOf(TempbanState.Active ban) {
        return Duration.between(ban.issuedAt(), ban.until());
    }

    private static boolean capped(TempbanState.Active ban) {
        return SanctionStrength.endOf(ban).isPresent();
    }

    private static String label(TempbanState.Active ban) {
        return capped(ban) ? SanctionDuration.format(spanOf(ban)) : PERMANENT_LABEL;
    }

    /** Everything a stored ban sets off: the history row, the kick, the event, the notices and the IP fan-out. */
    private void announce(
            PlayerRef actor, PlayerRef told, PlayerRef target, TempbanState.Active ban, boolean silent, Instant now) {
        boolean capped = capped(ban);
        Duration effective = spanOf(ban);
        Instant expiry = ban.until();
        Optional<String> reason = ban.reason();
        Optional<DrawnReason> drawn = ban.drawnReason();
        history.ban(actor, target, reason, drawn, capped ? Optional.of(expiry) : Optional.empty(), now);
        BanScreen.of(ban, now).kick(sanctions, notifier, target);
        events.publish(new PlayerTempbanned(target, ban, now));
        audit.tempbanned(actor, target, label(ban), true, reason);
        notifier.send(told, ModerationMessageKey.BAN_APPLIED, Map.of("player", target.name()));
        if (capped) {
            notifier.send(told, ModerationMessageKey.MOD_DURATION_CAPPED, capLabel(effective));
        }
        // The local kick above already removed the target here; this wakes peers that have them online so the
        // ban takes live effect cluster-wide rather than only on each peer's next login (a no-op with no bus). It
        // goes before the IP fan-out, whose guarded writes may give up and throw, so the stored ban always reaches
        // the peers.
        sync.banChanged(target);
        // A capped /ban lands a timed UUID ban, so its IP collateral must expire with it; a true permanent ban
        // (no cap) fans out permanent IP bans. Empty until = permanent, mirroring the UUID ban's own expiry.
        fanOutToIps(actor, told, target, reason, drawn, capped ? Optional.of(expiry) : Optional.empty(), now);
        if (!silent) {
            broadcast.announce(
                    broadcastKey(capped), broadcastPlaceholders(actor, target, reason, capped, effective), drawn);
        }
    }

    /**
     * Under STRICT, IP-ban every address the target is known to have connected from (the addresses the kernel
     * IP-history store retained while this module was enabled) so a banned account cannot return on a fresh one
     * from the same connection. Skipped for an exempt target (a defensive second
     * guard so STRICT never collaterally IP-bans an exempt player's shared connection) and fail-safe: an empty
     * IP set leaves the UUID ban as the only effect. The IP bans carry the UUID ban's own effective expiry
     * ({@code until}). Permanent when the ban is permanent, the capped expiry when a {@code maxduration} tier
     * reduced the ban to a tempban, so the collateral never outlives the sanction that triggered it.
     *
     * <p>An address already banned at least as long keeps its ban: the fan-out is collateral, so it only ever
     * lengthens an IP ban, and a capped {@code /ban} never turns a permanent {@code /banip} into a timed one. Each
     * address is written by compare and set, like the player ban, and only the addresses it banned are counted and
     * written to the history, as a {@code /banip} of that address naming the player; a kept address is only
     * audited as held, since its own ban is the one in force.
     */
    private void fanOutToIps(
            PlayerRef actor,
            PlayerRef told,
            PlayerRef target,
            Optional<String> reason,
            Optional<DrawnReason> drawn,
            Optional<Instant> until,
            Instant now) {
        if (!strictness.fansOutToIps() || guard.isExempt(target)) {
            return;
        }
        Set<String> ips = ipHistory.addressesOf(target.uuid());
        if (ips.isEmpty()) {
            return;
        }
        Optional<String> durationLabel = until.map(end -> SanctionDuration.format(Duration.between(now, end)));
        Predicate<IpBan> atLeastAsLong = GuardedWrite.notOutlastedBy(until, IpBan::until);
        int banned = 0;
        for (String ip : ips) {
            IpBan ipBan = new IpBan(ip, until, reason, Optional.of(target.uuid()), Issuer.of(actor), now, drawn);
            switch (GuardedWrite.store(GuardedWrite.ipBan(repository, ipBan), now, atLeastAsLong)) {
                case GuardedWrite.Kept<IpBan> kept ->
                    audit.ipBanned(actor, ip, Optional.of(target.uuid()), durationLabel, false, SanctionStrength.HELD);
                case GuardedWrite.Stored<IpBan> stored -> {
                    audit.ipBanned(actor, ip, Optional.of(target.uuid()), durationLabel, true, reason);
                    history.banIp(actor, Optional.of(target.uuid()), ip, reason, drawn, until, now);
                    banned++;
                }
            }
        }
        if (banned > 0) {
            notifier.send(told, ModerationMessageKey.MOD_BAN_STRICT_IP, Map.of("count", Integer.toString(banned)));
        }
    }

    private static MessageKey broadcastKey(boolean capped) {
        return capped ? ModerationMessageKey.MOD_BROADCAST_TEMPBAN : ModerationMessageKey.MOD_BROADCAST_BAN;
    }

    private static Map<String, String> broadcastPlaceholders(
            PlayerRef actor, PlayerRef target, Optional<String> reason, boolean capped, Duration effective) {
        if (capped) {
            return Map.of(
                    "actor", actor.name(),
                    "target", target.name(),
                    "reason", reason.orElse(""),
                    "duration", SanctionDuration.format(effective));
        }
        return Map.of("actor", actor.name(), "target", target.name(), "reason", reason.orElse(""));
    }

    private static Map<String, String> capLabel(Duration effective) {
        return Map.of("cap", SanctionDuration.format(effective));
    }
}
