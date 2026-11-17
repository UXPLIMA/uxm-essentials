package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A hop to the player's thread that does not answer in time leaves the balance unknown, never zero, and a change that
 * timed out never lands late, so a caller told "not done" can safely try again.
 */
class ExpCurrencyBackendTest {

    private static final Currency EXP =
            Currency.builder(CurrencyId.of("exp")).backendId("exp").build();

    private ServerMock server;
    private PlayerMock alex;
    private final Stalled stalled = new Stalled();

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        alex = server.addPlayer("Alex");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aBalanceThatDidNotAnswerIsUnknownNotZero() {
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server, stalled, new Quiet());

        assertThatThrownBy(() -> backend.balance(ref(), EXP)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aCreditThatTimedOutNeverLandsLate() {
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server, stalled, new Quiet());

        assertThat(backend.credit(ref(), Money.of(EXP, new BigDecimal("50"))).errorOrThrow())
                .isEqualTo(TransferError.PLAYER_OFFLINE);
        stalled.runHeld();

        assertThat(alex.getLevel()).isZero();
        assertThat(alex.getExp()).isZero();
    }

    private PlayerRef ref() {
        return new PlayerRef(alex.getUniqueId(), alex.getName());
    }

    /** The player's thread holds every hop until the test runs it, long after the caller stopped waiting. */
    private static final class Stalled implements Scheduler {

        private final List<Runnable> held = new ArrayList<>();

        void runHeld() {
            List.copyOf(held).forEach(Runnable::run);
        }

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            held.add(task);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            held.add(task);
        }

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }

    private static final class Quiet implements Logger {

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
