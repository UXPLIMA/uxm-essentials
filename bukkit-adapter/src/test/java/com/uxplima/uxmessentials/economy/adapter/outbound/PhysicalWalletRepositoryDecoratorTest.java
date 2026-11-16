package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

/**
 * The paths where the holder's thread never takes the inventory work. The work itself needs a live player, so these
 * cases are the ones that can be shown without a server: a change that never happened must be reported as such, and
 * money already taken must come back in the currency it was taken in.
 */
class PhysicalWalletRepositoryDecoratorTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Currency NUGGETS =
            Currency.builder(CurrencyId.of("nuggets")).physical(true).build();
    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");

    private final Ledger ledger = new Ledger();
    private final Queue queue = new Queue();
    private final EntityThreads threads = new EntityThreads();

    private PhysicalWalletRepositoryDecorator decorator(Duration timeout) {
        return new PhysicalWalletRepositoryDecorator(
                ledger,
                queue,
                new BukkitInventoryCalculations(),
                CurrencyRegistry.of(List.of(COINS, NUGGETS), COINS.id()),
                threads,
                new AllOnline(),
                new Quiet(),
                timeout);
    }

    @Test
    void anUpsertTheHoldersThreadNeverStartedThrowsAndNeverLandsLater() {
        threads.mode = Mode.HOLD;

        assertThatThrownBy(
                        () -> decorator(Duration.ofMillis(50)).upsertBalance(ALEX, Money.of(NUGGETS, BigDecimal.TEN)))
                .isInstanceOf(IllegalStateException.class);

        // The held task reaches the thread after the caller was told it failed: it must do nothing. Were it to
        // touch the inventory it would ask the server for the player, which this test has no server for.
        threads.runHeld();
        assertThat(queue.credits).isEmpty();
    }

    @Test
    void anUpsertForAHolderWhoLeftThrows() {
        threads.mode = Mode.RETIRE;

        assertThatThrownBy(
                        () -> decorator(Duration.ofSeconds(5)).upsertBalance(ALEX, Money.of(NUGGETS, BigDecimal.TEN)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRefusedRefundOfTheVirtualLegIsQueuedInThatCurrency() {
        threads.mode = Mode.RETIRE;
        ledger.refuseCredits = true;

        Result<Unit, TransferError> result = decorator(Duration.ofSeconds(5))
                .exchange(ALEX, Money.of(COINS, new BigDecimal("40")), Money.of(NUGGETS, BigDecimal.ONE));

        assertThat(result.isErr()).isTrue();
        assertThat(ledger.debits).containsExactly("coins:40");
        assertThat(queue.credits).containsExactly(ALEX.uuid() + ":coins:40");
    }

    private enum Mode {
        /** The task is kept and run later, the way a busy region thread reaches it after the wait ran out. */
        HOLD,
        /** The entity is retired before the task runs, the way a player who logs off is. */
        RETIRE
    }

    private static final class EntityThreads implements Scheduler {

        Mode mode = Mode.HOLD;
        private final List<Runnable> held = new ArrayList<>();

        void runHeld() {
            List<Runnable> due = List.copyOf(held);
            held.clear();
            due.forEach(Runnable::run);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            if (mode == Mode.RETIRE) {
                retired.run();
            } else {
                held.add(task);
            }
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            held.add(task);
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(com.uxplima.uxmessentials.shared.domain.Position position, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** The virtual ledger: debits succeed, credits can be refused (a max-balance clamp, say). */
    private static final class Ledger implements WalletRepository {

        boolean refuseCredits;
        final List<String> debits = new ArrayList<>();

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            debits.add(amount.currency().id().value() + ":"
                    + amount.amount().stripTrailingZeros().toPlainString());
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            return refuseCredits ? Result.err(TransferError.BALANCE_MAX_EXCEEDED) : Result.ok();
        }

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            return Optional.empty();
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return Wallet.empty(owner);
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {}

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }
    }

    private static final class Queue implements PendingTransactionRepository {

        final List<String> credits = new ArrayList<>();

        @Override
        public void queueCredit(PlayerRef player, String currencyId, BigDecimal amount) {
            credits.add(player.uuid() + ":" + currencyId + ":"
                    + amount.stripTrailingZeros().toPlainString());
        }

        @Override
        public List<PendingTransaction> getAndClearPending(UUID playerUuid) {
            return List.of();
        }
    }

    private static final class AllOnline implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
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
