package com.uxplima.uxmessentials.economy.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Outbound port for joint shared bank accounts persistence.
 *
 * <p>The two money-moving methods — {@link #deposit} and {@link #withdraw} — are <strong>atomic</strong>: each
 * performs the player's wallet leg (a guarded debit/credit on the native ledger) and the bank-balance change in
 * one transaction, committing together or not at all. A deposit whose guarded wallet debit changes no rows, or
 * a withdraw whose guarded bank-balance update changes no rows, leaves the other side untouched and returns the
 * modelled {@link TransferError}. The bank's sufficiency check is a guarded {@code UPDATE … WHERE balance >= ?},
 * not a JVM compare, so two concurrent withdrawals can never both overdraw the bank.
 */
public interface BankRepository {

    /** Finds a shared bank by its unique ID identifier. */
    Optional<SharedBank> findById(String id);

    /** Saves or updates the shared bank details and balance. */
    void save(SharedBank bank);

    /** Permanently deletes a shared bank account from storage. */
    void delete(String id);

    /** Lists all shared bank IDs a player is associated with. */
    List<String> findBankIdsForPlayer(UUID uuid);

    /**
     * Atomically debit {@code amount} from {@code player}'s wallet (the guarded debit) and add it to bank
     * {@code bankId}'s balance in one transaction. If the guarded wallet debit changes no rows (insufficient
     * funds) the bank balance is left untouched and {@link TransferError#INSUFFICIENT_FUNDS} is returned.
     */
    Result<Unit, TransferError> deposit(String bankId, PlayerRef player, Money amount);

    /**
     * Atomically subtract {@code amount} from bank {@code bankId}'s balance via a guarded
     * {@code UPDATE … WHERE balance >= ?} and credit it to {@code player}'s wallet in one transaction. When the
     * guarded bank update changes no rows the wallet is left untouched and
     * {@link TransferError#INSUFFICIENT_FUNDS} is returned; a wallet credit the clamp rejects rolls the whole
     * transaction back with {@link TransferError#BALANCE_MAX_EXCEEDED}.
     */
    Result<Unit, TransferError> withdraw(String bankId, PlayerRef player, Money amount);
}
