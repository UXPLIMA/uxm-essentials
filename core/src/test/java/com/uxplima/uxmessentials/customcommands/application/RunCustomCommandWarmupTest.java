package com.uxplima.uxmessentials.customcommands.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.application.port.RunFeedback;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ChainArguments;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CommandLiteral;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.FakePermissions;
import org.junit.jupiter.api.Test;

/**
 * What a custom command warmup does when the same player runs another command before it ends, through the use case
 * alone: the warmup port here keeps every warmup it is given and never stops one on its own.
 */
class RunCustomCommandWarmupTest {

    private static final double REWARD_PRICE = 100;
    private static final double KIT_PRICE = 40;
    private static final CustomCommand REWARD = command("reward", Duration.ofSeconds(3), Duration.ZERO, REWARD_PRICE);
    private static final CustomCommand KIT = command("kit", Duration.ofSeconds(3), Duration.ZERO, KIT_PRICE);
    private static final CustomCommand DAILY =
            command("daily", Duration.ofSeconds(3), Duration.ofMinutes(5), REWARD_PRICE);
    private static final CustomCommand HUB = command("hub", Duration.ZERO, Duration.ZERO, 0);
    private static final Map<String, String> RUN_KIT_THEN_REWARD = Map.of("chain", "kit then reward");

    private final HeldWarmups warmups = new HeldWarmups();
    private final Purse purse = new Purse();
    private final Feedback feedback = new Feedback();
    private final Gates gates = new Gates();
    private final AtomicReference<RunCustomCommand> self = new AtomicReference<>();

    private RunCustomCommand runner(ChainDepth depth) {
        RunCustomCommand runner = new RunCustomCommand(
                new FakePermissions(),
                gates,
                warmups,
                new ChainRunner(),
                (actor, requirements, arguments) -> gates.requirementsPass,
                purse,
                FeeBackendPolicy.DENY,
                feedback,
                depth,
                new QuietLog());
        self.set(runner);
        return runner;
    }

    private static CustomCommand command(String id, Duration warmup, Duration cooldown, double price) {
        return new CustomCommand(
                CustomCommandId.of(id),
                CommandLiteral.of(id),
                Optional.empty(),
                Optional.empty(),
                true,
                "",
                Optional.empty(),
                cooldown,
                warmup,
                price,
                List.of(),
                List.of(),
                ActionChain.empty(),
                ActionChain.empty());
    }

    @Test
    void aRepeatRefusedByItsRequirementsLeavesTheEarlierWarmupRunning() {
        ChainDepth depth = new ChainDepth(1);
        RunCustomCommand runner = runner(depth);
        runner.run(REWARD, ALEX, false, Map.of());

        gates.requirementsPass = false;
        assertThat(runner.run(REWARD, ALEX, false, Map.of())).isInstanceOf(RunOutcome.RequirementsFailed.class);
        warmups.completeAll();

        assertThat(feedback.reported).containsExactly("WarmupStarted", "RequirementsFailed", "Ok");
        assertThat(purse.charged).isEqualTo(REWARD_PRICE);
        assertThat(depth.tracked()).isZero();
    }

    @Test
    void aRepeatRefusedByItsCooldownLeavesTheEarlierWarmupRunning() {
        ChainDepth depth = new ChainDepth(1);
        RunCustomCommand runner = runner(depth);
        runner.run(KIT, ALEX, false, Map.of());

        gates.coolingDown = true;
        assertThat(runner.run(DAILY, ALEX, false, Map.of())).isInstanceOf(RunOutcome.OnCooldown.class);
        warmups.completeAll();

        assertThat(feedback.reported).containsExactly("WarmupStarted", "OnCooldown", "Ok");
        assertThat(feedback.ran).containsExactly("kit");
        assertThat(purse.charged).isEqualTo(KIT_PRICE);
        assertThat(depth.tracked()).isZero();
    }

    @Test
    void aWarmupCancelledWhenThePlayerLeftChargesNothingAndFreesTheirDepth() {
        ChainDepth depth = new ChainDepth(1);
        RunCustomCommand runner = runner(depth);
        runner.run(REWARD, ALEX, false, Map.of());

        warmups.cancelAll();

        assertThat(feedback.reported).containsExactly("WarmupStarted", "WarmupCancelled");
        assertThat(purse.charged).isZero();
        assertThat(depth.tracked()).isZero();
        assertThat(runner.run(REWARD, ALEX, false, Map.of()))
                .as("the next run starts fresh rather than replacing a warmup that is gone")
                .isInstanceOf(RunOutcome.WarmupStarted.class);
    }

    @Test
    void aPendingWarmupDoesNotCountAgainstAnUnrelatedCommandAtDepthOne() {
        ChainDepth depth = new ChainDepth(1);
        RunCustomCommand runner = runner(depth);
        runner.run(REWARD, ALEX, false, Map.of());

        assertThat(runner.run(HUB, ALEX, false, Map.of())).isInstanceOf(RunOutcome.Ok.class);
        warmups.completeAll();

        assertThat(feedback.reported).containsExactly("WarmupStarted", "Ok", "Ok");
        assertThat(purse.charged).isEqualTo(REWARD_PRICE);
        assertThat(depth.tracked()).isZero();
    }

    @Test
    void aCommandDispatchedInsideAnotherIsStillRefusedPastTheDepth() {
        ChainDepth depth = new ChainDepth(1);
        RunCustomCommand runner = runner(depth);

        runner.run(HUB, ALEX, false, RUN_KIT_THEN_REWARD);

        assertThat(feedback.reported).containsExactly("DepthExceeded", "DepthExceeded", "Ok");
        assertThat(purse.charged).isZero();
        assertThat(depth.tracked()).isZero();
    }

    @Test
    void aChainRunningTwoWarmupCommandsAtTheDefaultDepthLeavesOnlyTheLastWaiting() {
        ChainDepth depth = new ChainDepth(5);
        RunCustomCommand runner = runner(depth);

        assertThat(runner.run(HUB, ALEX, false, RUN_KIT_THEN_REWARD)).isInstanceOf(RunOutcome.Ok.class);
        warmups.completeAll();

        assertThat(feedback.reported).containsExactly("WarmupStarted", "WarmupReplaced", "WarmupStarted", "Ok", "Ok");
        assertThat(feedback.ran).containsExactly("hub", "reward");
        assertThat(purse.charged).isEqualTo(REWARD_PRICE);
        assertThat(depth.tracked()).isZero();
    }

    /** A run carrying the {@code chain} argument dispatches kit and then reward, as two {@code player:} steps would. */
    private final class ChainRunner implements ActionRunner {

        @Override
        public void run(PlayerRef actor, ActionChain chain, ChainArguments arguments) {
            if (!arguments.typed().containsKey("chain")) {
                return;
            }
            RunCustomCommand runner = Objects.requireNonNull(self.get(), "runner");
            runner.run(KIT, actor, false, Map.of());
            runner.run(REWARD, actor, false, Map.of());
        }
    }

    /** A warmup port that keeps every warmup until the test ends it, and never stops one by itself. */
    private static final class HeldWarmups implements Warmups {

        private final List<Held> held = new ArrayList<>();

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            return begin(who, kind, onComplete, onCancel, onCancel);
        }

        @Override
        public WarmupHandle begin(
                PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel, Runnable onSuperseded) {
            Held warmup = new Held(onComplete, onCancel, onSuperseded);
            held.add(warmup);
            return warmup;
        }

        void completeAll() {
            List.copyOf(held).forEach(Held::complete);
        }

        void cancelAll() {
            List.copyOf(held).forEach(Held::cancel);
        }
    }

    /** One warmup whose three endings are once-only, like the kernel's. */
    private static final class Held implements Warmups.WarmupHandle {

        private final Runnable onComplete;
        private final Runnable onCancel;
        private final Runnable onSuperseded;
        private boolean complete;
        private boolean cancelled;

        Held(Runnable onComplete, Runnable onCancel, Runnable onSuperseded) {
            this.onComplete = onComplete;
            this.onCancel = onCancel;
            this.onSuperseded = onSuperseded;
        }

        void complete() {
            if (!complete && !cancelled) {
                complete = true;
                onComplete.run();
            }
        }

        @Override
        public void cancel() {
            if (!complete && !cancelled) {
                cancelled = true;
                onCancel.run();
            }
        }

        @Override
        public void supersede() {
            if (!complete && !cancelled) {
                cancelled = true;
                onSuperseded.run();
            }
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /** The requirement and cooldown gates, each switchable by the test. */
    private static final class Gates implements Cooldowns {

        private boolean requirementsPass = true;
        private boolean coolingDown;

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
            return coolingDown ? Result.err(Duration.ofMinutes(1)) : Result.ok();
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

    /** Keeps each outcome by its case name and the id of every command reported as run. */
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

    /** A logger nobody reads. */
    private static final class QuietLog implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
