package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyBankMembers.ECONOMY_BANK_MEMBERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomySharedBanks.ECONOMY_SHARED_BANKS;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BankRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.EconomySharedBanksRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * jOOQ-backed {@link BankRepository} over the generated {@code ECONOMY_SHARED_BANKS} and
 * {@code ECONOMY_BANK_MEMBERS} tables. Every statement is typed jOOQ DSL so jOOQ renders per-dialect SQL
 * (an {@code ON DUPLICATE KEY UPDATE} on MySQL where the SQLite/Postgres backend gets {@code ON CONFLICT});
 * no SQL is ever string-concatenated. The bank balance is DB-backed and survives a world rollback, the same
 * invariant a wallet balance honours.
 */
@NullMarked
public final class JooqBankRepository extends JooqRepository implements BankRepository {

    private final CurrencyRegistry currencies;
    private final Clock clock;

    public JooqBankRepository(DSLContext dsl, CurrencyRegistry currencies, Clock clock) {
        super(dsl);
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<SharedBank> findById(String id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> {
            EconomySharedBanksRecord bank = dsl.selectFrom(ECONOMY_SHARED_BANKS)
                    .where(ECONOMY_SHARED_BANKS.ID.eq(id))
                    .fetchOne();
            if (bank == null) {
                return Optional.empty();
            }
            Optional<Currency> currency = currencies.find(CurrencyId.of(bank.getCurrency()));
            if (currency.isEmpty()) {
                return Optional.empty();
            }
            Money balance = Money.of(currency.get(), bank.getBalance());
            PlayerRef creator = ownerRef(dsl, bank.getCreatorUuid());
            List<BankMember> members = loadMembers(dsl, id);
            return Optional.of(new SharedBank(id, bank.getName(), balance, creator, members, bank.getCreatedAt()));
        });
    }

    @Override
    public void save(SharedBank bank) {
        Objects.requireNonNull(bank, "bank");
        write(dsl -> {
            dsl.insertInto(ECONOMY_SHARED_BANKS)
                    .set(ECONOMY_SHARED_BANKS.ID, bank.id())
                    .set(ECONOMY_SHARED_BANKS.NAME, bank.name())
                    .set(ECONOMY_SHARED_BANKS.BALANCE, bank.balance().amount())
                    .set(
                            ECONOMY_SHARED_BANKS.CURRENCY,
                            bank.balance().currency().id().value())
                    .set(
                            ECONOMY_SHARED_BANKS.CREATOR_UUID,
                            bank.creator().uuid().toString())
                    .set(ECONOMY_SHARED_BANKS.CREATED_AT, bank.createdAt())
                    .onConflict(ECONOMY_SHARED_BANKS.ID)
                    .doUpdate()
                    .set(ECONOMY_SHARED_BANKS.NAME, bank.name())
                    .set(ECONOMY_SHARED_BANKS.BALANCE, bank.balance().amount())
                    .execute();

            // The roster is rewritten wholesale: clear it, then re-insert the current members.
            dsl.deleteFrom(ECONOMY_BANK_MEMBERS)
                    .where(ECONOMY_BANK_MEMBERS.BANK_ID.eq(bank.id()))
                    .execute();
            for (BankMember member : bank.members()) {
                dsl.insertInto(ECONOMY_BANK_MEMBERS)
                        .set(ECONOMY_BANK_MEMBERS.BANK_ID, bank.id())
                        .set(
                                ECONOMY_BANK_MEMBERS.PLAYER_UUID,
                                member.player().uuid().toString())
                        .set(ECONOMY_BANK_MEMBERS.ROLE, member.role().name())
                        .execute();
            }
            return null;
        });
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id");
        write(dsl -> {
            dsl.deleteFrom(ECONOMY_BANK_MEMBERS)
                    .where(ECONOMY_BANK_MEMBERS.BANK_ID.eq(id))
                    .execute();
            dsl.deleteFrom(ECONOMY_SHARED_BANKS)
                    .where(ECONOMY_SHARED_BANKS.ID.eq(id))
                    .execute();
            return null;
        });
    }

    @Override
    public List<String> findBankIdsForPlayer(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return read(dsl -> dsl.select(ECONOMY_BANK_MEMBERS.BANK_ID)
                .from(ECONOMY_BANK_MEMBERS)
                .where(ECONOMY_BANK_MEMBERS.PLAYER_UUID.eq(uuid.toString()))
                .fetchInto(String.class));
    }

    @Override
    public Result<Unit, TransferError> deposit(String bankId, PlayerRef player, Money amount) {
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        // The guarded wallet debit and the bank-balance add commit together: a debit short of funds changes no
        // rows and the bank is left untouched.
        return write(dsl -> {
            if (WalletLegs.guardedDebit(dsl, player, amount) == 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            dsl.update(ECONOMY_SHARED_BANKS)
                    .set(ECONOMY_SHARED_BANKS.BALANCE, ECONOMY_SHARED_BANKS.BALANCE.plus(amount.amount()))
                    .where(ECONOMY_SHARED_BANKS.ID.eq(bankId))
                    .execute();
            return Result.ok();
        });
    }

    @Override
    public Result<Unit, TransferError> withdraw(String bankId, PlayerRef player, Money amount) {
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(amount, "amount");
        // The bank sufficiency is a guarded UPDATE … WHERE balance >= ?, so two concurrent withdrawals can never
        // both overdraw it. The clamp is checked read-only before any mutation, and the no-rows-changed guard
        // fires before the wallet credit, so no failure path ever returns after a committed change (the
        // transaction only commits when this block returns ok).
        return write(dsl -> {
            if (!WalletLegs.creditFitsClamp(dsl, player, amount)) {
                return Result.err(TransferError.BALANCE_MAX_EXCEEDED);
            }
            int changed = dsl.update(ECONOMY_SHARED_BANKS)
                    .set(ECONOMY_SHARED_BANKS.BALANCE, ECONOMY_SHARED_BANKS.BALANCE.minus(amount.amount()))
                    .where(ECONOMY_SHARED_BANKS.ID.eq(bankId))
                    .and(ECONOMY_SHARED_BANKS.BALANCE.ge(amount.amount()))
                    .execute();
            if (changed == 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            WalletLegs.creditRow(dsl, player, amount, clock.millis());
            return Result.ok();
        });
    }

    private List<BankMember> loadMembers(DSLContext dsl, String bankId) {
        List<BankMember> members = new ArrayList<>();
        dsl.select(ECONOMY_BANK_MEMBERS.PLAYER_UUID, ECONOMY_BANK_MEMBERS.ROLE, ECONOMY_OWNERS.NAME)
                .from(ECONOMY_BANK_MEMBERS)
                .leftJoin(ECONOMY_OWNERS)
                .on(ECONOMY_BANK_MEMBERS.PLAYER_UUID.eq(ECONOMY_OWNERS.OWNER))
                .where(ECONOMY_BANK_MEMBERS.BANK_ID.eq(bankId))
                .fetch()
                .forEach(row -> {
                    PlayerRef ref = playerRef(row.get(ECONOMY_BANK_MEMBERS.PLAYER_UUID), row.get(ECONOMY_OWNERS.NAME));
                    members.add(new BankMember(ref, BankRole.valueOf(row.get(ECONOMY_BANK_MEMBERS.ROLE))));
                });
        return members;
    }

    private static PlayerRef ownerRef(DSLContext dsl, String uuid) {
        String name = dsl.select(ECONOMY_OWNERS.NAME)
                .from(ECONOMY_OWNERS)
                .where(ECONOMY_OWNERS.OWNER.eq(uuid))
                .fetchOne(ECONOMY_OWNERS.NAME);
        return playerRef(uuid, name);
    }

    private static PlayerRef playerRef(String uuid, @Nullable String name) {
        String resolved = name == null || name.isBlank() ? uuid : name;
        return new PlayerRef(UUID.fromString(uuid), resolved);
    }
}
