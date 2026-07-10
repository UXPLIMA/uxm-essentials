package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

/**
 * The console-driven currency backend. Its command rendering accepts both amount tokens; a template carrying neither
 * is a load-time error; a sufficient balance dispatches its take (or give) command once with the player and amount
 * substituted; a debit refuses below the balance and dispatches nothing; an integral currency rounds the amount at
 * the boundary while a decimal one keeps its fraction; a currency that omits {@code integral} is decimal; and a
 * balance that will not parse degrades to zero with a single log line rather than throwing into the command.
 *
 * <p>Both the balance read and the command dispatch are global-region work, so off that thread the backend hops
 * through the {@link Scheduler} port. The fake below runs the scheduled task the moment it is handed over, the way a
 * real global thread eventually would, which is what lets these tests observe the hop rather than a timeout.
 */
class PlaceholderCurrencyBackendTest {

    private static final Currency CROWNS =
            Currency.builder(CurrencyId.of("crowns")).build();
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void acceptsBothAmountAndPriceTokens() {
        assertThat(PlaceholderCurrencyBackend.renderCommand("eco give %player% %amount%", "Alice", "50"))
                .isEqualTo("eco give Alice 50");
        assertThat(PlaceholderCurrencyBackend.renderCommand("eco give %player% %price%", "Alice", "50"))
                .isEqualTo("eco give Alice 50");
    }

    @Test
    void aCommandCarryingNoAmountTokenIsAStartupError() {
        assertThatThrownBy(
                        () -> PlaceholderCurrencyBackend.validateCommand("crowns", "give-command", "eco give %player%"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("crowns")
                .hasMessageContaining("give-command");
    }

    @Test
    void debitBelowTheBalanceReturnsInsufficientFundsAndDispatchesNothing() {
        Server server = mock(Server.class);
        CurrencyBackend backend = configured(new RecordingLogger(), new RecordingScheduler(), server);

        var result = backend.debit(ALICE, Money.of(CROWNS, new BigDecimal("50")));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        // The balance read legitimately hops to the global region; what must not happen is the take command.
        verify(server, never()).dispatchCommand(any(), any());
    }

    @Test
    void anUnparseableBalanceYieldsZeroAndLogsOnce() {
        RecordingLogger logger = new RecordingLogger();
        CurrencyBackend backend = configured(logger, new RecordingScheduler(), mock(Server.class));

        assertThat(backend.balance(ALICE, CROWNS)).isEqualTo(Money.zero(CROWNS));
        assertThat(backend.balance(ALICE, CROWNS)).isEqualTo(Money.zero(CROWNS));

        assertThat(logger.warnings).isEqualTo(1);
        assertThat(logger.lastWarning).contains("unparseable_balance");
    }

    @Test
    void aConfiguredCurrencyWithoutTheIntegralKeyIsDecimal() {
        CurrencyBackend backend = configured(new RecordingLogger(), new RecordingScheduler(), mock(Server.class));

        assertThat(backend.precision()).isEqualTo(Precision.DECIMAL);
    }

    @Test
    void aSufficientBalanceDispatchesTheTakeCommandOnce() {
        Server server = mock(Server.class);
        PlaceholderCurrencyBackend backend =
                backend((player, placeholder) -> "100", Precision.INTEGRAL, server, new RecordingScheduler());

        var result = backend.debit(ALICE, Money.of(CROWNS, new BigDecimal("50")));

        assertThat(result.isOk()).isTrue();
        assertThat(dispatched(server)).isEqualTo("myeco take Alice 50");
    }

    @Test
    void creditDispatchesTheGiveCommandOnce() {
        Server server = mock(Server.class);
        PlaceholderCurrencyBackend backend =
                backend((player, placeholder) -> "0", Precision.INTEGRAL, server, new RecordingScheduler());

        backend.credit(ALICE, Money.of(CROWNS, new BigDecimal("25")));

        assertThat(dispatched(server)).isEqualTo("myeco give Alice 25");
    }

    @Test
    void integralRoundsTheAmountAtTheBoundaryWhileDecimalKeepsItsFraction() {
        assertThat(creditedAmount(Precision.INTEGRAL)).isEqualTo("51");
        assertThat(creditedAmount(Precision.DECIMAL)).isEqualTo("50.75");
    }

    @Test
    void aCallerAlreadyOnTheGlobalThreadReadsTheBalanceWithoutScheduling() {
        RecordingScheduler onGlobalThread = new RecordingScheduler();
        onGlobalThread.owns = true;
        PlaceholderCurrencyBackend backend =
                backend((player, placeholder) -> "42", Precision.DECIMAL, mock(Server.class), onGlobalThread);

        assertThat(backend.balance(ALICE, CROWNS).amount()).isEqualByComparingTo("42");

        // Scheduling onto the global region from the global region and then blocking on the result would deadlock.
        assertThat(onGlobalThread.globalCalls).isZero();
    }

    /** The amount as it reaches the give command for a 50.75 credit at {@code precision}. */
    private String creditedAmount(Precision precision) {
        Server server = mock(Server.class);
        PlaceholderCurrencyBackend backend =
                backend((player, placeholder) -> "0", precision, server, new RecordingScheduler());

        backend.credit(ALICE, Money.of(CROWNS, new BigDecimal("50.75")));

        return dispatched(server).substring("myeco give Alice ".length());
    }

    /** The single console command the backend sent, verbatim. */
    private String dispatched(Server server) {
        ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
        verify(server).dispatchCommand(any(), command.capture());
        return command.getValue();
    }

    /** A backend for the {@code crowns} entry; PlaceholderAPI is absent, so its balance placeholder never resolves. */
    private CurrencyBackend configured(Logger log, Scheduler scheduler, Server server) {
        return PlaceholderCurrencyBackend.fromConfig("crowns", CONFIG, server, log, scheduler);
    }

    /** A backend whose balance reader is supplied directly, so the positive path runs without a live PlaceholderAPI. */
    private PlaceholderCurrencyBackend backend(
            PlaceholderCurrencyBackend.BalanceReader reader, Precision precision, Server server, Scheduler scheduler) {
        return new PlaceholderCurrencyBackend(
                "crowns",
                "%myeconomy_balance%",
                "myeco give %player% %amount%",
                "myeco take %player% %amount%",
                false,
                precision,
                server,
                new RecordingLogger(),
                scheduler,
                reader);
    }

    /**
     * Runs the scheduled task immediately, so the backend's future completes instead of timing out. {@code owns}
     * drives the deadlock guard that decides whether the backend hops at all.
     */
    private static final class RecordingScheduler implements Scheduler {
        private int globalCalls;
        private boolean owns;

        @Override
        public boolean onGlobalThread() {
            return owns;
        }

        @Override
        public void onGlobal(Runnable task) {
            globalCalls++;
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException("unused");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException("unused");
        }

        @Override
        public void async(Runnable task) {
            throw new UnsupportedOperationException("unused");
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("unused");
        }
    }

    /** Serves the {@code backends.placeholder.crowns} block, everything else falls through to the caller's default. */
    private static final ConfigStore CONFIG = new ConfigStore() {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return switch (path) {
                case "backends.placeholder.crowns.balance-placeholder" -> "%myeconomy_balance%";
                case "backends.placeholder.crowns.give-command" -> "myeco give %player% %amount%";
                case "backends.placeholder.crowns.take-command" -> "myeco take %player% %amount%";
                default -> fallback;
            };
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    };

    /** Counts warnings so the once-only degrade is observable; the other levels are unused here. */
    private static final class RecordingLogger implements Logger {
        private int warnings;
        private String lastWarning = "";

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings++;
            lastWarning = message;
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
