package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Decorator for {@link WalletRepository} that intercepts physical-currency operations. A physical balance lives
 * in the player's live inventory, so every read or mutation here touches the Bukkit inventory/world API and must
 * run on that player's region (entity) thread — Folia forbids the off-thread inventory access the previous
 * version performed. The repository contract is synchronous, so each physical branch marshals its inventory work
 * onto the holder's entity thread through the injected {@link Scheduler} and waits for the result; these methods
 * are only ever called off the tick thread (the provider runs them async), so the bounded wait is the standard
 * anti-corruption bridge, never a main-thread block.
 *
 * <p>Online players read/write their active inventory; an offline player's credit is queued in the pending
 * transactions table and an offline debit/transfer is refused, exactly as before. The {@link PlayerLookup} port
 * resolves online state without an off-thread {@code Bukkit.getPlayer} call from this thread.
 */
@NullMarked
public final class PhysicalWalletRepositoryDecorator implements WalletRepository {

    private static final Duration MARSHAL_TIMEOUT = Duration.ofSeconds(5);

    private final WalletRepository delegate;
    private final PendingTransactionRepository pendingRepo;
    private final BukkitInventoryCalculations calculations;
    private final CurrencyRegistry registry;
    private final Scheduler scheduler;
    private final PlayerLookup players;
    private final Logger log;

    public PhysicalWalletRepositoryDecorator(
            WalletRepository delegate,
            PendingTransactionRepository pendingRepo,
            BukkitInventoryCalculations calculations,
            CurrencyRegistry registry,
            Scheduler scheduler,
            PlayerLookup players,
            Logger log) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.pendingRepo = Objects.requireNonNull(pendingRepo, "pendingRepo");
        this.calculations = Objects.requireNonNull(calculations, "calculations");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Optional<Wallet> findByOwner(PlayerRef owner) {
        Optional<Wallet> dbWalletOpt = delegate.findByOwner(owner);
        boolean isOnline = players.isOnline(owner.uuid());
        if (dbWalletOpt.isEmpty() && !isOnline) {
            return Optional.empty();
        }

        Wallet dbWallet = dbWalletOpt.orElseGet(() -> Wallet.empty(owner));
        Map<Currency, Money> balances = new LinkedHashMap<>(dbWallet.balances());
        boolean hasPhysical = registry.all().stream().anyMatch(Currency::isPhysical);
        if (!hasPhysical) {
            return Optional.of(Wallet.of(owner, balances));
        }
        if (isOnline) {
            onEntityVoid(owner, player -> {
                for (Currency currency : registry.all()) {
                    if (currency.isPhysical()) {
                        balances.put(currency, Money.of(currency, calculations.getBalance(player, currency)));
                    }
                }
            });
        } else {
            for (Currency currency : registry.all()) {
                if (currency.isPhysical()) {
                    balances.put(currency, Money.zero(currency));
                }
            }
        }
        return Optional.of(Wallet.of(owner, balances));
    }

    @Override
    public Wallet ensureOwner(PlayerRef owner) {
        delegate.ensureOwner(owner);
        return findByOwner(owner).orElseGet(() -> Wallet.empty(owner));
    }

    @Override
    public void upsertBalance(PlayerRef owner, Money balance) {
        if (!balance.currency().isPhysical()) {
            delegate.upsertBalance(owner, balance);
            return;
        }
        if (!players.isOnline(owner.uuid())) {
            pendingRepo.queueCredit(owner.uuid(), balance.currency().id().value(), balance.amount());
            return;
        }
        onEntityVoid(owner, player -> {
            BigDecimal current = calculations.getBalance(player, balance.currency());
            BigDecimal diff = balance.amount().subtract(current);
            if (diff.signum() > 0) {
                calculations.credit(player, Money.of(balance.currency(), diff));
            } else if (diff.signum() < 0) {
                calculations.debit(player, Money.of(balance.currency(), diff.negate()));
            }
        });
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        if (!amount.currency().isPhysical()) {
            return delegate.debit(owner, amount);
        }
        if (!players.isOnline(owner.uuid())) {
            return Result.err(TransferError.PLAYER_OFFLINE);
        }
        return onEntity(owner, player -> calculations.debit(player, amount), Result.err(TransferError.PLAYER_OFFLINE));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        if (!amount.currency().isPhysical()) {
            return delegate.credit(owner, amount);
        }
        if (!players.isOnline(owner.uuid())) {
            pendingRepo.queueCredit(owner.uuid(), amount.currency().id().value(), amount.amount());
            return Result.ok();
        }
        return onEntity(owner, player -> calculations.credit(player, amount), Result.err(TransferError.PLAYER_OFFLINE));
    }

    @Override
    public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
        if (!amount.currency().isPhysical()) {
            return delegate.transfer(from, to, amount);
        }
        if (!players.isOnline(from.uuid())) {
            return Result.err(TransferError.PLAYER_OFFLINE);
        }
        Result<Unit, TransferError> debitResult =
                onEntity(from, player -> calculations.debit(player, amount), Result.err(TransferError.PLAYER_OFFLINE));
        if (debitResult.isErr()) {
            return debitResult;
        }
        if (players.isOnline(to.uuid())) {
            Result<Unit, TransferError> creditResult = onEntity(
                    to, player -> calculations.credit(player, amount), Result.err(TransferError.PLAYER_OFFLINE));
            if (creditResult.isErr()) {
                // Roll the debit back on the sender's thread; the move commits both legs or neither.
                onEntity(from, player -> calculations.credit(player, amount), Result.ok());
                return creditResult;
            }
        } else {
            pendingRepo.queueCredit(to.uuid(), amount.currency().id().value(), amount.amount());
        }
        return Result.ok();
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        if (!currency.isPhysical()) {
            return delegate.top(currency, limit);
        }
        // The physical baltop scans every online inventory; do it on the global region thread where the roster
        // is consistent, then sort and trim off-thread.
        List<BaltopRow> rows = onGlobal(
                () -> {
                    List<BaltopRow> collected = new ArrayList<>();
                    for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                        BigDecimal bal = calculations.getBalance(player, currency);
                        if (bal.signum() > 0) {
                            collected.add(new BaltopRow(
                                    new PlayerRef(player.getUniqueId(), player.getName()), Money.of(currency, bal)));
                        }
                    }
                    return collected;
                },
                new ArrayList<>());
        rows.sort((r1, r2) -> r2.balance().amount().compareTo(r1.balance().amount()));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    /** Run {@code work} on {@code owner}'s entity thread and wait for its result, falling back on a miss. */
    private <T> T onEntity(PlayerRef owner, java.util.function.Function<Player, T> work, T fallback) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.onEntity(owner, () -> {
            Player player = org.bukkit.Bukkit.getPlayer(owner.uuid());
            if (player == null || !player.isOnline()) {
                future.complete(fallback);
                return;
            }
            try {
                future.complete(work.apply(player));
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        });
        return await(future, fallback);
    }

    /** Run a side-effecting {@code work} on {@code owner}'s entity thread and wait for it to finish. */
    private void onEntityVoid(PlayerRef owner, java.util.function.Consumer<Player> work) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        scheduler.onEntity(owner, () -> {
            Player player = org.bukkit.Bukkit.getPlayer(owner.uuid());
            if (player == null || !player.isOnline()) {
                future.complete(Boolean.FALSE);
                return;
            }
            try {
                work.accept(player);
                future.complete(Boolean.TRUE);
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        });
        await(future, Boolean.FALSE);
    }

    private <T> T onGlobal(Supplier<T> work, T fallback) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.onGlobal(() -> {
            try {
                future.complete(work.get());
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        });
        return await(future, fallback);
    }

    private <T> T await(CompletableFuture<T> future, T fallback) {
        try {
            return future.get(MARSHAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (TimeoutException timeout) {
            log.warn("physical wallet inventory marshalling timed out after {}ms", MARSHAL_TIMEOUT.toMillis());
            return fallback;
        } catch (java.util.concurrent.ExecutionException failure) {
            log.error("physical wallet inventory work failed", failure);
            return fallback;
        }
    }
}
