package com.uxplima.uxmessentials.migration.adapter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.migration.convert.live.BalanceFeed;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import net.milkbowl.vault.economy.Economy;
import org.jspecify.annotations.NullMarked;

/**
 * Reads live balances out of the registered Vault {@link Economy} provider for the importer. Every account this
 * server or the shared name list knows (see {@link FeedAccounts}) that Vault holds an account for, with a positive balance, becomes a balance-only {@link ImportedUser}
 * the writer seeds a wallet with under {@code defaultCurrency}.
 *
 * <p>This is the platform-side {@link BalanceFeed} the {@code vault} live source reads through, so the
 * migration module stays free of the Vault SDK. The read runs off the calling thread on the import
 * executor, the same as the old {@code /eco migrate} path did; the Vault economy calls themselves run off
 * the main thread, which matches that prior behaviour. Vault providers are expected to answer balance
 * queries off-tick.
 *
 * <p>A provider naming itself {@code uxmEssentials} is us: the feed reports unavailable and yields nothing
 * rather than migrating an economy from itself.
 */
@NullMarked
final class VaultBalanceFeed implements BalanceFeed {

    private static final String SELF_PROVIDER = "uxmEssentials";

    private final Plugin plugin;
    private final Currency defaultCurrency;
    private final FeedAccounts accounts;

    VaultBalanceFeed(Plugin plugin, Currency defaultCurrency, FeedAccounts accounts) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    @Override
    public boolean available() {
        return economy().isPresent();
    }

    @Override
    public Stream<ImportedUser> users() {
        Optional<Economy> economy = economy();
        if (economy.isEmpty()) {
            return Stream.empty();
        }
        Economy eco = economy.get();
        return accounts.all().map(account -> toUser(account, eco)).flatMap(Optional::stream);
    }

    /** Maps one offline player to a balance-only user, or empty when it has no positive Vault balance. */
    private Optional<ImportedUser> toUser(FeedAccounts.Account account, Economy eco) {
        OfflinePlayer op = account.player();
        if (!eco.hasAccount(op)) {
            return Optional.empty();
        }
        double balance = eco.getBalance(op);
        if (balance <= 0) {
            return Optional.empty();
        }
        BigDecimal figure = defaultCurrency.normalize(BigDecimal.valueOf(balance));
        PlayerRef owner = new PlayerRef(op.getUniqueId(), account.name());
        return Optional.of(new ImportedUser(owner, List.of(), Optional.of(figure), List.of()));
    }

    /** The registered Vault provider, unless none is present or the registered one is uxmEssentials itself. */
    private Optional<Economy> economy() {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return Optional.empty();
        }
        Economy eco = rsp.getProvider();
        if (eco.getName().equalsIgnoreCase(SELF_PROVIDER)) {
            return Optional.empty();
        }
        return Optional.of(eco);
    }
}
