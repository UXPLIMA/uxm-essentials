package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.PendingPay;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * A staged payment that lapses says so.
 *
 * <p>The expiry wrote one console line and told the payer nothing, so somebody who ran a large {@code /pay}
 * and then read the rules for a minute came back to a prompt that was no longer there, and
 * {@code pay.confirm-expired} shipped in twelve languages unsent. The registry owns the timer, so it owns the
 * word: it calls the seam the wiring points at the payer's notifier.
 */
class SchedulerPendingPayRegistryTest {

    private static final PlayerRef PAYER = new PlayerRef(UUID.randomUUID(), "Payer");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Target");

    @Test
    void anExpiredConfirmationTellsThePayer() {
        ImmediateScheduler scheduler = new ImmediateScheduler();
        List<PlayerRef> told = new ArrayList<>();
        SchedulerPendingPayRegistry registry =
                new SchedulerPendingPayRegistry(scheduler, new SilentLogger(), Duration.ofSeconds(30), told::add);

        registry.stage(pending());

        assertThat(told).containsExactly(PAYER);
        assertThat(registry.peek(PAYER))
                .as("the expiry drops the slot as well as speaking")
                .isEmpty();
    }

    @Test
    void aConfirmationTakenBeforeItLapsesTellsNobody() {
        DeferredScheduler scheduler = new DeferredScheduler();
        List<PlayerRef> told = new ArrayList<>();
        SchedulerPendingPayRegistry registry =
                new SchedulerPendingPayRegistry(scheduler, new SilentLogger(), Duration.ofSeconds(30), told::add);
        registry.stage(pending());

        assertThat(registry.take(PAYER)).isPresent();
        scheduler.fire(); // the timer wakes after the payment already went through

        assertThat(told)
                .as("the slot was consumed, so the stale timer has nothing to expire and nothing to say")
                .isEmpty();
    }

    @Test
    void areplacedPromptDoesNotMakeTheOldTimerSpeak() {
        DeferredScheduler scheduler = new DeferredScheduler();
        List<PlayerRef> told = new ArrayList<>();
        SchedulerPendingPayRegistry registry =
                new SchedulerPendingPayRegistry(scheduler, new SilentLogger(), Duration.ofSeconds(30), told::add);

        registry.stage(pending());
        // A second large /pay replaces the first. It has to differ from it: the slot is compared by value,
        // which is what makes a stale timer harmless, and two identical stagings are the same pay.
        registry.stage(pending(new BigDecimal("2000")));
        scheduler.fireFirst(); // the first prompt's timer

        assertThat(told)
                .as("the first timer no longer owns the slot, so it neither clears it nor speaks")
                .isEmpty();
        assertThat(registry.peek(PAYER)).isPresent();
    }

    private static PendingPay pending() {
        return pending(new BigDecimal("1000"));
    }

    private static PendingPay pending(BigDecimal amount) {
        Currency currency = Currency.builder(CurrencyId.of("coins")).build();
        return new PendingPay(PAYER, TARGET, new Money(currency, amount), Instant.EPOCH);
    }

    /** Runs the expiry the moment it is armed, which is the lapse this test is about. */
    private static final class ImmediateScheduler extends NoopScheduler {
        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Holds every armed expiry so a test can fire it after the fact. */
    private static final class DeferredScheduler extends NoopScheduler {
        private final List<Runnable> armed = new ArrayList<>();

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            armed.add(task);
        }

        void fire() {
            armed.forEach(Runnable::run);
        }

        void fireFirst() {
            armed.get(0).run();
        }
    }

    /** Everything a scheduler is asked for here that this test does not care about. */
    private abstract static class NoopScheduler implements Scheduler {
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
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }
    }

    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable failure) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
