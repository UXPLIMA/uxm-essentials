package com.uxplima.uxmessentials.economy.adapter.vault;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Publishes the native wallet as a Vault economy: the outward half of {@link VaultEconomyAdapter}, which
 * brings a foreign Vault economy in. Without this class every third-party plugin that pays a reward asks the
 * {@code ServicesManager} for {@code net.milkbowl.vault.economy.Economy}, finds nothing, and pays nothing,
 * however many balances this plugin is holding.
 *
 * <p>Vault has one currency, so this view serves exactly the configured default {@link Currency} and ignores
 * the world argument of every world-aware method: a balance here is per account, never per world. A caller
 * that wants another currency has to use this plugin's own API, which has always been able to say which one.
 *
 * <p>Vault's bank API is a set of named shared accounts, which is not what {@code /bank} is. Every bank
 * method answers {@link ResponseType#NOT_IMPLEMENTED} rather than mapping a personal account onto a shape
 * that does not fit it.
 *
 * <p>The name-keyed methods are Vault's deprecated half. They resolve a name through the plugin's lookup from memory
 * only, and refuse otherwise, because the alternative is a blocking lookup on whichever thread the caller happens to
 * hold. The server's own name cache is not asked first: it matches any case and keeps one profile per lower-cased
 * name, so on an offline-mode server, where {@code Steve} and {@code steve} are two accounts, money would move to
 * whichever joined last. A name that could mean several accounts is refused too. A UUID-keyed caller is never
 * refused.
 *
 * <p>Every method is synchronous, because Vault's contract is: a caller wants a number back, so there is no
 * seam to hand the work to the scheduler through. The rule that every provider call runs off the tick thread
 * governs calling a foreign economy and cannot govern answering one. So the wallet read behind this view has
 * to stay cheap enough to answer inline, which is what the repository cache is for, and this class does as
 * little of it as it can: a movement reads the balance once, before the write, and derives the figure Vault
 * wants by arithmetic rather than reading again. Reading afterwards would be a guaranteed database round trip
 * on the caller's thread, because the write has just dropped that owner from the cache.
 */
@NullMarked
// Vault deprecated its name-keyed half and never removed it. An implementation still has to answer every
// one of those methods, so the warning says nothing this class can act on.
@SuppressWarnings("deprecation")
public final class NativeVaultEconomy implements Economy {

    private static final String NOT_A_BANK = "uxmEssentials has no named bank accounts";
    private static final String UNKNOWN_PLAYER = "unknown player";
    private static final String NEGATIVE_AMOUNT = "amount must not be negative";
    private static final String UNREACHABLE =
            "the ledger failed during the change and cannot say whether it was stored; check the balance";

    /** At most one failed write is logged with its cause per this window; the ones in between are counted. */
    private static final Duration LOG_EVERY = Duration.ofMinutes(1);

    private final EconomyProvider provider;
    private final Currency currency;
    private final Server server;
    private final PlayerLookup players;
    private final Logger log;
    private final LongSupplier nanos;
    private final AtomicLong nextLogAt = new AtomicLong();
    private final AtomicBoolean loggedOnce = new AtomicBoolean();
    private final AtomicInteger suppressed = new AtomicInteger();

    public NativeVaultEconomy(
            EconomyProvider provider, Currency currency, Server server, PlayerLookup players, Logger log) {
        this(provider, currency, server, players, log, System::nanoTime);
    }

    NativeVaultEconomy(
            EconomyProvider provider,
            Currency currency,
            Server server,
            PlayerLookup players,
            Logger log,
            LongSupplier nanos) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.server = Objects.requireNonNull(server, "server");
        this.players = Objects.requireNonNull(players, "players");
        this.log = Objects.requireNonNull(log, "log");
        this.nanos = Objects.requireNonNull(nanos, "nanos");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "uxmEssentials";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return currency.precision();
    }

    @Override
    public String format(double amount) {
        return MoneyFormat.withSymbol(Money.of(currency, BigDecimal.valueOf(amount)));
    }

    @Override
    public String currencyNamePlural() {
        return currency.plural();
    }

    @Override
    public String currencyNameSingular() {
        return currency.id().value();
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return provider.hasAccount(ref(player), currency);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(String name) {
        OfflinePlayer player = cached(name);
        return player != null && hasAccount(player);
    }

    @Override
    public boolean hasAccount(String name, String world) {
        return hasAccount(name);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        PlayerRef owner = ref(player);
        try {
            return provider.balance(owner, currency).amount().doubleValue();
        } catch (RuntimeException unreachable) {
            // Vault's balance has no failure shape; an exception would land in the calling plugin's code. Nothing
            // is reported as held, so a has() check on it refuses rather than lets a payment through.
            logFailure("balance", owner, Money.zero(currency), unreachable);
            return 0d;
        }
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String name) {
        OfflinePlayer player = cached(name);
        return player == null ? 0d : getBalance(player);
    }

    @Override
    public double getBalance(String name, String world) {
        return getBalance(name);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String name, double amount) {
        OfflinePlayer player = cached(name);
        return player != null && has(player, amount);
    }

    @Override
    public boolean has(String name, String world, double amount) {
        return has(name, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return failure(0d, getBalance(player), NEGATIVE_AMOUNT);
        }
        PlayerRef owner = ref(player);
        Money moved = Money.of(currency, BigDecimal.valueOf(amount));
        Money before;
        try {
            before = provider.balance(owner, currency);
        } catch (RuntimeException unreachable) {
            // The balance did not answer, so nothing was taken yet: Vault's failure answer, not an exception.
            logFailure("withdraw", owner, moved, unreachable);
            return failure(amount, 0d, UNREACHABLE);
        }
        try {
            if (provider.debit(owner, moved).isErr()) {
                return failure(amount, before.amount().doubleValue(), "insufficient funds");
            }
        } catch (RuntimeException unreachable) {
            // The calling plugin gets Vault's failure answer, the one shape it handles, rather than an exception
            // thrown into its own code. A failure at commit leaves the outcome unknown, so the answer does not claim
            // that nothing moved.
            logFailure("withdraw", owner, moved, unreachable);
            return failure(amount, before.amount().doubleValue(), UNREACHABLE);
        }
        return success(amount, before.minus(moved).amount().doubleValue());
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, double amount) {
        OfflinePlayer player = cached(name);
        return player == null ? failure(amount, 0d, UNKNOWN_PLAYER) : withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, String world, double amount) {
        return withdrawPlayer(name, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return failure(0d, getBalance(player), NEGATIVE_AMOUNT);
        }
        PlayerRef owner = ref(player);
        Money moved = Money.of(currency, BigDecimal.valueOf(amount));
        Money before;
        try {
            before = provider.balance(owner, currency);
        } catch (RuntimeException unreachable) {
            logFailure("deposit", owner, moved, unreachable);
            return failure(amount, 0d, UNREACHABLE);
        }
        // No ensureAccount here: a native credit opens the account itself, and against a foreign backend the
        // call does nothing at all. Asking for it separately is a database write per reward paid, on whichever
        // thread the caller holds.
        try {
            if (provider.credit(owner, moved).isErr()) {
                return failure(amount, before.amount().doubleValue(), "the balance ceiling would be passed");
            }
        } catch (RuntimeException unreachable) {
            logFailure("deposit", owner, moved, unreachable);
            return failure(amount, before.amount().doubleValue(), UNREACHABLE);
        }
        return success(amount, before.plus(moved).amount().doubleValue());
    }

    /**
     * Tell the console a write failed. A database outage fails every reward another plugin pays, possibly every
     * tick, so the cause is logged once per {@link #LOG_EVERY} and the failures in between are counted into the next
     * line.
     */
    private void logFailure(String op, PlayerRef owner, Money moved, RuntimeException failure) {
        long now = nanos.getAsLong();
        if (loggedOnce.compareAndSet(false, true)) {
            nextLogAt.set(now + LOG_EVERY.toNanos());
        } else {
            long due = nextLogAt.get();
            if (now - due < 0 || !nextLogAt.compareAndSet(due, now + LOG_EVERY.toNanos())) {
                suppressed.incrementAndGet();
                return;
            }
        }
        log.error(
                "event=vault_write_failed op=" + op + " owner=" + owner.uuid() + " amount="
                        + moved.amount().toPlainString() + " currency="
                        + currency.id().value() + " suppressed="
                        + suppressed.getAndSet(0) + " (the outcome is not known; check the balance)",
                failure);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, double amount) {
        OfflinePlayer player = cached(name);
        return player == null ? failure(amount, 0d, UNKNOWN_PLAYER) : depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, String world, double amount) {
        return depositPlayer(name, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        provider.ensureAccount(ref(player), currency);
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String name) {
        OfflinePlayer player = cached(name);
        return player != null && createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String name, String world) {
        return createPlayerAccount(name);
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer owner) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, String owner) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    private PlayerRef ref(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        String name = player.getName();
        return new PlayerRef(
                player.getUniqueId(), name == null ? player.getUniqueId().toString() : name);
    }

    /** The account {@code name} names, from memory; null for a name nobody here used or several accounts share. */
    private @Nullable OfflinePlayer cached(String name) {
        return players.matchByNameFromMemory(Objects.requireNonNull(name, "name"))
                .player()
                .map(found -> server.getOfflinePlayer(found.uuid()))
                .orElse(null);
    }

    private static EconomyResponse success(double amount, double balance) {
        return new EconomyResponse(amount, balance, ResponseType.SUCCESS, "");
    }

    private static EconomyResponse failure(double amount, double balance, String reason) {
        return new EconomyResponse(amount, balance, ResponseType.FAILURE, reason);
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0d, 0d, ResponseType.NOT_IMPLEMENTED, NOT_A_BANK);
    }
}
