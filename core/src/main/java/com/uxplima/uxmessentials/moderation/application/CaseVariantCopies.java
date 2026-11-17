package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.CaseCopies;
import com.uxplima.uxmessentials.moderation.application.port.JailSync;
import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.event.ModerationEvent;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerMuted;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnjailed;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnmuted;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The mutes and jails copied onto other spellings of a name on an offline-mode server, from the copy to its end.
 *
 * <p>{@link #carryOver} gives a joining account the mute and the jail {@link CaseVariantSanctions} says it inherits,
 * unless it has an active one of its own. A copy is an ordinary sanction on the account, so every check reads it as it
 * reads any other, and every copy is kept in {@link CaseCopies} with the sanction it came from. A copied mute reads
 * "inherited from" its original holder in its reason and in the history.
 *
 * <p>{@link #onEvent} keeps the copies with their original. When the original is lifted, by a command, the menu, the
 * API or a sentence served, the copies it made that are still in force are lifted too; when a mute is replaced by a
 * longer one, its copies take the new one. A copy lifted on its own account is that copy alone: the original keeps
 * running, and the copy is not made again.
 */
public final class CaseVariantCopies {

    /** The actor a lift that follows the original is recorded under: no staff member ran it. */
    static final PlayerRef SYSTEM_ACTOR = PlayerRef.system("system");

    /** The audit detail of a copy lifted because its original was. */
    private static final String ORIGINAL_LIFTED = "original-lifted";

    private final ModerationRepository repository;
    private final CaseCopies copies;
    private final Notifier notifier;
    private final SanctionHistoryRecorder history;
    private final Sanctions sanctions;
    private final SanctionSync sync;
    private final JailSync jailSync;
    private final DomainEventPublisher events;
    private final ModerationAudit audit;
    private final Clock clock;

    public CaseVariantCopies(
            ModerationRepository repository,
            CaseCopies copies,
            Notifier notifier,
            SanctionHistoryRecorder history,
            Sanctions sanctions,
            SanctionSync sync,
            JailSync jailSync,
            DomainEventPublisher events,
            ModerationAudit audit,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.copies = Objects.requireNonNull(copies, "copies");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.history = Objects.requireNonNull(history, "history");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.jailSync = Objects.requireNonNull(jailSync, "jailSync");
        this.events = Objects.requireNonNull(events, "events");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Give {@code who} the mute and the jail of {@code inherited}, each unless the account already has an active one of
     * its own. Called once the login is allowed, on the player's storage lane ahead of the join's own work there, so
     * the jail on join already finds the copy; the copied mute is published as an event, which the in-memory mute
     * takes whether the join's read of it lands before or after. Writes to storage, so call it off the tick.
     */
    public void carryOver(PlayerRef who, CaseVariantSanctions.Inherited inherited) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(inherited, "inherited");
        Instant now = clock.instant();
        Optional<PlayerRef> muteFrom =
                inherited.muteFrom().filter(from -> inherited.mute().isActiveAt(now));
        Optional<PlayerRef> jailFrom =
                inherited.jailFrom().filter(from -> inherited.jail().isActiveAt(now));
        if (muteFrom.isEmpty() && jailFrom.isEmpty()) {
            return;
        }
        muteFrom.ifPresent(from -> {
            MuteState own = repository.loadMute(who);
            if (!own.isActiveAt(now) && giveMute(who, own, inherited.mute(), from)) {
                events.publish(new PlayerMuted(who, repository.loadMute(who), now));
            }
        });
        if (jailFrom.isPresent()
                && inherited.jail() instanceof JailState.Active held
                && !repository.loadJail(who).isActiveAt(now)) {
            copies.copyJail(
                    who,
                    held,
                    now,
                    new CaseCopies.Copy(
                            who.uuid(), CaseCopies.Kind.JAIL, jailFrom.get().uuid(), held.issuedAt()));
        }
    }

    /**
     * A moderation event of this server: an unmute or an unjail lifts the copies the lifted sanction made, and a mute
     * replaced by a longer one passes the new one to its copies. Reads and writes storage, so deliver it off the tick.
     */
    public void onEvent(ModerationEvent event) {
        Objects.requireNonNull(event, "event");
        switch (event) {
            case PlayerUnmuted unmuted -> liftMutes(unmuted.target());
            case PlayerUnjailed unjailed -> liftJails(unjailed.target());
            case PlayerMuted muted -> followMute(muted.target(), muted.mute());
            default -> {
                // Bans are not copied: the login check reads them from the original account every time.
            }
        }
    }

    /** Store a copy of {@code original} from {@code from} on {@code who}, replacing {@code current}. */
    private boolean giveMute(PlayerRef who, MuteState current, MuteState original, PlayerRef from) {
        DrawnReason drawn = DrawnReason.inherited(from.name(), reason(original));
        Optional<String> text = Optional.of(SanctionReasons.capped(notifier.render(who, drawn.line(), drawn.args())));
        MuteState copy =
                switch (original) {
                    case MuteState.Permanent permanent ->
                        MuteState.permanent(permanent.issuer(), text, permanent.issuedAt(), Optional.of(drawn));
                    case MuteState.Timed timed ->
                        MuteState.timed(timed.until(), timed.issuer(), text, timed.issuedAt(), Optional.of(drawn));
                    case MuteState.None none -> none;
                };
        if (copy instanceof MuteState.None) {
            return false;
        }
        Instant issuedAt = CaseVariantSanctions.issuedAt(copy);
        CaseCopies.Copy record = new CaseCopies.Copy(who.uuid(), CaseCopies.Kind.MUTE, from.uuid(), issuedAt);
        if (!copies.copyMute(who, current, copy, clock.instant(), record)) {
            return false;
        }
        history.inheritedMute(issuer(copy), who, drawn, text, copy.expiry());
        return true;
    }

    private void liftMutes(PlayerRef origin) {
        Instant now = clock.instant();
        for (CaseCopies.Copy copy : copies.madeFrom(origin.uuid(), CaseCopies.Kind.MUTE)) {
            PlayerRef holder = named(copy.target());
            MuteState held = repository.loadMute(holder);
            if (!isCopy(held, copy, now)) {
                continue;
            }
            repository.saveMute(holder, MuteState.none());
            history.unmute(SYSTEM_ACTOR, holder);
            events.publish(new PlayerUnmuted(holder, now));
            sync.muteChanged(holder);
            audit.unmuted(SYSTEM_ACTOR, holder, true, Optional.of(ORIGINAL_LIFTED));
        }
    }

    private void liftJails(PlayerRef origin) {
        Instant now = clock.instant();
        for (CaseCopies.Copy copy : copies.madeFrom(origin.uuid(), CaseCopies.Kind.JAIL)) {
            PlayerRef holder = named(copy.target());
            if (!(repository.loadJail(holder) instanceof JailState.Active held)
                    || !held.isActiveAt(now)
                    || !held.issuedAt().equals(copy.issuedAt())) {
                continue;
            }
            repository.saveJail(holder, JailState.none());
            sanctions.releaseFromJail(holder);
            jailSync.jailChanged(holder, false);
            events.publish(new PlayerUnjailed(holder, now));
            audit.unjailed(SYSTEM_ACTOR, holder, true, Optional.of(ORIGINAL_LIFTED));
        }
    }

    private void followMute(PlayerRef origin, MuteState replacement) {
        Instant now = clock.instant();
        if (!replacement.isActiveAt(now)) {
            return;
        }
        Instant replacedAt = CaseVariantSanctions.issuedAt(replacement);
        for (CaseCopies.Copy copy : copies.madeFrom(origin.uuid(), CaseCopies.Kind.MUTE)) {
            if (copy.issuedAt().equals(replacedAt)) {
                continue;
            }
            PlayerRef holder = named(copy.target());
            MuteState held = repository.loadMute(holder);
            if (isCopy(held, copy, now) && giveMute(holder, held, replacement, origin)) {
                events.publish(new PlayerMuted(holder, repository.loadMute(holder), now));
                sync.muteChanged(holder);
            }
        }
    }

    /** Whether {@code held} is still the copy {@code copy} records: in force, and issued with its original. */
    private static boolean isCopy(MuteState held, CaseCopies.Copy copy, Instant now) {
        return held.isActiveAt(now) && CaseVariantSanctions.issuedAt(held).equals(copy.issuedAt());
    }

    /** The account {@code account} under its stored name, which the history, the audit and the events show. */
    private PlayerRef named(UUID account) {
        PlayerRef bare = new PlayerRef(account, account.toString());
        return repository.seen(bare).map(SeenRecord::player).orElse(bare);
    }

    private static Optional<String> reason(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.reason();
            case MuteState.Timed timed -> timed.reason();
            case MuteState.None none -> Optional.empty();
        };
    }

    private static Issuer issuer(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.issuer();
            case MuteState.Timed timed -> timed.issuer();
            case MuteState.None none -> throw new IllegalArgumentException("a lifted mute has no issuer");
        };
    }
}
