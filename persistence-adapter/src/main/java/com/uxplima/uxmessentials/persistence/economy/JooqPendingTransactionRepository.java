package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyPendingPhysicalTransactions.ECONOMY_PENDING_PHYSICAL_TRANSACTIONS;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link PendingTransactionRepository} over the generated
 * {@code ECONOMY_PENDING_PHYSICAL_TRANSACTIONS} table. Every statement is typed jOOQ DSL, no
 * string-concatenated SQL.
 *
 * <p>The take reads the player's rows first, then deletes them one by one by id and hands over only the rows whose
 * own delete removed them. Two takes running at once (a quick rejoin, or two servers on one database) can both read
 * the same rows, but each row is deleted by exactly one of them, so no credit is paid twice on any backend's
 * isolation level. A credit queued after the read has a new id, so the take leaves it for the next one.
 */
@NullMarked
public final class JooqPendingTransactionRepository extends JooqRepository implements PendingTransactionRepository {

    public JooqPendingTransactionRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void queueCredit(PlayerRef player, String currencyId, BigDecimal amount) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(amount, "amount");

        long now = System.currentTimeMillis();
        write(dsl -> {
            // The row references the owner, and a credit can be queued for a player the economy has never seen
            // (a refund, or a payment to someone who never joined), so the owner is written first.
            WalletLegs.upsertOwner(dsl, player, now);
            dsl.insertInto(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS)
                    .set(
                            ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.ID,
                            UUID.randomUUID().toString())
                    .set(
                            ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID,
                            player.uuid().toString())
                    .set(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CURRENCY, currencyId)
                    .set(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.AMOUNT, amount)
                    .set(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CREATED_AT, now)
                    .execute();
            return null;
        });
    }

    @Override
    public List<PendingTransaction> getAndClearPending(UUID playerUuid, Set<String> leave) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(leave, "leave");
        List<PendingTransaction> read = read(dsl -> dsl.select(
                                ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.ID,
                                ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID,
                                ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CURRENCY,
                                ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.AMOUNT)
                        .from(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS)
                        .where(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID.eq(playerUuid.toString()))
                        .orderBy(
                                ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CREATED_AT,
                                ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.ID)
                        .fetch(row -> new PendingTransaction(
                                row.value1(), UUID.fromString(row.value2()), row.value3(), row.value4())))
                .stream()
                .filter(row -> !leave.contains(row.currencyId().toLowerCase(Locale.ROOT)))
                .toList();
        if (read.isEmpty()) {
            return List.of();
        }
        return write(dsl -> claim(
                read,
                id -> dsl.deleteFrom(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS)
                        .where(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.ID.eq(id))
                        .execute()));
    }

    /**
     * Keep the rows whose own delete removed exactly one row. A row another take deleted first answers zero and is
     * left out, because that take is the one paying it.
     */
    static List<PendingTransaction> claim(List<PendingTransaction> rows, ToIntFunction<String> deleteById) {
        List<PendingTransaction> claimed = new ArrayList<>(rows.size());
        for (PendingTransaction row : rows) {
            if (deleteById.applyAsInt(row.id()) == 1) {
                claimed.add(row);
            }
        }
        return List.copyOf(claimed);
    }
}
