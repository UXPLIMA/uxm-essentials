package com.uxplima.uxmessentials.persistence.trade;

import static com.uxplima.uxmessentials.persistence.jooq.tables.TradeEscrow.TRADE_ESCROW;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.TradeEscrowRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeEscrow;
import com.uxplima.uxmessentials.trade.application.TradeEscrowPhase;
import com.uxplima.uxmessentials.trade.application.port.TradeEscrowStore;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * jOOQ-backed {@link TradeEscrowStore} over the generated {@code TRADE_ESCROW} table — one row per side of a live
 * cross-server trade, keyed by {@code (trade_id, owner_uuid)}. Every state change is a single guarded jOOQ statement so
 * a duplicate bus signal, a double region hop, or a rejoin can never move a side's goods twice: {@link #commitBoth}
 * flips both rows only when both are {@code HELD} (in one transaction), {@link #claim} advances a single
 * {@code COMMITTED} row to {@code CLAIMED}, and {@link #beginRefund} deletes only a still-{@code HELD} row. The money
 * map is stored as its portable {@code currency=amount} text encoding and the item stacks as their opaque base64, both
 * mirroring the existing base64-in-TEXT idiom (V45/V68). No SQL is ever string-concatenated.
 */
@NullMarked
public final class JooqTradeEscrowStore extends JooqRepository implements TradeEscrowStore {

    public JooqTradeEscrowStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void escrow(TradeEscrow escrow) {
        Objects.requireNonNull(escrow, "escrow");
        String tradeId = escrow.tradeId().toString();
        String owner = escrow.owner().uuid().toString();
        String money = encodeMoney(escrow.money());
        write(dsl -> {
            dsl.insertInto(TRADE_ESCROW)
                    .set(TRADE_ESCROW.TRADE_ID, tradeId)
                    .set(TRADE_ESCROW.OWNER_UUID, owner)
                    .set(TRADE_ESCROW.OWNER_NAME, escrow.owner().name())
                    .set(TRADE_ESCROW.OWNER_SERVER, escrow.ownerServer())
                    .set(
                            TRADE_ESCROW.COUNTERPARTY_UUID,
                            escrow.counterparty().uuid().toString())
                    .set(TRADE_ESCROW.COUNTERPARTY_NAME, escrow.counterparty().name())
                    .set(TRADE_ESCROW.COUNTERPARTY_SERVER, escrow.counterpartyServer())
                    .set(TRADE_ESCROW.ITEM_DATA, escrow.itemData())
                    .set(TRADE_ESCROW.ITEM_COUNT, escrow.itemCount())
                    .set(TRADE_ESCROW.MONEY, money)
                    .set(TRADE_ESCROW.PHASE, escrow.phase().name())
                    .set(TRADE_ESCROW.CREATED_AT, escrow.createdAt().toEpochMilli())
                    .onConflict(TRADE_ESCROW.TRADE_ID, TRADE_ESCROW.OWNER_UUID)
                    .doUpdate()
                    .set(TRADE_ESCROW.ITEM_DATA, escrow.itemData())
                    .set(TRADE_ESCROW.ITEM_COUNT, escrow.itemCount())
                    .set(TRADE_ESCROW.MONEY, money)
                    .set(TRADE_ESCROW.PHASE, escrow.phase().name())
                    .set(TRADE_ESCROW.CREATED_AT, escrow.createdAt().toEpochMilli())
                    .execute();
            return null;
        });
    }

    @Override
    public Optional<TradeEscrow> find(TradeId tradeId, UUID owner) {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(TRADE_ESCROW)
                .where(TRADE_ESCROW.TRADE_ID.eq(tradeId.toString()))
                .and(TRADE_ESCROW.OWNER_UUID.eq(owner.toString()))
                .fetchOptional()
                .map(JooqTradeEscrowStore::toEscrow));
    }

    @Override
    public List<TradeEscrow> findByOwner(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(TRADE_ESCROW)
                .where(TRADE_ESCROW.OWNER_UUID.eq(owner.toString()))
                .fetch()
                .map(JooqTradeEscrowStore::toEscrow));
    }

    @Override
    public boolean commitBoth(TradeId tradeId, UUID a, UUID b) {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        String id = tradeId.toString();
        List<String> owners = List.of(a.toString(), b.toString());
        return write(dsl -> {
            Integer held = dsl.selectCount()
                    .from(TRADE_ESCROW)
                    .where(TRADE_ESCROW.TRADE_ID.eq(id))
                    .and(TRADE_ESCROW.OWNER_UUID.in(owners))
                    .and(TRADE_ESCROW.PHASE.eq(TradeEscrowPhase.HELD.name()))
                    .fetchOne(0, Integer.class);
            if (held == null || held != 2) {
                return false;
            }
            dsl.update(TRADE_ESCROW)
                    .set(TRADE_ESCROW.PHASE, TradeEscrowPhase.COMMITTED.name())
                    .where(TRADE_ESCROW.TRADE_ID.eq(id))
                    .and(TRADE_ESCROW.OWNER_UUID.in(owners))
                    .and(TRADE_ESCROW.PHASE.eq(TradeEscrowPhase.HELD.name()))
                    .execute();
            return true;
        });
    }

    @Override
    public boolean claim(TradeId tradeId, UUID owner) {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(owner, "owner");
        return write(dsl -> dsl.update(TRADE_ESCROW)
                        .set(TRADE_ESCROW.PHASE, TradeEscrowPhase.CLAIMED.name())
                        .where(TRADE_ESCROW.TRADE_ID.eq(tradeId.toString()))
                        .and(TRADE_ESCROW.OWNER_UUID.eq(owner.toString()))
                        .and(TRADE_ESCROW.PHASE.eq(TradeEscrowPhase.COMMITTED.name()))
                        .execute()
                == 1);
    }

    @Override
    public boolean beginRefund(TradeId tradeId, UUID owner) {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(owner, "owner");
        return write(dsl -> dsl.deleteFrom(TRADE_ESCROW)
                        .where(TRADE_ESCROW.TRADE_ID.eq(tradeId.toString()))
                        .and(TRADE_ESCROW.OWNER_UUID.eq(owner.toString()))
                        .and(TRADE_ESCROW.PHASE.eq(TradeEscrowPhase.HELD.name()))
                        .execute()
                == 1);
    }

    @Override
    public void clear(TradeId tradeId, UUID owner) {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(owner, "owner");
        write(dsl -> {
            dsl.deleteFrom(TRADE_ESCROW)
                    .where(TRADE_ESCROW.TRADE_ID.eq(tradeId.toString()))
                    .and(TRADE_ESCROW.OWNER_UUID.eq(owner.toString()))
                    .execute();
            return null;
        });
    }

    /** Rebuild the escrow aggregate from a row, decoding the opaque item text and the money encoding. */
    private static TradeEscrow toEscrow(TradeEscrowRecord row) {
        return new TradeEscrow(
                TradeId.fromString(row.getTradeId()),
                new PlayerRef(UUID.fromString(row.getOwnerUuid()), row.getOwnerName()),
                row.getOwnerServer(),
                new PlayerRef(UUID.fromString(row.getCounterpartyUuid()), row.getCounterpartyName()),
                row.getCounterpartyServer(),
                row.getItemData() == null ? "" : row.getItemData(),
                row.getItemCount() == null ? 0 : row.getItemCount(),
                decodeMoney(row.getMoney()),
                TradeEscrowPhase.valueOf(row.getPhase()),
                Instant.ofEpochMilli(row.getCreatedAt()));
    }

    /** Encode the staked money map as {@code currency=amount} pairs joined by {@code ;} — empty for no money. */
    private static String encodeMoney(Map<String, BigDecimal> money) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, BigDecimal> leg : money.entrySet()) {
            if (leg.getValue().signum() <= 0) {
                continue;
            }
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(leg.getKey()).append('=').append(leg.getValue().toPlainString());
        }
        return text.toString();
    }

    /** Decode the {@code currency=amount} money text back into a map; a blank or null column is no money. */
    private static Map<String, BigDecimal> decodeMoney(@Nullable String encoded) {
        Map<String, BigDecimal> money = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return money;
        }
        for (String pair : encoded.split(";", -1)) {
            int split = pair.indexOf('=');
            if (split > 0) {
                money.put(pair.substring(0, split), new BigDecimal(pair.substring(split + 1)));
            }
        }
        return money;
    }
}
