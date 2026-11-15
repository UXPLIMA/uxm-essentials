package com.uxplima.uxmessentials.migration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/** Balances kept in a map, standing in for the ledger. The import only reads and sets balances. */
final class InMemoryWallets implements WalletRepository {

    private final Map<UUID, Map<Currency, Money>> balances = new HashMap<>();

    Optional<Money> balance(PlayerRef owner, Currency currency) {
        return Optional.ofNullable(balances.getOrDefault(owner.uuid(), Map.of()).get(currency));
    }

    @Override
    public Optional<Wallet> findByOwner(PlayerRef owner) {
        Map<Currency, Money> owned = balances.get(owner.uuid());
        return owned == null ? Optional.empty() : Optional.of(Wallet.of(owner, owned));
    }

    @Override
    public Wallet ensureOwner(PlayerRef owner) {
        return Wallet.of(owner, balances.computeIfAbsent(owner.uuid(), ignored -> new HashMap<>()));
    }

    @Override
    public void upsertBalance(PlayerRef owner, Money balance) {
        balances.computeIfAbsent(owner.uuid(), ignored -> new HashMap<>()).put(balance.currency(), balance);
    }

    @Override
    public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
        throw new UnsupportedOperationException("the import never transfers");
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        throw new UnsupportedOperationException("the import never debits");
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        throw new UnsupportedOperationException("the import never credits");
    }

    @Override
    public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
        throw new UnsupportedOperationException("the import never exchanges");
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        throw new UnsupportedOperationException("the import never ranks");
    }
}
