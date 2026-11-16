package com.uxplima.uxmessentials.customcommands.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.application.port.RequirementCheck;
import com.uxplima.uxmessentials.customcommands.application.port.RunFeedback;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.application.ledger.CooldownTiers;
import com.uxplima.uxmessentials.shared.application.permission.ContentPermissions;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * Runs one operator-defined command through its gates, in the order the design fixes: sender kind, permission,
 * chain depth, requirements, cooldown, warmup, cost, actions. Each gate produces its own {@link RunOutcome}, and
 * every outcome is reported through the feedback port exactly once, so the lines a player reads and the order the
 * gates run in can never drift apart.
 *
 * <p>The cost is charged after the warmup completes, never before, so a warmup a player walks out of costs nothing
 * and no refund path is needed. The cooldown is stamped at the same moment the chain starts, so a run that was
 * refused at any gate leaves the clock alone.
 *
 * <p>A player has at most one custom command warmup, as with teleports. Running a command with a warmup while an
 * earlier one is still counting down replaces the earlier one rather than being refused: the earlier one ends as
 * {@link RunOutcome.WarmupReplaced}, releases its depth and is never charged, and only the latest one can run. A
 * repeat refused at an earlier gate (requirements, cooldown) starts no warmup and leaves the earlier one alone. The
 * replacing run is allowed one level past the depth ceiling, because the level the earlier warmup holds is released
 * before the replacement's warmup begins: a repeat is never turned away for the depth of the run it replaces.
 *
 * <p>A warmup the player skips (a bypass node, or a zero tier) completes inside {@code begin}. The run then reports
 * what the chain did, never a countdown that is already over.
 *
 * <p>Depth is entered before the requirements are evaluated and released once the chain has run, which means a
 * command that calls itself through a {@code command:} action inside the same dispatch is refused at the configured
 * depth. A step scheduled behind a {@code delay:} runs later at depth zero: that is a fresh execution rather than a
 * re-entry, and treating it as one would make a delayed self-call impossible for no benefit.
 *
 * <p>A priced command whose currency backend does not answer follows {@link FeeBackendPolicy}: it runs unpaid or is
 * refused. Either way the operator is told once when the backend goes missing and once when it answers again, not on
 * every run, because a busy server would otherwise bury the log in the same line.
 */
public final class RunCustomCommand {

    /** Holding this node skips the cooldown gate outright. */
    public static final String COOLDOWN_BYPASS = ContentPermissions.CUSTOMCOMMAND_COOLDOWN_BYPASS;

    /** Holding this node runs a priced command without paying for it. */
    public static final String COST_BYPASS = ContentPermissions.CUSTOMCOMMAND_COST_BYPASS;

    /** The feature segment the shared warmup port builds its permission nodes from. */
    public static final String WARMUP_FEATURE = "customcommand";

    private final Permissions permissions;
    private final Cooldowns cooldowns;
    private final Warmups warmups;
    private final ActionRunner actions;
    private final RequirementCheck requirements;
    private final CommandFee fee;
    private final FeeBackendPolicy onBackendMissing;
    private final RunFeedback feedback;
    private final ChainDepth depth;
    private final Logger log;
    private final AtomicBoolean backendMissing = new AtomicBoolean();

    /**
     * The warmup each player is waiting on, so the depth gate knows a repeat will end it. An entry leaves when its
     * warmup ends in any way, and only that warmup's own ending removes it.
     */
    private final ConcurrentHashMap<UUID, WarmupHandle> warmingUp = new ConcurrentHashMap<>();

    public RunCustomCommand(
            Permissions permissions,
            Cooldowns cooldowns,
            Warmups warmups,
            ActionRunner actions,
            RequirementCheck requirements,
            CommandFee fee,
            FeeBackendPolicy onBackendMissing,
            RunFeedback feedback,
            ChainDepth depth,
            Logger log) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.warmups = Objects.requireNonNull(warmups, "warmups");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        this.fee = Objects.requireNonNull(fee, "fee");
        this.onBackendMissing = Objects.requireNonNull(onBackendMissing, "onBackendMissing");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.depth = Objects.requireNonNull(depth, "depth");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The cooldown label a command is keyed under, shared by the gate and the stamp. */
    public static String cooldownLabel(CustomCommandId id) {
        Objects.requireNonNull(id, "id");
        return "customcommand." + id.value();
    }

    /**
     * Run {@code command} for {@code actor}. {@code console} marks a non-player sender, which changes two gates: it
     * may be turned away outright, and it clears a permission gate no console account can hold.
     */
    public RunOutcome run(CustomCommand command, PlayerRef actor, boolean console, Map<String, String> arguments) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(arguments, "arguments");
        RunOutcome refused = senderGates(command, actor, console);
        if (refused != null) {
            return report(actor, command, refused);
        }
        if (!enterDepth(command, actor)) {
            log.warn(
                    "custom command '{}' called itself past the allowed depth",
                    command.id().value());
            return report(actor, command, new RunOutcome.DepthExceeded());
        }
        // The level is released once, by whichever comes first: this method on any path that did not hand it to a
        // started warmup, or the warmup's own ending. A throw while the warmup is being set up therefore cannot leak
        // it,
        // and a warmup that ended inside begin cannot release it twice.
        AtomicBoolean holding = new AtomicBoolean(true);
        Runnable release = () -> {
            if (holding.compareAndSet(true, false)) {
                depth.exit(actor.uuid());
            }
        };
        boolean handedOver = false;
        try {
            RunOutcome blocked = stateGates(command, actor, arguments, false);
            if (blocked != null) {
                return report(actor, command, blocked);
            }
            if (command.warmup().isZero()) {
                return report(actor, command, finish(command, actor, arguments));
            }
            AtomicReference<RunOutcome> ended = new AtomicReference<>();
            beginWarmup(command, actor, arguments, ended, release);
            handedOver = true;
            RunOutcome already = ended.get();
            // A warmup that ended inside begin has reported its own outcome and released its depth already.
            return already != null ? already : report(actor, command, new RunOutcome.WarmupStarted(command.warmup()));
        } finally {
            if (!handedOver) {
                release.run();
            }
        }
    }

    /**
     * Claim a level of depth for this run. A run that starts a warmup while the actor is still waiting on another one
     * replaces it, and the level the earlier one holds is released before the new warmup needs its own.
     */
    private boolean enterDepth(CustomCommand command, PlayerRef actor) {
        boolean replacing = !command.warmup().isZero() && warmingUp.containsKey(actor.uuid());
        return replacing ? depth.enterReplacing(actor.uuid()) : depth.enter(actor.uuid());
    }

    /**
     * Evaluate every gate for {@code actor} without touching anything: no deny chain, no depth entry, no cooldown
     * stamp, no charge and no actions. This is what {@code /customcmd test} reports.
     */
    public RunOutcome dryRun(CustomCommand command, PlayerRef actor, boolean console) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");
        RunOutcome refused = senderGates(command, actor, console);
        if (refused != null) {
            return refused;
        }
        RunOutcome blocked = stateGates(command, actor, Map.of(), true);
        if (blocked != null) {
            return blocked;
        }
        if (!charged(command, actor)) {
            return new RunOutcome.Ok();
        }
        if (!fee.available()) {
            return onBackendMissing == FeeBackendPolicy.DENY ? new RunOutcome.FeeBackendMissing() : new RunOutcome.Ok();
        }
        return fee.canAfford(actor, command.cost()) ? new RunOutcome.Ok() : new RunOutcome.CannotAfford(command.cost());
    }

    /** The gates that only look at who is asking: the console policy and the declared permission node. */
    private @Nullable RunOutcome senderGates(CustomCommand command, PlayerRef actor, boolean console) {
        if (console && !command.consoleAllowed()) {
            return new RunOutcome.ConsoleDenied();
        }
        if (console) {
            return null;
        }
        if (command.permission().isPresent()
                && !permissions.has(actor, command.permission().get())) {
            return new RunOutcome.NoPermission();
        }
        return null;
    }

    /**
     * The gates that read the actor's current state: the declared requirements and the cooldown. A {@code dry} pass
     * evaluates both without running the deny chain, which is what makes {@code /customcmd test} side-effect free.
     */
    private @Nullable RunOutcome stateGates(
            CustomCommand command, PlayerRef actor, Map<String, String> arguments, boolean dry) {
        if (!requirements.passes(actor, command.requirements(), arguments)) {
            if (!dry && !command.requirementDeny().isEmpty()) {
                actions.run(actor, command.requirementDeny(), arguments);
            }
            return new RunOutcome.RequirementsFailed();
        }
        if (!waits(command, actor)) {
            return null;
        }
        return cooldowns
                .checkLabel(actor, cooldownLabel(command.id()))
                .asError()
                .<RunOutcome>map(RunOutcome.OnCooldown::new)
                .orElse(null);
    }

    /**
     * Whether a run of {@code command} by {@code actor} waits for anything: the command's own cooldown, or a label
     * tier the actor holds, which sets the wait even on a command declared without one. A command nobody waits for
     * skips the gate and the stamp, so it runs at once on a network too, before the actor's cooldowns are read.
     */
    private boolean waits(CustomCommand command, PlayerRef actor) {
        if (permissions.has(actor, COOLDOWN_BYPASS)) {
            return false;
        }
        return !command.cooldown().isZero()
                || CooldownTiers.labelWaitSeconds(permissions, actor, cooldownLabel(command.id()), 0L) > 0;
    }

    /**
     * Start the shared warmup, deferring the charge and the chain to the moment it completes. The actor's earlier
     * warmup, if any, is superseded first. Exactly one of the three callbacks runs; each forgets this warmup, runs
     * {@code release} for the depth this run holds and leaves the outcome it reported in {@code ended}.
     */
    private void beginWarmup(
            CustomCommand command,
            PlayerRef actor,
            Map<String, String> arguments,
            AtomicReference<RunOutcome> ended,
            Runnable release) {
        UUID who = actor.uuid();
        WarmupHandle earlier = warmingUp.remove(who);
        if (earlier != null) {
            earlier.supersede();
        }
        AtomicReference<WarmupHandle> self = new AtomicReference<>();
        Runnable forget = () -> {
            // Empty only for a warmup that ended inside begin, which was never remembered.
            WarmupHandle started = self.get();
            if (started != null) {
                warmingUp.remove(who, started);
            }
        };
        Warmups.WarmupKind kind =
                new Warmups.WarmupKind(WARMUP_FEATURE, command.warmup().toSeconds());
        WarmupHandle handle = warmups.begin(
                actor,
                kind,
                () -> {
                    forget.run();
                    try {
                        ended.set(report(actor, command, finish(command, actor, arguments)));
                    } finally {
                        release.run();
                    }
                },
                () -> {
                    forget.run();
                    release.run();
                    ended.set(report(actor, command, new RunOutcome.WarmupCancelled()));
                },
                () -> {
                    forget.run();
                    release.run();
                    ended.set(report(actor, command, new RunOutcome.WarmupReplaced()));
                });
        self.set(handle);
        remember(who, handle);
    }

    /** Remember {@code handle} as the warmup {@code who} waits on, unless it has already ended. */
    private void remember(UUID who, WarmupHandle handle) {
        if (handle.isComplete() || handle.isCancelled()) {
            return;
        }
        WarmupHandle previous = warmingUp.put(who, handle);
        if (previous != null && previous != handle) {
            // Two warmups begun at once on different threads: whichever is remembered last supersedes the other.
            previous.supersede();
        }
        if (handle.isComplete() || handle.isCancelled()) {
            // It ended between the check and the put, after its own callback had nothing to forget.
            warmingUp.remove(who, handle);
        }
    }

    /** Charge the cost, stamp the cooldown and run the chain, in that order; a failed charge runs nothing. */
    private RunOutcome finish(CustomCommand command, PlayerRef actor, Map<String, String> arguments) {
        RunOutcome unpaid = charged(command, actor) ? pay(command, actor) : null;
        if (unpaid != null) {
            return unpaid;
        }
        if (waits(command, actor)) {
            cooldowns.stampLabel(actor, cooldownLabel(command.id()), command.cooldown());
        }
        actions.run(actor, command.actions(), arguments);
        return new RunOutcome.Ok();
    }

    /**
     * Take the price, or answer why the run stops. A missing backend is settled by the policy before any money is
     * touched, so an allowed run is simply not charged and a denied one never reaches the provider.
     */
    private @Nullable RunOutcome pay(CustomCommand command, PlayerRef actor) {
        if (!backendAnswers()) {
            return onBackendMissing == FeeBackendPolicy.DENY ? new RunOutcome.FeeBackendMissing() : null;
        }
        if (!fee.canAfford(actor, command.cost()) || !fee.charge(actor, command.cost())) {
            return new RunOutcome.CannotAfford(command.cost());
        }
        return null;
    }

    /** Read the backend's state and log only its transitions, so a long outage costs one warning, not one per run. */
    private boolean backendAnswers() {
        boolean available = fee.available();
        if (!available && backendMissing.compareAndSet(false, true)) {
            if (onBackendMissing == FeeBackendPolicy.DENY) {
                log.warn("the currency backend for custom command costs is missing; priced custom commands are"
                        + " refused until it returns (on-backend-missing = deny)");
            } else {
                log.warn("the currency backend for custom command costs is missing; priced custom commands run for"
                        + " free until it returns (on-backend-missing = allow)");
            }
        } else if (available && backendMissing.compareAndSet(true, false)) {
            log.info("the currency backend for custom command costs is available again; priced custom commands"
                    + " charge their cost again");
        }
        return available;
    }

    /** Whether this run has to pay: the definition carries a price and the actor holds no bypass node. */
    private boolean charged(CustomCommand command, PlayerRef actor) {
        return command.charged() && !permissions.has(actor, COST_BYPASS);
    }

    /** Report {@code outcome} once and hand it back, so every return path reports exactly one outcome. */
    private RunOutcome report(PlayerRef actor, CustomCommand command, RunOutcome outcome) {
        feedback.report(actor, command, outcome);
        return outcome;
    }

    /** The warmup a command declares, as the shared port's kind; kept public for the movement tracker. */
    public static Warmups.WarmupKind warmupKind(Duration warmup) {
        Objects.requireNonNull(warmup, "warmup");
        return new Warmups.WarmupKind(WARMUP_FEATURE, warmup.toSeconds());
    }
}
