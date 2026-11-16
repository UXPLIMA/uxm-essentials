package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import com.uxplima.uxmessentials.customcommands.adapter.inbound.listener.CommandWarmupTracker;
import com.uxplima.uxmessentials.customcommands.application.FeeBackendPolicy;
import com.uxplima.uxmessentials.customcommands.application.RunCustomCommand;
import com.uxplima.uxmessentials.customcommands.application.RunOutcome;
import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.application.port.RunFeedback;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CommandLiteral;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.adapter.outbound.warmup.SchedulerWarmups;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A player who runs a paid custom command again while its warmup is still counting down, through the real use case,
 * the tracking decorator, the kernel countdown and the tracker the move listener reads.
 */
class TrackingCommandWarmupsTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e5"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000005a77"), "Sam");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), "world");
    private static final Position STANDING = new Position(WORLD, 10.5, 64, 10.5, 0f, 0f);
    private static final Position WALKED_OFF = new Position(WORLD, 14.5, 64, 10.5, 0f, 0f);

    private static final double REWARD_PRICE = 100;
    private static final double KIT_PRICE = 40;
    private static final CustomCommand REWARD = paidWithWarmup("reward", REWARD_PRICE);
    private static final CustomCommand KIT = paidWithWarmup("kit", KIT_PRICE);

    private static final String STARTED = "WarmupStarted";
    private static final String REPLACED = "WarmupReplaced";
    private static final String MOVED = "WarmupCancelled";
    private static final String RAN = "Ok";

    private final Countdowns scheduler = new Countdowns();
    private final Nodes nodes = new Nodes();
    private final Purse purse = new Purse();
    private final Actions actions = new Actions();
    private final Feedback feedback = new Feedback();
    private final CommandWarmupTracker tracker = new CommandWarmupTracker();
    private final Warnings log = new Warnings();
    // The tightest ceiling an operator can set, so a repeat that counted against depth would be refused.
    private final ChainDepth depth = new ChainDepth(1);
    private final RunCustomCommand runner = new RunCustomCommand(
            nodes,
            new NoCooldowns(),
            new TrackingCommandWarmups(new SchedulerWarmups(scheduler, nodes), tracker, who -> Optional.of(STANDING)),
            actions,
            (actor, requirements, arguments) -> true,
            purse,
            FeeBackendPolicy.DENY,
            feedback,
            depth,
            log);

    private static CustomCommand paidWithWarmup(String id, double price) {
        return new CustomCommand(
                CustomCommandId.of(id),
                CommandLiteral.of(id),
                Optional.empty(),
                Optional.empty(),
                true,
                "",
                Optional.empty(),
                Duration.ZERO,
                Duration.ofSeconds(3),
                price,
                List.of(),
                List.of(),
                ActionChain.empty(),
                ActionChain.empty());
    }

    private RunOutcome run(CustomCommand command) {
        return run(command, ALEX);
    }

    private RunOutcome run(CustomCommand command, PlayerRef who) {
        return runner.run(command, who, false, Map.of());
    }

    /** The server's move event for {@code who} walking from {@link #STANDING} to {@code to}. */
    private static PlayerMoveEvent moveEvent(PlayerRef who, Position to) {
        World world = fake(World.class, method -> switch (method) {
            case "getUID" -> WORLD.uid();
            case "getName" -> WORLD.name();
            default -> throw new UnsupportedOperationException(method);
        });
        Player player = fake(Player.class, method -> switch (method) {
            case "getUniqueId" -> who.uuid();
            default -> throw new UnsupportedOperationException(method);
        });
        return new PlayerMoveEvent(
                player,
                new Location(world, STANDING.x(), STANDING.y(), STANDING.z()),
                new Location(world, to.x(), to.y(), to.z()));
    }

    private interface Answer {
        Object answer(String method);
    }

    private static <T> T fake(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(
                TrackingCommandWarmupsTest.class.getClassLoader(),
                new Class<?>[] {type},
                (self, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    case "toString" -> type.getSimpleName();
                    default -> answer.answer(method.getName());
                }));
    }

    /** Exactly one chain ran, and it was {@code id}'s. */
    private void assertRan(String id) {
        assertThat(actions.runs).isEqualTo(1);
        assertThat(feedback.ran).containsExactly(id);
    }

    @Test
    void aSecondRunDuringTheWarmupReplacesTheFirstSoTheCommandRunsAndChargesOnce() {
        run(REWARD);
        run(REWARD);

        scheduler.elapse();

        assertRan("reward");
        assertThat(purse.charged).isEqualTo(REWARD_PRICE);
        assertThat(feedback.reported)
                .as("the player hears the earlier run was replaced, not that they moved")
                .containsExactly(STARTED, REPLACED, STARTED, RAN);
    }

    @Test
    void walkingOffAfterTheRepeatStopsItAndNothingRunsOrCharges() {
        run(REWARD);
        run(REWARD);

        tracker.onMove(ALEX.uuid(), WALKED_OFF);
        scheduler.elapse();

        assertThat(actions.runs)
                .as("the first warmup was not left running out of the move listener's reach")
                .isZero();
        assertThat(purse.charged).isZero();
        assertThat(feedback.reported).containsExactly(STARTED, REPLACED, STARTED, MOVED);
    }

    @Test
    void aMoveCancelLeavesTheMoveListenerWatchingTheNextPlayer() {
        run(REWARD, ALEX);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        run(REWARD, SAM);
        tracker.onMove(moveEvent(SAM, WALKED_OFF));
        scheduler.elapse();

        assertThat(actions.runs)
                .as("the second player's move still reached their warmup after the first player's cancel")
                .isZero();
        assertThat(purse.charged).isZero();
        assertThat(feedback.reported).containsExactly(STARTED, MOVED, STARTED, MOVED);
    }

    @Test
    void aPlayerWhoQuitsBeforeTheCountdownEndsIsNotChargedWhenTheyComeBack() {
        run(REWARD);

        tracker.quit(ALEX.uuid());
        scheduler.elapse();

        assertThat(actions.runs).isZero();
        assertThat(purse.charged).isZero();
        assertThat(depth.tracked()).isZero();
    }

    @Test
    void anotherCommandWithAWarmupReplacesTheWaitingOne() {
        run(REWARD);
        run(KIT);

        scheduler.elapse();

        assertRan("kit");
        assertThat(purse.charged).isEqualTo(KIT_PRICE);
    }

    @Test
    void aWarmupWhoseCountdownEndedButHasNotRunYetLosesToTheReplacement() {
        run(REWARD);
        scheduler.elapseCountdownsOnly();

        run(REWARD);
        scheduler.elapse();

        assertRan("reward");
        assertThat(purse.charged).isEqualTo(REWARD_PRICE);
    }

    @Test
    void aCommandThatAlreadyRanIsNotCancelledAndTheNextRunStaysCancellable() {
        run(REWARD);
        scheduler.elapse();

        run(REWARD);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);
        scheduler.elapse();

        assertRan("reward");
        assertThat(purse.charged).isEqualTo(REWARD_PRICE);
        assertThat(feedback.reported).containsExactly(STARTED, RAN, STARTED, MOVED);
    }

    @Test
    void aRepeatThatSkipsItsWarmupStillStopsTheEarlierOne() {
        run(REWARD);
        nodes.held.add("uxmessentials.bypass.warmup.customcommand");

        run(REWARD);
        scheduler.elapse();

        assertThat(actions.runs)
                .as("the earlier warmup does not run after the instant one")
                .isEqualTo(1);
        assertThat(purse.charged).isEqualTo(REWARD_PRICE);
        assertThat(feedback.reported)
                .as("the instant run is reported as run, not as a countdown")
                .containsExactly(STARTED, REPLACED, RAN);
    }

    @Test
    void aRepeatAtTheDepthCeilingReplacesTheEarlierWarmupInsteadOfBeingRefused() {
        assertThat(run(REWARD)).isInstanceOf(RunOutcome.WarmupStarted.class);
        assertThat(run(REWARD)).isInstanceOf(RunOutcome.WarmupStarted.class);
        assertThat(run(REWARD))
                .as("each repeat released the depth of the warmup it replaced")
                .isInstanceOf(RunOutcome.WarmupStarted.class);

        scheduler.elapse();

        assertRan("reward");
        assertThat(purse.charged).isEqualTo(REWARD_PRICE);
        assertThat(feedback.reported).containsExactly(STARTED, REPLACED, STARTED, REPLACED, STARTED, RAN);
        assertThat(log.warnings).as("nothing is logged as a runaway chain").isEmpty();
        assertThat(depth.tracked()).isZero();
    }

    @Test
    void aPlayerWhoSkipsTheWarmupIsNotToldToStandStillAfterTheCommandRan() {
        nodes.held.add("uxmessentials.bypass.warmup.customcommand");

        RunOutcome outcome = run(REWARD);

        assertThat(outcome).isInstanceOf(RunOutcome.Ok.class);
        assertThat(feedback.reported).containsExactly(RAN);
        assertRan("reward");
        assertThat(depth.tracked()).isZero();
    }

    /**
     * A scheduler with the countdown split from the run: {@link #elapseCountdownsOnly()} ends every countdown and
     * leaves the hop to the player's thread queued, the moment a replacement can still race the completion.
     */
    private static final class Countdowns implements Scheduler {

        private final List<Runnable> countdowns = new ArrayList<>();
        private final Deque<Runnable> playerThread = new ArrayDeque<>();

        void elapseCountdownsOnly() {
            List<Runnable> due = List.copyOf(countdowns);
            countdowns.clear();
            due.forEach(Runnable::run);
        }

        void elapse() {
            elapseCountdownsOnly();
            Runnable next;
            while ((next = playerThread.pollFirst()) != null) {
                next.run();
            }
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            playerThread.add(task);
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            countdowns.add(task);
        }
    }

    /** A player holding only the nodes a test grants, so every warmup is the declared length unless bypassed. */
    private static final class Nodes implements Permissions {

        private final Set<String> held = new HashSet<>();

        @Override
        public boolean has(PlayerRef who, String node) {
            return held.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    /** Cooldowns that never hold anyone back, keeping the test on the warmup alone. */
    private static final class NoCooldowns implements Cooldowns {

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            // Nothing to remember.
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            // Nothing to remember.
        }
    }

    /** A currency that always answers and keeps a running total of what it took. */
    private static final class Purse implements CommandFee {

        private double charged;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean canAfford(PlayerRef who, double amount) {
            return true;
        }

        @Override
        public boolean charge(PlayerRef who, double amount) {
            charged += amount;
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }

    /** Counts how many chains ran. */
    private static final class Actions implements ActionRunner {

        private int runs;

        @Override
        public void run(PlayerRef actor, ActionChain chain, Map<String, String> arguments) {
            runs++;
        }
    }

    /**
     * Keeps each reported outcome by its case name, in the order the player would read them, and the id of every
     * command reported as run.
     */
    private static final class Feedback implements RunFeedback {

        private final List<String> reported = new ArrayList<>();
        private final List<String> ran = new ArrayList<>();

        @Override
        public void report(PlayerRef who, CustomCommand command, RunOutcome outcome) {
            reported.add(outcome.getClass().getSimpleName());
            if (outcome instanceof RunOutcome.Ok) {
                ran.add(command.id().value());
            }
        }
    }

    /** A logger that keeps only its warnings. */
    private static final class Warnings implements Logger {

        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
