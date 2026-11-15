package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyItemReturns.ECONOMY_ITEM_RETURNS;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

import com.uxplima.uxmessentials.economy.application.port.ItemReturnStore;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link ItemReturnStore} over the {@code economy_item_returns} table.
 *
 * <p>The take-all reads first and opens a write only when there are rows. It then deletes the rows one by one and
 * counts a row only when its own delete removed it. Two takes running at once (a quick rejoin, or two servers on one
 * database) can both read the same rows, but each row is deleted by exactly one of them, so no item is handed out
 * twice, on any backend's isolation level.
 */
@NullMarked
public final class JooqItemReturnStore extends JooqRepository implements ItemReturnStore {

    /** One kept row as read, before it is claimed. */
    record KeptRow(String id, String item, int amount) {}

    private final Clock clock;

    public JooqItemReturnStore(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void keep(UUID owner, Map<String, Integer> items) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return;
        }
        long now = clock.millis();
        write(dsl -> {
            items.forEach((item, amount) -> {
                if (amount > 0) {
                    dsl.insertInto(ECONOMY_ITEM_RETURNS)
                            .set(ECONOMY_ITEM_RETURNS.ID, UUID.randomUUID().toString())
                            .set(ECONOMY_ITEM_RETURNS.OWNER, owner.toString())
                            .set(ECONOMY_ITEM_RETURNS.ITEM, item)
                            .set(ECONOMY_ITEM_RETURNS.AMOUNT, amount)
                            .set(ECONOMY_ITEM_RETURNS.CREATED_AT, now)
                            .execute();
                }
            });
            return null;
        });
    }

    @Override
    public Map<String, Integer> takeAll(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        List<KeptRow> rows =
                read(dsl -> dsl.select(ECONOMY_ITEM_RETURNS.ID, ECONOMY_ITEM_RETURNS.ITEM, ECONOMY_ITEM_RETURNS.AMOUNT)
                        .from(ECONOMY_ITEM_RETURNS)
                        .where(ECONOMY_ITEM_RETURNS.OWNER.eq(owner.toString()))
                        .orderBy(ECONOMY_ITEM_RETURNS.CREATED_AT, ECONOMY_ITEM_RETURNS.ID)
                        .fetch(row -> new KeptRow(row.value1(), row.value2(), row.value3())));
        if (rows.isEmpty()) {
            return Map.of();
        }
        return write(dsl -> claim(
                rows,
                id -> dsl.deleteFrom(ECONOMY_ITEM_RETURNS)
                        .where(ECONOMY_ITEM_RETURNS.ID.eq(id))
                        .execute()));
    }

    /**
     * Sum the rows whose delete removed exactly one row. A row another take deleted first answers zero and is left out,
     * because that take is the one handing it over.
     */
    static Map<String, Integer> claim(List<KeptRow> rows, ToIntFunction<String> deleteById) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (KeptRow row : rows) {
            if (deleteById.applyAsInt(row.id()) == 1) {
                items.merge(row.item(), row.amount(), Integer::sum);
            }
        }
        return items;
    }
}
