package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.Test;

/**
 * Every queued amount must end up either paid exactly once or back on the queue for the next join, in the currency
 * it was queued in.
 */
class DeliverPendingCreditsTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).max(new BigDecimal("100")).build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();

    private final InMemoryBank bank = new InMemoryBank(COINS, GEMS);
    private final Queue queue = new Queue();
    private final List<String> notices = new ArrayList<>();
    private final RecordingLogger log = new RecordingLogger();
    private final DeliverPendingCredits credits = new DeliverPendingCredits(
            bank,
            queue,
            new EconomyNotifier(
                    (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                        notices.add(key.key());
                        return key.key();
                    },
                    (viewer, text) -> {}),
            new Online(),
            log);
    private final Set<UUID> online = new HashSet<>(Set.of(ALEX.uuid()));

    @Test
    void queuedCreditsArePaidOncePerCurrencyAndLeaveTheQueue() {
        queue.queueCredit(ALEX, "coins", new BigDecimal("10"));
        queue.queueCredit(ALEX, "coins", new BigDecimal("5"));
        queue.queueCredit(ALEX, "gems", new BigDecimal("2"));

        credits.deliver(ALEX);

        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("15");
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("2");
        assertThat(queue.rows).isEmpty();
        assertThat(notices).hasSize(2);

        credits.deliver(ALEX);

        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("15");
    }

    @Test
    void aRefusedCreditGoesBackOnTheQueueInItsOwnCurrency() {
        queue.queueCredit(ALEX, "coins", new BigDecimal("500"));
        queue.queueCredit(ALEX, "gems", new BigDecimal("2"));

        credits.deliver(ALEX);

        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("2");
        assertThat(queue.rows).containsExactly("coins=500");
        assertThat(notices).hasSize(1);
    }

    @Test
    void aCreditThatThrowsGoesBackOnTheQueue() {
        bank.breakCredits(GEMS);
        queue.queueCredit(ALEX, "gems", new BigDecimal("2"));

        credits.deliver(ALEX);

        assertThat(queue.rows).containsExactly("gems=2");
        assertThat(notices).isEmpty();
        assertThat(log.errors()).hasSize(1);
    }

    @Test
    void anUnknownCurrencyStaysQueued() {
        queue.queueCredit(ALEX, "tokens", new BigDecimal("3"));

        credits.deliver(ALEX);

        assertThat(queue.rows).containsExactly("tokens=3");
    }

    @Test
    void aQueueThatCannotBeReadKeepsItsRowsAndPaysNothing() {
        queue.queueCredit(ALEX, "coins", new BigDecimal("10"));
        queue.readsFail = true;

        credits.deliver(ALEX);

        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
        assertThat(queue.rows).containsExactly("coins=10");
        assertThat(log.errors()).hasSize(1);
    }

    @Test
    void aPlayerWhoLeftBeforeDeliveryKeepsTheirRowsAndIsToldNothing() {
        queue.queueCredit(ALEX, "coins", new BigDecimal("10"));
        online.remove(ALEX.uuid());

        credits.deliver(ALEX);

        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("0");
        assertThat(queue.rows).containsExactly("coins=10");
        assertThat(notices).isEmpty();
    }

    private final class Online implements PlayerLookup {

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
            return online.contains(uuid);
        }
    }

    /** One player's queue as {@code currency=amount} rows, all taken by one take. */
    private static final class Queue implements PendingTransactionRepository {

        final List<String> rows = new ArrayList<>();
        boolean readsFail;

        @Override
        public void queueCredit(PlayerRef player, String currencyId, BigDecimal amount) {
            rows.add(currencyId + "=" + amount.toPlainString());
        }

        @Override
        public List<PendingTransaction> getAndClearPending(UUID playerUuid) {
            if (readsFail) {
                throw new IllegalStateException("database unavailable");
            }
            List<PendingTransaction> taken = new ArrayList<>();
            for (String row : rows) {
                int cut = row.indexOf('=');
                taken.add(new PendingTransaction(
                        Integer.toString(taken.size()),
                        playerUuid,
                        row.substring(0, cut),
                        new BigDecimal(row.substring(cut + 1))));
            }
            rows.clear();
            return taken;
        }
    }
}
