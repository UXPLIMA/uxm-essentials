package com.uxplima.uxmessentials.economy.fakes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.LoanRepository;
import com.uxplima.uxmessentials.economy.domain.Loan;
import com.uxplima.uxmessentials.economy.domain.Loan.CreditScore;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * An in-memory {@link LoanRepository} backed by an {@link InMemoryWalletRepository}, modelling the atomic
 * disburse/repay the jOOQ adapter performs: the wallet leg (a guarded credit/debit) and the loan-row change
 * apply together, and a wallet leg that cannot apply leaves the loan untouched. The two pieces share one
 * wallet so a service test can assert money moved exactly once.
 */
public final class InMemoryLoanRepository implements LoanRepository {

    private final InMemoryWalletRepository wallets;
    private final Map<String, Loan> byId = new LinkedHashMap<>();
    private final Map<PlayerRef, CreditScore> scores = new LinkedHashMap<>();

    public InMemoryLoanRepository(InMemoryWalletRepository wallets) {
        this.wallets = wallets;
    }

    @Override
    public Optional<Loan> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<Loan> findByDebtor(PlayerRef debtor) {
        List<Loan> out = new ArrayList<>();
        for (Loan loan : byId.values()) {
            if (loan.debtor().uuid().equals(debtor.uuid())) {
                out.add(loan);
            }
        }
        return out;
    }

    @Override
    public List<Loan> findAllActive() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public void save(Loan loan) {
        byId.put(loan.id(), loan);
    }

    @Override
    public void delete(String id) {
        byId.remove(id);
    }

    @Override
    public CreditScore getCreditScore(PlayerRef player) {
        return scores.getOrDefault(player, new CreditScore(player, 500, 0L));
    }

    @Override
    public void saveCreditScore(CreditScore creditScore) {
        scores.put(creditScore.player(), creditScore);
    }

    @Override
    public Result<Unit, TransferError> disburse(Loan loan) {
        Result<Unit, TransferError> credited = wallets.credit(loan.debtor(), loan.principal());
        if (credited.isErr()) {
            return credited;
        }
        byId.put(loan.id(), loan);
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> applyRepayment(
            PlayerRef debtor, Money paid, Loan updatedLoan, boolean fullyPaid) {
        Result<Unit, TransferError> debited = wallets.debit(debtor, paid);
        if (debited.isErr()) {
            return debited;
        }
        if (fullyPaid) {
            byId.remove(updatedLoan.id());
        } else {
            byId.put(updatedLoan.id(), updatedLoan);
        }
        return Result.ok();
    }
}
