package com.uxplima.uxmessentials.persistence.trade;

import static com.uxplima.uxmessentials.persistence.jooq.tables.TradeItemReturns.TRADE_ITEM_RETURNS;

import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.trade.application.port.TradeItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link TradeItemReturns} over the {@code trade_item_returns} table. The items are stored as base64 of
 * their serialized bytes, the form the vault tables use.
 *
 * <p>A batch is claimed by deleting its row, and only the delete that removed it reports the claim. Two deliveries
 * running at once (a quick rejoin, or two servers on one database) can both read the same rows, but each row is
 * deleted by exactly one of them, so no item is handed out twice, on any backend's isolation level.
 */
@NullMarked
public final class JooqTradeItemReturns extends JooqRepository implements TradeItemReturns {

    private final Clock clock;

    public JooqTradeItemReturns(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void keep(UUID owner, VaultContents items) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return;
        }
        String encoded = Base64.getEncoder().encodeToString(items.payload().orElseThrow());
        long now = clock.millis();
        write(dsl -> dsl.insertInto(TRADE_ITEM_RETURNS)
                .set(TRADE_ITEM_RETURNS.ID, UUID.randomUUID().toString())
                .set(TRADE_ITEM_RETURNS.OWNER, owner.toString())
                .set(TRADE_ITEM_RETURNS.CONTENTS, encoded)
                .set(TRADE_ITEM_RETURNS.CREATED_AT, now)
                .execute());
    }

    @Override
    public List<Kept> kept(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.select(TRADE_ITEM_RETURNS.ID, TRADE_ITEM_RETURNS.CONTENTS)
                .from(TRADE_ITEM_RETURNS)
                .where(TRADE_ITEM_RETURNS.OWNER.eq(owner.toString()))
                .orderBy(TRADE_ITEM_RETURNS.CREATED_AT, TRADE_ITEM_RETURNS.ID)
                .fetch(row -> new Kept(
                        row.value1(), VaultContents.of(Base64.getDecoder().decode(row.value2())))));
    }

    @Override
    public boolean claim(String id) {
        Objects.requireNonNull(id, "id");
        return write(dsl -> dsl.deleteFrom(TRADE_ITEM_RETURNS)
                        .where(TRADE_ITEM_RETURNS.ID.eq(id))
                        .execute()
                == 1);
    }
}
