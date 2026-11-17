package com.uxplima.uxmessentials.customcommands.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;

import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.application.port.RunFeedback;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ActionStep;
import com.uxplima.uxmessentials.customcommands.domain.ArgumentKind;
import com.uxplima.uxmessentials.customcommands.domain.ChainArguments;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CommandArgument;
import com.uxplima.uxmessentials.customcommands.domain.CommandLiteral;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkClaims;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkCooldowns;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MemoryClaimLedger;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RunCustomCommandTest {

    private static final double PRICE = 100;

    private final Purse purse = new Purse();
    private final Actions actions = new Actions();
    private final Feedback feedback = new Feedback();
    private final Log log = new Log();

    private RunCustomCommand runner(FeeBackendPolicy policy) {
        return runner(policy, new FakePermissions(), new ClockCooldowns(CLOCK));
    }

    private RunCustomCommand runner(FeeBackendPolicy policy, FakePermissions permissions, Cooldowns cooldowns) {
        return new RunCustomCommand(
                permissions,
                cooldowns,
                new NoWarmups(),
                actions,
                (actor, requirements, arguments) -> true,
                purse,
                policy,
                feedback,
                new ChainDepth(5),
                log);
    }

    /** Cooldowns kept for a network, with Alex's still being read until {@code scheduler} runs. */
    private static NetworkCooldowns networkCooldowns(FakePermissions permissions, QueuedScheduler scheduler) {
        NetworkClaims claims = new NetworkClaims(new MemoryClaimLedger(), scheduler, CLOCK, new RecordingLogger());
        claims.joined(ALEX.uuid());
        return new NetworkCooldowns(claims, permissions, CLOCK);
    }

    @Test
    void aLabelTierAddsAWaitToACommandDeclaredWithoutOne() {
        FakePermissions permissions =
                new FakePermissions().holdTier(ALEX, "uxmessentials.cooldown.customcommand.reward", 30);
        QueuedScheduler scheduler = new QueuedScheduler();
        RunCustomCommand runner = runner(FeeBackendPolicy.ALLOW, permissions, networkCooldowns(permissions, scheduler));
        scheduler.runAll();

        assertThat(run(runner)).isInstanceOf(RunOutcome.Ok.class);
        scheduler.runAll();

        assertThat(run(runner)).isInstanceOf(RunOutcome.OnCooldown.class);
        assertThat(actions.runs).isEqualTo(1);
    }

    @Test
    void aCommandNobodyWaitsForRunsBeforeTheActorsCooldownsAreRead() {
        FakePermissions permissions = new FakePermissions();
        RunCustomCommand runner =
                runner(FeeBackendPolicy.ALLOW, permissions, networkCooldowns(permissions, new QueuedScheduler()));

        assertThat(run(runner)).isInstanceOf(RunOutcome.Ok.class);
        assertThat(run(runner)).isInstanceOf(RunOutcome.Ok.class);
        assertThat(actions.runs).isEqualTo(2);
    }

    private static CustomCommand priced() {
        return new CustomCommand(
                CustomCommandId.of("reward"),
                CommandLiteral.of("reward"),
                Optional.empty(),
                Optional.empty(),
                true,
                "",
                Optional.empty(),
                Duration.ZERO,
                Duration.ZERO,
                PRICE,
                List.of(),
                List.of(),
                ActionChain.empty(),
                ActionChain.empty());
    }

    private RunOutcome run(RunCustomCommand runner) {
        return runner.run(priced(), ALEX, false, Map.of());
    }

    @Test
    void allowRunsAPricedCommandUnpaidWhileTheBackendIsMissing() {
        purse.available = false;

        RunOutcome outcome = run(runner(FeeBackendPolicy.ALLOW));

        assertThat(outcome).isInstanceOf(RunOutcome.Ok.class);
        assertThat(actions.runs).isEqualTo(1);
        assertThat(purse.charged).isZero();
    }

    @Test
    void denyRefusesAPricedCommandAndRunsNothingWhileTheBackendIsMissing() {
        purse.available = false;

        RunOutcome outcome = run(runner(FeeBackendPolicy.DENY));

        assertThat(outcome).isInstanceOf(RunOutcome.FeeBackendMissing.class);
        assertThat(feedback.reported).containsExactly(outcome);
        assertThat(actions.runs).isZero();
        assertThat(purse.charged).isZero();
    }

    @Test
    void aSelectorTypedForAConsoleLineIsRefusedBeforeAnythingIsSpentOrRun() {
        RunCustomCommand runner = runner(FeeBackendPolicy.ALLOW);

        RunOutcome outcome = runner.run(giving(), ALEX, false, Map.of("target", "@a", "args", "@a"));

        assertThat(outcome).isEqualTo(new RunOutcome.ArgumentRefused("target", false));
        assertThat(feedback.reported).containsExactly(outcome);
        assertThat(actions.runs).isZero();
        assertThat(purse.charged).isZero();
        assertThat(runner.run(giving(), ALEX, false, Map.of("target", "Steve", "args", "Steve")))
                .isInstanceOf(RunOutcome.Ok.class);
    }

    @Test
    void aChainIsHandedOnlyTheValuesItsCommandLinesAllow() {
        runner(FeeBackendPolicy.ALLOW).run(giving(), ALEX, false, Map.of("target", "Steve", "args", "Steve x"));

        ChainArguments handed = Objects.requireNonNull(actions.last, "no chain ran");
        assertThat(handed.typed()).containsEntry("args", "Steve x");
        assertThat(handed.forPrivilegedLines()).containsOnly(Map.entry("target", "Steve"));
    }

    /** A priced command that gives the named player a diamond from the console. */
    private static CustomCommand giving() {
        return new CustomCommand(
                CustomCommandId.of("gift"),
                CommandLiteral.of("gift"),
                Optional.empty(),
                Optional.empty(),
                true,
                "",
                Optional.empty(),
                Duration.ZERO,
                Duration.ZERO,
                PRICE,
                List.of(CommandArgument.of("target", ArgumentKind.PLAYER)),
                List.of(),
                ActionChain.empty(),
                new ActionChain(List.of(ActionStep.now("console: give %arg_target% diamond 1")), List.of()));
    }

    @Test
    void denyStillChargesWhenTheBackendAnswers() {
        RunOutcome outcome = run(runner(FeeBackendPolicy.DENY));

        assertThat(outcome).isInstanceOf(RunOutcome.Ok.class);
        assertThat(actions.runs).isEqualTo(1);
        assertThat(purse.charged).isEqualTo(PRICE);
    }

    @Test
    void aMissingBackendWarnsOnceAndItsReturnIsNotedOnce() {
        RunCustomCommand runner = runner(FeeBackendPolicy.ALLOW);
        purse.available = false;

        run(runner);
        run(runner);
        run(runner);
        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.getFirst()).contains("missing", "free");
        assertThat(log.infos).isEmpty();

        purse.available = true;
        run(runner);
        run(runner);
        assertThat(log.infos).hasSize(1);
        assertThat(log.warnings).hasSize(1);
        assertThat(purse.charged).isEqualTo(2 * PRICE);

        purse.available = false;
        run(runner);
        assertThat(log.warnings).hasSize(2);
    }

    @Test
    void anUnknownPolicyFallsBackToAllowAndNamesTheFileAndKey() {
        ConfigStore config = new OneValue("on-backend-missing", "refuse");

        CustomCommandsConfig read = CustomCommandsConfig.from(config, log);

        assertThat(read.onBackendMissing()).isEqualTo(FeeBackendPolicy.ALLOW);
        assertThat(log.warnings).hasSize(1);
        assertThat(log.warnings.getFirst()).contains("modules/customcommands/config.conf", "on-backend-missing");
    }

    /** A currency whose backend can be switched off, holding enough for any run. */
    private static final class Purse implements CommandFee {
        boolean available = true;
        double charged;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean canAfford(PlayerRef who, double amount) {
            return available;
        }

        @Override
        public boolean charge(PlayerRef who, double amount) {
            if (!available) {
                return false;
            }
            charged += amount;
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }

    private static final class Actions implements ActionRunner {
        int runs;

        @Nullable ChainArguments last;

        @Override
        public void run(PlayerRef actor, ActionChain chain, ChainArguments arguments) {
            runs++;
            last = arguments;
        }
    }

    private static final class Feedback implements RunFeedback {
        final List<RunOutcome> reported = new ArrayList<>();

        @Override
        public void report(PlayerRef who, CustomCommand command, RunOutcome outcome) {
            reported.add(outcome);
        }
    }

    /** The test commands carry no warmup, so a warmup that begins is a broken gate. */
    private static final class NoWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            throw new AssertionError("a command without a warmup started one");
        }
    }

    /** Keeps warnings and infos with their placeholders filled, the way an operator reads them. */
    private static final class Log implements Logger {
        final List<String> warnings = new ArrayList<>();
        final List<String> infos = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            infos.add(fill(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(fill(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String fill(String message, Object... args) {
            String filled = message;
            for (Object arg : args) {
                filled = filled.replaceFirst("\\{}", Matcher.quoteReplacement(String.valueOf(arg)));
            }
            return filled;
        }
    }

    /** A config tree holding one string; every other key reads its fallback. */
    private record OneValue(String key, String value) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return path.equals(key) ? value : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
