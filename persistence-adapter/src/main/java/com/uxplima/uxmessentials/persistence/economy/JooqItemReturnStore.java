package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyItemReturns.ECONOMY_ITEM_RETURNS;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.ItemReturnStore;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link ItemReturnStore} over the {@code economy_item_returns} table. The take-all reads and deletes in
 * one transaction, and deletes only the rows it read, so a row kept by another server between the two statements is
 * left for the next join instead of being deleted unread.
 */
@NullMarked
public final class JooqItemReturnStore extends JooqRepository implements ItemReturnStore {

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
        return write(dsl -> {
            List<Record3<String, String, Integer>> rows = dsl.select(
                            ECONOMY_ITEM_RETURNS.ID, ECONOMY_ITEM_RETURNS.ITEM, ECONOMY_ITEM_RETURNS.AMOUNT)
                    .from(ECONOMY_ITEM_RETURNS)
                    .where(ECONOMY_ITEM_RETURNS.OWNER.eq(owner.toString()))
                    .orderBy(ECONOMY_ITEM_RETURNS.CREATED_AT, ECONOMY_ITEM_RETURNS.ID)
                    .fetch();
            Map<String, Integer> items = new LinkedHashMap<>();
            if (rows.isEmpty()) {
                return items;
            }
            rows.forEach(row -> items.merge(row.value2(), row.value3(), Integer::sum));
            dsl.deleteFrom(ECONOMY_ITEM_RETURNS)
                    .where(ECONOMY_ITEM_RETURNS.ID.in(
                            rows.stream().map(Record3::value1).toList()))
                    .execute();
            return items;
        });
    }
}
