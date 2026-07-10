package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;

import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The console-driven currency backend. Its command rendering accepts both amount tokens; a template carrying neither
 * is a load-time error; a debit refuses below the balance and dispatches nothing; and a balance that will not parse
 * degrades to zero with a single log line rather than throwing into the command that read it.
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

    /** A backend for the {@code crowns} entry; PlaceholderAPI is absent, so its balance placeholder never resolves. */
    private CurrencyBackend backend(Logger log, Scheduler scheduler) {
        return PlaceholderCurrencyBackend.fromConfig("crowns", CONFIG, mock(Server.class), log, scheduler);
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
