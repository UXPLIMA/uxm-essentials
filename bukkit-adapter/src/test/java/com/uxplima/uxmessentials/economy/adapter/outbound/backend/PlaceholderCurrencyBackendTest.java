package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
        Scheduler scheduler = mock(Scheduler.class);
        CurrencyBackend backend = backend(new RecordingLogger(), scheduler);

        var result = backend.debit(ALICE, Money.of(CROWNS, new BigDecimal("50")));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        verify(scheduler, never()).onGlobal(any());
    }

    @Test
    void anUnparseableBalanceYieldsZeroAndLogsOnce() {
        RecordingLogger logger = new RecordingLogger();
        CurrencyBackend backend = backend(logger, mock(Scheduler.class));

        assertThat(backend.balance(ALICE, CROWNS)).isEqualTo(Money.zero(CROWNS));
        assertThat(backend.balance(ALICE, CROWNS)).isEqualTo(Money.zero(CROWNS));

        assertThat(logger.warnings).isEqualTo(1);
        assertThat(logger.lastWarning).contains("unparseable_balance");
    }

    @Test
    void aConfiguredCurrencyWithoutTheIntegralKeyIsDecimal() {
        CurrencyBackend backend = backend(new RecordingLogger(), mock(Scheduler.class));

        assertThat(backend.precision()).isEqualTo(Precision.DECIMAL);
    }

    @Test
    void aSufficientBalanceDispatchesTheTakeCommandOnce() {
        Server server = tickThreadServer();
        Scheduler scheduler = mock(Scheduler.class);
        PlaceholderCurrencyBackend backend =
                backend((player, placeholder) -> "100", Precision.INTEGRAL, server, scheduler);

        var result = backend.debit(ALICE, Money.of(CROWNS, new BigDecimal("50")));

        assertThat(result.isOk()).isTrue();
        assertThat(dispatched(server, scheduler)).isEqualTo("myeco take Alice 50");
    }

    @Test
    void creditDispatchesTheGiveCommandOnce() {
        Server server = tickThreadServer();
        Scheduler scheduler = mock(Scheduler.class);
        PlaceholderCurrencyBackend backend =
                backend((player, placeholder) -> "0", Precision.INTEGRAL, server, scheduler);

        backend.credit(ALICE, Money.of(CROWNS, new BigDecimal("25")));

        assertThat(dispatched(server, scheduler)).isEqualTo("myeco give Alice 25");
    }

    @Test
    void integralRoundsTheAmountAtTheBoundaryWhileDecimalKeepsItsFraction() {
        assertThat(creditedAmount(Precision.INTEGRAL)).isEqualTo("51");
        assertThat(creditedAmount(Precision.DECIMAL)).isEqualTo("50.75");
    }

    /** The amount as it reaches the give command for a 50.75 credit at {@code precision}. */
    private String creditedAmount(Precision precision) {
        Server server = tickThreadServer();
        Scheduler scheduler = mock(Scheduler.class);
        PlaceholderCurrencyBackend backend = backend((player, placeholder) -> "0", precision, server, scheduler);

        backend.credit(ALICE, Money.of(CROWNS, new BigDecimal("50.75")));

        return dispatched(server, scheduler).substring("myeco give Alice ".length());
    }

    /** Capture the single console command the backend hands to the global-thread hop and render it verbatim. */
    private String dispatched(Server server, Scheduler scheduler) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(1)).onGlobal(task.capture());
        task.getValue().run();
        ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
        verify(server).dispatchCommand(any(), command.capture());
        return command.getValue();
    }

    /** A backend for the {@code crowns} entry; PlaceholderAPI is absent, so its balance placeholder never resolves. */
    private CurrencyBackend backend(Logger log, Scheduler scheduler) {
        return PlaceholderCurrencyBackend.fromConfig("crowns", CONFIG, tickThreadServer(), log, scheduler);
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

    /** A server that reports the calling thread as a tick thread, so a balance read resolves inline without a hop. */
    private static Server tickThreadServer() {
        Server server = mock(Server.class);
        when(server.isPrimaryThread()).thenReturn(true);
        return server;
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
