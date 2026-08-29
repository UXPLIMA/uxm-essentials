package com.uxplima.uxmessentials.economy.adapter.vault;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
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
 * <p>The name-keyed methods are Vault's deprecated half. They resolve only a player the server already has
 * cached, and refuse otherwise, because the alternative is a blocking profile lookup on whichever thread the
 * caller happens to hold. A UUID-keyed caller is never refused.
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

    private final EconomyProvider provider;
    private final Currency currency;
    private final Server server;

    public NativeVaultEconomy(EconomyProvider provider, Currency currency, Server server) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.server = Objects.requireNonNull(server, "server");
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
        return provider.balance(ref(player), currency).amount().doubleValue();
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
        Money before = provider.balance(owner, currency);
        if (provider.debit(owner, moved).isErr()) {
            return failure(amount, before.amount().doubleValue(), "insufficient funds");
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
        Money before = provider.balance(owner, currency);
        // No ensureAccount here: a native credit opens the account itself, and against a foreign backend the
        // call does nothing at all. Asking for it separately is a database write per reward paid, on whichever
        // thread the caller holds.
        if (provider.credit(owner, moved).isErr()) {
            return failure(amount, before.amount().doubleValue(), "the balance ceiling would be passed");
        }
        return success(amount, before.plus(moved).amount().doubleValue());
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

    private @Nullable OfflinePlayer cached(String name) {
        return server.getOfflinePlayerIfCached(Objects.requireNonNull(name, "name"));
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
