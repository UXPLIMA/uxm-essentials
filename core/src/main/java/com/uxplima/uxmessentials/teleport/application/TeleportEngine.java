package com.uxplima.uxmessentials.teleport.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupKind;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;
import com.uxplima.uxmessentials.teleport.domain.event.WarmupCancelled;

/**
 * The cooldown/warmup machinery every cooldowned teleport flows through. It owns three steps in order:
 * (1) gate the shared teleport cooldown, (2) begin a move-cancellable warmup through the {@link Warmups}
 * port, and (3) on completion issue the async hop via {@link TeleportExecutor} and — under the
 * {@code teleport} start phase — stamp the cooldown only then, so a denied, cancelled, or move-cancelled
 * teleport never burns it (the cooldown is added <em>after</em> the warmup).
 *
 * <p>The move-cancels-warmup invariant is enforced by the adapter's {@code PlayerMoveEvent} listener
 * flipping the {@link WarmupHandle}; this engine wires the warmup's {@code onComplete}/{@code onCancel}
 * callbacks (teleport-then-stamp, or warmup-cancelled feedback) and returns the live handle so the
 * caller's move listener can cancel it.
 */
public final class TeleportEngine {

    private static final String FEATURE = "tp";

    private final Cooldowns cooldowns;
    private final Warmups warmups;
    private final TeleportExecutor executor;
    private final PlayerNotifier notifier;
    private final DomainEventPublisher events;
    private final TeleportSettings settings;
    private final JailGate jail;

    public TeleportEngine(
            Cooldowns cooldowns,
            Warmups warmups,
            TeleportExecutor executor,
            PlayerNotifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.warmups = Objects.requireNonNull(warmups, "warmups");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.jail = Objects.requireNonNull(jail, "jail");
    }

    /**
     * Run the full gated teleport: check the cooldown, begin the warmup, and on completion hop and
     * (phase-dependently) stamp. Returns the cooldown-gate result so the caller can render the remaining
     * wait; on success the warmup is already in flight and its handle is registered with {@code mover}'s
     * move listener by the caller through {@link #beginGatedWarmup}.
     */
    public Result<Unit, TeleportError> launch(PlayerRef mover, Destination destination, TeleportKind kind) {
        Objects.requireNonNull(mover, "mover");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(kind, "kind");
        if (jail.isJailed(mover)) {
            // A jailed player cannot self-teleport — /home, /warp, /spawn, /back, /rtp all funnel through here
            // (docs/permissions.md). The moderation context owns the gate; without moderation it is NEVER.
            notifier.send(mover, TeleportMessageKey.JAILED);
            return Result.err(TeleportError.JAILED);
        }
        Result<Unit, Duration> gate = cooldowns.check(mover, cooldownKind(kind));
        if (gate.isErr()) {
            notifyCooldown(mover, gate.errorOrThrow());
            return Result.err(TeleportError.ON_COOLDOWN);
        }
        beginGatedWarmup(mover, destination, kind);
        return Result.ok();
    }

    /**
     * Begin the warmup and wire its callbacks, returning the {@link WarmupHandle} the caller's move
     * listener cancels. Separated from {@link #launch} so a flow that has already passed the cooldown
     * gate (an accepted {@code tpa}) reuses the warmup wiring without re-checking.
     */
    public WarmupHandle beginGatedWarmup(PlayerRef mover, Destination destination, TeleportKind kind) {
        WarmupKind warmupKind = new WarmupKind(FEATURE, settings.defaultWarmupSeconds());
        return warmups.begin(
                mover,
                warmupKind,
                () -> onWarmupComplete(mover, destination, kind),
                () -> onWarmupCancelled(mover, kind));
    }

    private void onWarmupComplete(PlayerRef mover, Destination destination, TeleportKind kind) {
        executor.teleport(mover, destination, kind);
        if (settings.cooldownStartPhase() == CooldownStartPhase.TELEPORT) {
            cooldowns.stamp(mover, cooldownKind(kind));
        }
    }

    private void onWarmupCancelled(PlayerRef mover, TeleportKind kind) {
        notifier.send(mover, TeleportMessageKey.WARMUP_CANCELLED);
        events.publish(new WarmupCancelled(mover, kind, WarmupCancelReason.ABORTED));
    }

    /**
     * Stamp the cooldown for an earlier phase ({@code request}/{@code accept}) when configured. This is the
     * {@code tpa} path, so the kind is {@link TeleportKind#REQUEST}; tpa carries no per-verb override and
     * stamps under the shared {@code tp} scope, matching its prior single-cooldown behaviour.
     */
    public void stampForPhase(PlayerRef mover, CooldownStartPhase atPhase) {
        if (settings.cooldownStartPhase() == atPhase && atPhase != CooldownStartPhase.TELEPORT) {
            cooldowns.stamp(mover, cooldownKind(TeleportKind.REQUEST));
        }
    }

    private void notifyCooldown(PlayerRef mover, Duration remaining) {
        long seconds = Math.max(1, remaining.toSeconds());
        notifier.send(mover, TeleportMessageKey.COOLDOWN_ACTIVE, Map.of("seconds", Long.toString(seconds)));
    }

    /**
     * The cooldown identity for {@code kind}. The tier-node space stays {@code tp} so every verb still
     * resolves the shared {@code uxmessentials.tp.cooldown.<n>} permission tiers. A verb whose config
     * override is set ({@code cooldowns.<verb> >= 0}) gets its own stamp scope ({@code tp.<verb>}) so it
     * rate-limits independently, with the override as its fallback default; a verb left at {@code -1}
     * keeps the shared {@code tp} stamp and the shared {@code default-cooldown}, preserving prior behaviour.
     */
    private CooldownKind cooldownKind(TeleportKind kind) {
        long override = settings.verbCooldownOverrideSeconds(kind);
        Cooldowns.CooldownStartPhase phase = mapPhase(settings.cooldownStartPhase());
        if (override < 0) {
            return new CooldownKind(FEATURE, settings.defaultCooldownSeconds(), phase);
        }
        return CooldownKind.scoped(FEATURE, FEATURE + "." + verbScope(kind), override, phase);
    }

    private static String verbScope(TeleportKind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Cooldowns.CooldownStartPhase mapPhase(CooldownStartPhase phase) {
        return switch (phase) {
            case REQUEST -> Cooldowns.CooldownStartPhase.REQUEST;
            case ACCEPT -> Cooldowns.CooldownStartPhase.ACCEPT;
            case TELEPORT -> Cooldowns.CooldownStartPhase.TELEPORT;
        };
    }
}
