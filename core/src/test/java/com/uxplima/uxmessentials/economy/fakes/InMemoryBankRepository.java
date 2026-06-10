package com.uxplima.uxmessentials.economy.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BankRepository;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * An in-memory {@link BankRepository} backed by an {@link InMemoryWalletRepository}, modelling the atomic
 * deposit/withdraw the jOOQ adapter performs: the player's wallet leg and the bank-balance change apply
 * together, the withdraw's bank sufficiency is a guarded check, and a leg that cannot apply leaves the other
 * untouched.
 */
public final class InMemoryBankRepository implements BankRepository {

    private final InMemoryWalletRepository wallets;
    private final Map<String, SharedBank> byId = new LinkedHashMap<>();

    public InMemoryBankRepository(InMemoryWalletRepository wallets) {
        this.wallets = wallets;
    }

    @Override
    public Optional<SharedBank> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public void save(SharedBank bank) {
        byId.put(bank.id(), bank);
    }

    @Override
    public List<SharedBank> findAll() {
        return List.copyOf(byId.values());
    }

    @Override
    public void creditBank(String bankId, Money interest) {
        SharedBank bank = byId.get(bankId);
        if (bank != null && bank.balance().currency().equals(interest.currency())) {
            byId.put(
                    bankId,
                    new SharedBank(
                            bank.id(),
                            bank.name(),
                            bank.balance().plus(interest),
                            bank.creator(),
                            bank.members(),
                            bank.createdAt()));
        }
    }

    @Override
    public void delete(String id) {
        byId.remove(id);
    }

    @Override
    public List<String> findBankIdsForPlayer(UUID uuid) {
        List<String> out = new ArrayList<>();
        for (SharedBank bank : byId.values()) {
            if (bank.members().stream().anyMatch(m -> m.player().uuid().equals(uuid))) {
                out.add(bank.id());
            }
        }
        return out;
    }

    @Override
    public Result<Unit, BankError> deposit(String bankId, PlayerRef player, Money amount) {
        SharedBank bank = byId.get(bankId);
        if (bank == null) {
            return Result.err(BankError.NOT_FOUND);
        }
        Result<Unit, TransferError> debited = wallets.debit(player, amount);
        if (debited.isErr()) {
            return Result.err(BankError.INSUFFICIENT_FUNDS);
        }
        byId.put(bankId, bank.withBalance(bank.balance().plus(amount)));
        return Result.ok();
    }

    @Override
    public Result<Unit, BankError> withdraw(String bankId, PlayerRef player, Money amount) {
        SharedBank bank = byId.get(bankId);
        if (bank == null || bank.balance().isLessThan(amount)) {
            return Result.err(BankError.INSUFFICIENT_BANK_FUNDS);
        }
        Result<Unit, TransferError> credited = wallets.credit(player, amount);
        if (credited.isErr()) {
            return Result.err(BankError.BALANCE_MAX_EXCEEDED);
        }
        byId.put(bankId, bank.withBalance(bank.balance().minus(amount)));
        return Result.ok();
    }
}
