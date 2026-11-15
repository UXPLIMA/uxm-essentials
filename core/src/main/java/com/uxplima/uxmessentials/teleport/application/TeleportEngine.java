package com.uxplima.uxmessentials.teleport.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupKind;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.CombatGate;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;
import com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleporting;
import com.uxplima.uxmessentials.teleport.domain.event.WarmupCancelled;
import org.jspecify.annotations.Nullable;

/**
 * The cooldown/warmup machinery every cooldowned teleport flows through. It owns three steps in order:
 * (1) gate the shared teleport cooldown, (2) begin a move-cancellable warmup through the {@link Warmups}
 * port, and (3) on completion issue the async hop via {@link TeleportExecutor} and, under the
 * {@code teleport} start phase. Stamp the cooldown only then, so a denied, cancelled, or move-cancelled
 * teleport never burns it (the cooldown is added <em>after</em> the warmup).
 *
 * <p>The move-cancels-warmup invariant is enforced by the adapter's {@code PlayerMoveEvent} listener
 * flipping the {@link WarmupHandle}; this engine wires the warmup's {@code onComplete}/{@code onCancel}
 * callbacks (teleport-then-stamp, or warmup-cancelled feedback) and returns the live handle so the
 * caller's move listener can cancel it.
 */
public final class TeleportEngine {

    private static final String FEATURE = "tp";

    // /rtp resolves its own cooldown tier, uxmessentials.rtp.cooldown.<seconds>, rather than riding the shared tp
    // tier, so an operator can rate-limit random-teleport independently. The pre-search check (gateRandom) and the
    // post-arrival stamp (onLanded) both resolve this same feature, so the two never key different nodes.
    private static final String RTP_FEATURE = "rtp";

    private final Cooldowns cooldowns;
    private final Warmups warmups;
    private final TeleportExecutor executor;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final DomainGate gate;
    private final TeleportSettings settings;
    private final JailGate jail;
    private final CombatGate combat;
    private final TeleportFee fee;
    private final ArrivalGrace grace;
    private final Logger log;

    public TeleportEngine(
            Cooldowns cooldowns,
            Warmups warmups,
            TeleportExecutor executor,
            Notifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail) {
        this(cooldowns, warmups, executor, notifier, events, settings, jail, CombatGate.NEVER);
    }

    public TeleportEngine(
            Cooldowns cooldowns,
            Warmups warmups,
            TeleportExecutor executor,
            Notifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail,
            CombatGate combat) {
        this(
                cooldowns,
                warmups,
                executor,
                notifier,
                events,
                settings,
                jail,
                combat,
                TeleportFee.FREE,
                ArrivalGrace.NONE);
    }

    public TeleportEngine(
            Cooldowns cooldowns,
            Warmups warmups,
            TeleportExecutor executor,
            Notifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail,
            TeleportFee fee,
            ArrivalGrace grace) {
        this(cooldowns, warmups, executor, notifier, events, settings, jail, CombatGate.NEVER, fee, grace);
    }

    /** The engine with nothing outside the plugin able to refuse a teleport. The form the pure tests use. */
    public TeleportEngine(
            Cooldowns cooldowns,
            Warmups warmups,
            TeleportExecutor executor,
            Notifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail,
            CombatGate combat,
            TeleportFee fee,
            ArrivalGrace grace) {
        this(
                cooldowns,
                warmups,
                executor,
                notifier,
                events,
                settings,
                jail,
                combat,
                fee,
                grace,
                DomainGate.allowAll(),
                new SilentLog());
    }

    public TeleportEngine(
            Cooldowns cooldowns,
            Warmups warmups,
            TeleportExecutor executor,
            Notifier notifier,
            DomainEventPublisher events,
            TeleportSettings settings,
            JailGate jail,
            CombatGate combat,
            TeleportFee fee,
            ArrivalGrace grace,
            DomainGate gate,
            Logger log) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.warmups = Objects.requireNonNull(warmups, "warmups");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.jail = Objects.requireNonNull(jail, "jail");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.fee = Objects.requireNonNull(fee, "fee");
        this.grace = Objects.requireNonNull(grace, "grace");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.log = Objects.requireNonNull(log, "log");
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
        Result<Unit, TeleportError> admitted = preGate(mover, destination, kind);
        if (admitted.isErr()) {
            return admitted;
        }
        if (beginGatedWarmup(mover, destination, kind).isCancelled()) {
            // The only thing that refuses a warmup before it has begun is the gate, and it has already told the
            // player. Reported as an error so a caller that acts on the outcome (staff /tp, world entry) sees it.
            return Result.err(TeleportError.VETOED);
        }
        return Result.ok();
    }

    /**
     * The checks {@link #launch} runs before a warmup, for a hop that leaves this backend: the origin asks them
     * before it charges and connects the player, so a cross-server teleport is refused for the same jail, combat
     * tag and cooldown as a local one, and a refusal costs nothing. Each refusal is told to the player. The veto
     * gate is left to the target's {@link #arriveImmediately}, the backend where the destination exists.
     */
    public Result<Unit, TeleportError> gateHop(PlayerRef mover, Destination destination, TeleportKind kind) {
        Objects.requireNonNull(mover, "mover");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(kind, "kind");
        return preGate(mover, destination, kind);
    }

    private Result<Unit, TeleportError> preGate(PlayerRef mover, Destination destination, TeleportKind kind) {
        if (jail.isJailed(mover)) {
            // A jailed player cannot self-teleport, /home, /warp, /spawn, /back, /rtp all funnel through here.
            // The moderation context owns the gate; without moderation it is NEVER.
            notifier.send(mover, TeleportMessageKey.JAILED);
            return Result.err(TeleportError.JAILED);
        }
        if (combat.isTagged(mover)) {
            // Same shape as the jail gate, and checked before the cooldown so a refused escape costs nothing.
            // The tag belongs to whichever combat plugin is installed; without one the gate is NEVER.
            notifier.send(mover, TeleportMessageKey.COMBAT_TAGGED);
            return Result.err(TeleportError.COMBAT_TAGGED);
        }
        Result<Unit, Duration> waiting = cooldowns.check(mover, cooldownKind(kind, destination));
        if (waiting.isErr()) {
            notifyCooldown(mover, waiting.errorOrThrow());
            return Result.err(TeleportError.ON_COOLDOWN);
        }
        return Result.ok();
    }

    /**
     * Begin the warmup and wire its callbacks, returning the {@link WarmupHandle} the caller's move
     * listener cancels. Separated from {@link #launch} so a flow that has already passed the cooldown
     * gate (an accepted {@code tpa}) reuses the warmup wiring without re-checking.
     */
    public WarmupHandle beginGatedWarmup(PlayerRef mover, Destination destination, TeleportKind kind) {
        return beginWarmup(
                mover,
                destination,
                kind,
                () -> hop(mover, destination, kind, TeleportEngine::noPostArrival, TeleportEngine::nothingToUndo));
    }

    /**
     * Begin the move-cancellable warmup of a hop that leaves this backend, running {@code onComplete} when it ends
     * instead of a local teleport. The veto gate is not asked here: the destination lives on the target backend,
     * whose {@link #arriveImmediately} asks it on landing. Returns the handle the move listener cancels.
     */
    public WarmupHandle beginHopWarmup(PlayerRef mover, TeleportKind kind, Runnable onComplete) {
        Objects.requireNonNull(mover, "mover");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onComplete, "onComplete");
        WarmupKind warmupKind = new WarmupKind(FEATURE, settings.defaultWarmupSeconds());
        return warmups.begin(mover, warmupKind, onComplete, () -> onWarmupCancelled(mover, kind));
    }

    private static void nothingToUndo() {
        // A hop that cost nothing has nothing to undo when it misses.
    }

    private static void noPostArrival() {
        // The default gated teleport (home/warp/spawn/back) has no per-arrival side effect beyond the cooldown
        // stamp the engine always folds in; only RTP adds a grace window on top.
    }

    /**
     * Gate an {@code /rtp} <em>before</em> the safe-search runs: the jail gate, the shared cooldown, and, when
     * a cost is configured, affordability. A player who is jailed, on cooldown, or short on funds is rejected
     * here (with the matching notice) so the pre-warmed queue is never even consulted for them; the money is not
     * touched, only checked. On {@code ok} the caller may serve a location and hand it to {@link #launchRandom}.
     */
    public Result<Unit, TeleportError> gateRandom(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (jail.isJailed(who)) {
            notifier.send(who, TeleportMessageKey.JAILED);
            return Result.err(TeleportError.JAILED);
        }
        if (combat.isTagged(who)) {
            notifier.send(who, TeleportMessageKey.COMBAT_TAGGED);
            return Result.err(TeleportError.COMBAT_TAGGED);
        }
        Result<Unit, Duration> waiting = cooldowns.check(who, randomCooldownKind());
        if (waiting.isErr()) {
            notifyCooldown(who, waiting.errorOrThrow());
            return Result.err(TeleportError.ON_COOLDOWN);
        }
        BigDecimal cost = settings.rtpCost();
        if (cost.signum() > 0 && !fee.canAfford(who, cost)) {
            notifier.send(who, TeleportMessageKey.RTP_CANT_AFFORD, price(cost));
            return Result.err(TeleportError.RTP_CANT_AFFORD);
        }
        return Result.ok();
    }

    /**
     * Begin the move-cancellable warmup for an already-gated {@code /rtp}. When the warmup ends the cost (if any)
     * is withdrawn before the hop, so a player who spent their balance while standing still is refused with the
     * same line the gate uses and does not move. A hop that then misses gives the money back. The cooldown stamp
     * and the arrival grace wait for a real landing, so a move-cancelled warmup costs nothing at all.
     */
    public void launchRandom(PlayerRef who, Destination destination) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(destination, "destination");
        beginWarmup(who, destination, TeleportKind.RANDOM, () -> chargeThenHop(who, destination));
    }

    /**
     * Serve an immediate {@code /rtp} landing with no warmup, cooldown, or charge, the respawn / first-join
     * path. The grace window still applies on arrival, since a fresh drop into unexplored terrain is exactly
     * where it matters most. There is no move-cancel because these teleports are involuntary and instant.
     */
    public void arriveRandomImmediately(PlayerRef who, Destination destination) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(destination, "destination");
        executor.relocate(who, destination, TeleportKind.RANDOM, () -> grace.applyOnArrival(who));
    }

    /**
     * Move a player who did not ask to move: the void rescue a world performs when someone falls out of it.
     * No warmup, no cooldown, no charge, and no {@code /back} point, since a rescue is not a place the player
     * chose to leave. The arrival grace still applies, which is what stops the fall damage the drop earned.
     */
    public void relocateImmediately(PlayerRef who, Destination destination) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(destination, "destination");
        executor.relocate(who, destination, TeleportKind.RESPAWN, () -> grace.applyOnArrival(who));
    }

    /**
     * Finish a hop the player already paid for and waited out on another backend: the cross-server arrival. No
     * warmup and no cooldown check, since the origin ran {@link #gateHop} before it charged and the player has
     * just joined. A jailed or combat-tagged player is still refused, and so is a hop the veto gate stops, each with
     * its notice. The landing goes through the same path as a local teleport's, so a {@code teleport}-phase
     * cooldown is stamped once the player stands at the destination. Exactly one of {@code onLanded} and
     * {@code onFailed} runs for a hop that was issued; a refusal runs neither and returns the error, so the caller
     * can give the money back.
     */
    public Result<Unit, TeleportError> arriveImmediately(
            PlayerRef mover, Destination destination, TeleportKind kind, Runnable onLanded, Runnable onFailed) {
        Objects.requireNonNull(mover, "mover");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onLanded, "onLanded");
        Objects.requireNonNull(onFailed, "onFailed");
        if (jail.isJailed(mover)) {
            notifier.send(mover, TeleportMessageKey.JAILED);
            return Result.err(TeleportError.JAILED);
        }
        if (combat.isTagged(mover)) {
            notifier.send(mover, TeleportMessageKey.COMBAT_TAGGED);
            return Result.err(TeleportError.COMBAT_TAGGED);
        }
        if (!gate.allows(new PlayerTeleporting(mover, kind, destination.position()))) {
            notifier.send(mover, TeleportError.VETOED.messageKey());
            return Result.err(TeleportError.VETOED);
        }
        executor.teleport(mover, destination, kind, () -> landArrival(mover, destination, kind, onLanded), onFailed);
        return Result.ok();
    }

    /**
     * Stamp the landing's cooldown, then report the landing. The caller settles money on {@code onLanded}, so a
     * stamp that fails is logged and the landing still reported: the player has moved either way.
     */
    private void landArrival(PlayerRef mover, Destination destination, TeleportKind kind, Runnable onLanded) {
        try {
            onLanded(mover, destination, kind, TeleportEngine::noPostArrival);
        } catch (RuntimeException stampFailure) {
            log.error("cooldown stamp for the cross-server arrival of " + mover.name() + " failed", stampFailure);
        }
        onLanded.run();
    }

    private void chargeThenHop(PlayerRef who, Destination destination) {
        BigDecimal cost = settings.rtpCost();
        if (cost.signum() <= 0) {
            hop(who, destination, TeleportKind.RANDOM, () -> grace.applyOnArrival(who), TeleportEngine::nothingToUndo);
            return;
        }
        PaidHop paid = new PaidHop(who, destination, cost);
        CompletionStage<Result<Unit, TeleportError>> charge;
        try {
            charge = fee.charge(who, cost);
        } catch (RuntimeException chargeFailure) {
            paid.afterCharge(null, chargeFailure);
            return;
        }
        var ignored = charge.whenComplete(paid::afterCharge);
    }

    private Map<String, String> price(BigDecimal cost) {
        return Map.of("cost", fee.format(cost));
    }

    private void refund(PlayerRef who, BigDecimal cost) {
        CompletionStage<Result<Unit, TeleportError>> refund;
        try {
            refund = fee.refund(who, cost);
        } catch (RuntimeException refundFailure) {
            afterRefund(who, cost, null, refundFailure);
            return;
        }
        var ignored = refund.whenComplete((refunded, refundFailure) -> afterRefund(who, cost, refunded, refundFailure));
    }

    private void afterRefund(
            PlayerRef who,
            BigDecimal cost,
            @Nullable Result<Unit, TeleportError> refunded,
            @Nullable Throwable refundFailure) {
        try {
            if (refunded == null) {
                log.error(
                        "rtp refund of " + cost.toPlainString() + " for " + who.name() + " did not complete",
                        orUnknown(refundFailure));
                notifier.send(who, TeleportError.RTP_REFUND_FAILED.messageKey(), price(cost));
                return;
            }
            notifier.send(
                    who,
                    refunded.isOk()
                            ? TeleportMessageKey.RTP_REFUNDED
                            : refunded.errorOrThrow().messageKey(),
                    price(cost));
        } catch (RuntimeException noticeFailure) {
            log.error("rtp refund notice for " + who.name() + " could not be sent", noticeFailure);
        }
    }

    private static Throwable orUnknown(@Nullable Throwable failure) {
        return failure != null ? failure : new IllegalStateException("the fee port completed without an outcome");
    }

    /**
     * One paid {@code /rtp} after its warmup: the charge, then the hop, then at most one refund. The money is owed
     * back only once the charge has gone through and only until the player lands, so a miss reported twice, or a
     * failure thrown after the executor already reported one, never refunds twice.
     */
    private final class PaidHop {

        private enum State {
            UNCHARGED,
            OWED,
            SETTLED
        }

        private final PlayerRef who;
        private final Destination destination;
        private final BigDecimal cost;
        private final AtomicReference<State> state = new AtomicReference<>(State.UNCHARGED);

        PaidHop(PlayerRef who, Destination destination, BigDecimal cost) {
            this.who = who;
            this.destination = destination;
            this.cost = cost;
        }

        void afterCharge(@Nullable Result<Unit, TeleportError> charged, @Nullable Throwable chargeFailure) {
            try {
                if (charged == null) {
                    // The port broke its promise to always answer, so whether money left is unknown: no hop, no
                    // refund, and the operator gets the trace to settle it.
                    log.error(
                            "rtp charge of " + cost.toPlainString() + " for " + who.name() + " did not complete",
                            orUnknown(chargeFailure));
                    notifier.send(who, TeleportError.RTP_CHARGE_FAILED.messageKey(), price(cost));
                    return;
                }
                if (charged.isErr()) {
                    notifier.send(who, charged.errorOrThrow().messageKey(), price(cost));
                    return;
                }
                state.set(State.OWED);
                executor.teleport(
                        who,
                        destination,
                        TeleportKind.RANDOM,
                        () -> {
                            state.set(State.SETTLED);
                            onLanded(who, destination, TeleportKind.RANDOM, () -> grace.applyOnArrival(who));
                        },
                        this::missed);
            } catch (RuntimeException failure) {
                log.error("rtp for " + who.name() + " failed after the charge", failure);
                missed();
            }
        }

        void missed() {
            if (state.compareAndSet(State.OWED, State.SETTLED)) {
                refund(who, cost);
            }
        }
    }

    /** The logger behind the pure-test constructor, which has no operator to tell. */
    private static final class SilentLog implements Logger {

        @Override
        public void info(String message, Object... args) {
            // Nobody reads a pure test's log.
        }

        @Override
        public void warn(String message, Object... args) {
            // Nobody reads a pure test's log.
        }

        @Override
        public void error(String message, Throwable cause) {
            // Nobody reads a pure test's log.
        }

        @Override
        public void debug(String message, Object... args) {
            // Nobody reads a pure test's log.
        }
    }

    private WarmupHandle beginWarmup(PlayerRef mover, Destination destination, TeleportKind kind, Runnable onComplete) {
        // The one choke point every voluntary teleport passes through, which is why the veto question lives here
        // rather than at each of the half-dozen entry points. Before the warmup, so a refused teleport does not
        // make the player stand still first.
        if (!gate.allows(new PlayerTeleporting(mover, kind, destination.position()))) {
            notifier.send(mover, TeleportError.VETOED.messageKey());
            return new Warmups.RefusedWarmup();
        }
        long seconds =
                destination.warmupOverride().map(Duration::toSeconds).orElseGet(() -> settings.defaultWarmupSeconds());
        WarmupKind warmupKind = new WarmupKind(FEATURE, seconds);
        return warmups.begin(mover, warmupKind, onComplete, () -> onWarmupCancelled(mover, kind));
    }

    private void hop(
            PlayerRef mover, Destination destination, TeleportKind kind, Runnable postArrival, Runnable onFailed) {
        executor.teleport(mover, destination, kind, () -> onLanded(mover, destination, kind, postArrival), onFailed);
    }

    private void onLanded(PlayerRef mover, Destination destination, TeleportKind kind, Runnable postArrival) {
        if (settings.cooldownStartPhase() == CooldownStartPhase.TELEPORT) {
            cooldowns.stamp(mover, cooldownKind(kind, destination));
        }
        postArrival.run();
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
            cooldowns.stamp(
                    mover,
                    cooldownKind(
                            TeleportKind.REQUEST,
                            Destination.at(new Position(new WorldRef(UUID.randomUUID(), "world"), 0, 0, 0, 0f, 0f))));
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
    private CooldownKind cooldownKind(TeleportKind kind, Destination destination) {
        if (kind == TeleportKind.RANDOM && destination.cooldownOverride().isEmpty()) {
            // A random teleport carries no destination-level override, so its stamp resolves the same rtp-specific
            // tier the pre-search gate checked: keeping gateRandom's check and onLanded's stamp on one node.
            return randomCooldownKind();
        }
        if (destination.cooldownOverride().isPresent()) {
            long seconds = destination.cooldownOverride().get().toSeconds();
            return CooldownKind.scoped(
                    FEATURE, FEATURE + "." + verbScope(kind), seconds, mapPhase(settings.cooldownStartPhase()));
        }
        long override = settings.verbCooldownOverrideSeconds(kind);
        Cooldowns.CooldownStartPhase phase = mapPhase(settings.cooldownStartPhase());
        if (override < 0) {
            return new CooldownKind(FEATURE, settings.defaultCooldownSeconds(), phase);
        }
        return CooldownKind.scoped(FEATURE, FEATURE + "." + verbScope(kind), override, phase);
    }

    /**
     * The {@code /rtp} cooldown identity used by {@link #gateRandom} before a destination exists. A random
     * teleport never carries a destination-level override, so this matches {@link #cooldownKind} for a
     * RANDOM destination exactly, the pre-search check and the post-arrival stamp resolve the same key.
     *
     * <p>RTP keys its own {@code rtp} tier ({@code uxmessentials.rtp.cooldown.<seconds>}) with its own stamp, so
     * a {@code /rtp} cooldown is independent of the shared {@code tp} tier. The config default is the per-verb
     * {@code cooldowns.rtp} override when set, else the shared {@code default-cooldown}; the numbered tier still
     * refines it through the shared min-reducer (the shortest tier the player holds wins).
     */
    private CooldownKind randomCooldownKind() {
        long override = settings.verbCooldownOverrideSeconds(TeleportKind.RANDOM);
        long defaultSeconds = override < 0 ? settings.defaultCooldownSeconds() : override;
        return new CooldownKind(RTP_FEATURE, defaultSeconds, mapPhase(settings.cooldownStartPhase()));
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
