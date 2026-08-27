package com.uxplima.uxmessentials.customcommands.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.application.port.RequirementCheck;
import com.uxplima.uxmessentials.customcommands.application.port.RunFeedback;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CommandLiteral;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The gate order of a custom command run, gate by gate: who may ask, what they must hold, how deep a chain may
 * call itself, what the definition requires, what it costs and how long the wait is. The fakes record what each
 * gate did, so a change that runs a gate out of turn (charging before a warmup, stamping a refused run) fails here
 * rather than on a live server.
 */
class RunCustomCommandTest {

    private final FakePermissions permissions = new FakePermissions();
    private final FakeCooldowns cooldowns = new FakeCooldowns();
    private final FakeWarmups warmups = new FakeWarmups();
    private final RecordingActions actions = new RecordingActions();
    private final FakeRequirements requirements = new FakeRequirements();
    private final FakeFee fee = new FakeFee();
    private final RecordingFeedback feedback = new RecordingFeedback();
    private final PlayerRef steve = new PlayerRef(UUID.randomUUID(), "Steve");

    private RunCustomCommand useCase(int maxDepth) {
        return new RunCustomCommand(
                permissions,
                cooldowns,
                warmups,
                actions,
                requirements,
                fee,
                feedback,
                new ChainDepth(maxDepth),
                new SilentLogger());
    }

    @Test
    void runsTheChainWhenEveryGateOpens() {
        assertThat(useCase(5).run(command().build(), steve, false, Map.of())).isInstanceOf(RunOutcome.Ok.class);
        assertThat(actions.chains()).hasSize(1);
    }

    @Test
    void turnsTheConsoleAwayWhenTheFileForbidsIt() {
        CustomCommand command = command().consoleAllowed(false).build();

        assertThat(useCase(5).run(command, PlayerRef.system("CONSOLE"), true, Map.of()))
                .isInstanceOf(RunOutcome.ConsoleDenied.class);
        assertThat(actions.chains()).isEmpty();
    }

    @Test
    void refusesASenderWhoLacksTheCommandsOwnPermission() {
        CustomCommand command =
                command().permission("uxmessentials.customcommand.odul").build();

        assertThat(useCase(5).run(command, steve, false, Map.of())).isInstanceOf(RunOutcome.NoPermission.class);
    }

    @Test
    void theConsoleClearsAPermissionGateItCannotHold() {
        CustomCommand command =
                command().permission("uxmessentials.customcommand.odul").build();

        assertThat(useCase(5).run(command, PlayerRef.system("CONSOLE"), true, Map.of()))
                .isInstanceOf(RunOutcome.Ok.class);
    }

    @Test
    void refusesAChainThatReEntersPastTheConfiguredDepth() {
        RunCustomCommand useCase = useCase(1);
        CustomCommand command = command().build();
        actions.onRun(() ->
                assertThat(useCase.run(command, steve, false, Map.of())).isInstanceOf(RunOutcome.DepthExceeded.class));

        assertThat(useCase.run(command, steve, false, Map.of())).isInstanceOf(RunOutcome.Ok.class);
    }

    @Test
    void runsTheDenyChainWhenTheRequirementsFail() {
        requirements.fail();
        CustomCommand command = command()
                .requirements(List.of("has-money:100"))
                .requirementDeny("message:no")
                .build();

        assertThat(useCase(5).run(command, steve, false, Map.of())).isInstanceOf(RunOutcome.RequirementsFailed.class);
        assertThat(actions.chains()).hasSize(1);
        assertThat(actions.chains().get(0).steps().get(0).token()).isEqualTo("message:no");
    }

    @Test
    void reportsTheRemainingWaitWhenTheCommandIsCoolingDown() {
        cooldowns.remaining(Duration.ofSeconds(12));

        RunOutcome outcome =
                useCase(5).run(command().cooldown(Duration.ofSeconds(30)).build(), steve, false, Map.of());

        assertThat(outcome).isEqualTo(new RunOutcome.OnCooldown(Duration.ofSeconds(12)));
        assertThat(actions.chains()).isEmpty();
    }

    @Test
    void theCooldownBypassNodeSkipsTheGateAndRunsTheChain() {
        permissions.grant(steve, RunCustomCommand.COOLDOWN_BYPASS);
        cooldowns.remaining(Duration.ofSeconds(12));

        assertThat(useCase(5).run(command().cooldown(Duration.ofSeconds(30)).build(), steve, false, Map.of()))
                .isInstanceOf(RunOutcome.Ok.class);
    }

    @Test
    void aWarmupDefersTheChainAndTheCostUntilItCompletes() {
        CustomCommand command =
                command().warmup(Duration.ofSeconds(3)).cost(100).build();

        assertThat(useCase(5).run(command, steve, false, Map.of()))
                .isEqualTo(new RunOutcome.WarmupStarted(Duration.ofSeconds(3)));
        assertThat(actions.chains()).isEmpty();
        assertThat(fee.charged()).isZero();

        warmups.complete();

        assertThat(actions.chains()).hasSize(1);
        assertThat(fee.charged()).isEqualTo(100);
    }

    @Test
    void aCancelledWarmupNeverChargesAndReportsTheCancellation() {
        CustomCommand command =
                command().warmup(Duration.ofSeconds(3)).cost(100).build();
        useCase(5).run(command, steve, false, Map.of());

        warmups.cancel();

        assertThat(fee.charged()).isZero();
        assertThat(actions.chains()).isEmpty();
        assertThat(feedback.outcomes()).last().isInstanceOf(RunOutcome.WarmupCancelled.class);
    }

    @Test
    void aPlayerWhoCannotPayIsRefusedBeforeTheChainRuns() {
        fee.balance(10);

        assertThat(useCase(5).run(command().cost(100).build(), steve, false, Map.of()))
                .isEqualTo(new RunOutcome.CannotAfford(100));
        assertThat(actions.chains()).isEmpty();
    }

    @Test
    void theCostBypassNodeRunsTheChainForFree() {
        permissions.grant(steve, RunCustomCommand.COST_BYPASS);
        fee.balance(0);

        assertThat(useCase(5).run(command().cost(100).build(), steve, false, Map.of()))
                .isInstanceOf(RunOutcome.Ok.class);
        assertThat(fee.charged()).isZero();
    }

    @Test
    void theCooldownIsStampedExactlyOncePerAcceptedRun() {
        useCase(5).run(command().cooldown(Duration.ofSeconds(30)).build(), steve, false, Map.of());

        assertThat(cooldowns.stamps()).containsExactly("customcommand.odul");
    }

    @Test
    void everyOutcomeIsReportedThroughTheFeedbackPortExactlyOnce() {
        useCase(5).run(command().build(), steve, false, Map.of());

        assertThat(feedback.outcomes()).hasSize(1);
    }

    @Test
    void theDryRunNamesTheFirstClosedGateAndChangesNothing() {
        cooldowns.remaining(Duration.ofSeconds(12));

        RunOutcome outcome =
                useCase(5).dryRun(command().cooldown(Duration.ofSeconds(30)).build(), steve, false);

        assertThat(outcome).isInstanceOf(RunOutcome.OnCooldown.class);
        assertThat(cooldowns.stamps()).isEmpty();
        assertThat(fee.charged()).isZero();
        assertThat(actions.chains()).isEmpty();
        assertThat(feedback.outcomes()).isEmpty();
    }

    private static Builder command() {
        return new Builder();
    }

    /** Builds the worked {@code odul} definition with every gate open, one call per gate a test wants closed. */
    private static final class Builder {

        private Optional<String> permission = Optional.empty();
        private boolean consoleAllowed = true;
        private Duration cooldown = Duration.ZERO;
        private Duration warmup = Duration.ZERO;
        private double cost;
        private List<String> requirements = List.of();
        private ActionChain requirementDeny = ActionChain.empty();

        Builder permission(String node) {
            this.permission = Optional.of(node);
            return this;
        }

        Builder consoleAllowed(boolean allowed) {
            this.consoleAllowed = allowed;
            return this;
        }

        Builder cooldown(Duration cooldown) {
            this.cooldown = cooldown;
            return this;
        }

        Builder warmup(Duration warmup) {
            this.warmup = warmup;
            return this;
        }

        Builder cost(double cost) {
            this.cost = cost;
            return this;
        }

        Builder requirements(List<String> requirements) {
            this.requirements = requirements;
            return this;
        }

        Builder requirementDeny(String token) {
            this.requirementDeny = ActionChain.of(List.of(token), ActionChain.ChainLimits.defaults());
            return this;
        }

        CustomCommand build() {
            return new CustomCommand(
                    CustomCommandId.of("odul"),
                    CommandLiteral.of("odul"),
                    permission,
                    Optional.empty(),
                    consoleAllowed,
                    "Reward a player",
                    Optional.empty(),
                    cooldown,
                    warmup,
                    cost,
                    List.of(),
                    requirements,
                    requirementDeny,
                    ActionChain.of(List.of("message:done"), ActionChain.ChainLimits.defaults()));
        }
    }

    private static final class FakePermissions implements Permissions {

        private final Set<String> granted = new HashSet<>();

        void grant(PlayerRef who, String node) {
            granted.add(who.uuid() + "|" + node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(who.uuid() + "|" + node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class FakeCooldowns implements Cooldowns {

        private final List<String> stamps = new ArrayList<>();
        private @Nullable Duration remaining;

        void remaining(Duration remaining) {
            this.remaining = remaining;
        }

        List<String> stamps() {
            return stamps;
        }

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return remaining == null ? Result.ok() : Result.err(remaining);
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            stamps.add(label);
        }
    }

    /** A warmup port that hands back the callbacks so a test decides when the wait ends and how. */
    private static final class FakeWarmups implements Warmups {

        private @Nullable Runnable onComplete;
        private @Nullable Runnable onCancel;

        void complete() {
            Runnable callback = onComplete;
            onComplete = null;
            onCancel = null;
            if (callback != null) {
                callback.run();
            }
        }

        void cancel() {
            Runnable callback = onCancel;
            onComplete = null;
            onCancel = null;
            if (callback != null) {
                callback.run();
            }
        }

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            this.onComplete = onComplete;
            this.onCancel = onCancel;
            return new CompletedWarmup(who);
        }
    }

    private static final class RecordingActions implements ActionRunner {

        private final List<ActionChain> chains = new ArrayList<>();
        private @Nullable Runnable duringRun;

        void onRun(Runnable duringRun) {
            this.duringRun = duringRun;
        }

        List<ActionChain> chains() {
            return chains;
        }

        @Override
        public void run(PlayerRef actor, ActionChain chain, Map<String, String> arguments) {
            chains.add(chain);
            Runnable hook = duringRun;
            duringRun = null;
            if (hook != null) {
                hook.run();
            }
        }
    }

    private static final class FakeRequirements implements RequirementCheck {

        private boolean pass = true;

        void fail() {
            pass = false;
        }

        @Override
        public boolean passes(PlayerRef actor, List<String> requirements, Map<String, String> arguments) {
            return pass;
        }
    }

    private static final class FakeFee implements CommandFee {

        private double balance = 1_000;
        private double charged;

        void balance(double balance) {
            this.balance = balance;
        }

        double charged() {
            return charged;
        }

        @Override
        public boolean canAfford(PlayerRef who, double amount) {
            return balance >= amount;
        }

        @Override
        public boolean charge(PlayerRef who, double amount) {
            balance -= amount;
            charged += amount;
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }

    private static final class RecordingFeedback implements RunFeedback {

        private final List<RunOutcome> outcomes = new ArrayList<>();

        List<RunOutcome> outcomes() {
            return outcomes;
        }

        @Override
        public void report(PlayerRef who, CustomCommand command, RunOutcome outcome) {
            outcomes.add(outcome);
        }
    }

    private static final class SilentLogger implements Logger {

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
