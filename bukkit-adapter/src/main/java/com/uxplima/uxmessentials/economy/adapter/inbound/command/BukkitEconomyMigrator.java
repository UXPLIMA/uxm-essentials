package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.io.File;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.uxplima.uxmessentials.economy.application.port.EconomyMigrator;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Platform-side implementation of {@link EconomyMigrator} migrating data to the active {@link EconomyProvider}.
 */
@NullMarked
public final class BukkitEconomyMigrator implements EconomyMigrator {

    private final Plugin plugin;
    private final EconomyProvider economyProvider;
    private final Currency defaultCurrency;

    public BukkitEconomyMigrator(Plugin plugin, EconomyProvider economyProvider, Currency defaultCurrency) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
    }

    @Override
    public Result<Unit, String> migrate(String source) {
        Objects.requireNonNull(source, "source");
        String lowerSource = source.toLowerCase(java.util.Locale.ROOT).trim();
        return switch (lowerSource) {
            case "essentialsx", "essentials" -> migrateEssentials();
            case "playerpoints" -> migratePlayerPoints();
            case "vault" -> migrateVault();
            default -> Result.err("unknown source: " + source + ". Supported: essentialsx, playerpoints, vault");
        };
    }

    private Result<Unit, String> migrateEssentials() {
        File folder = new File(plugin.getDataFolder().getParentFile(), "Essentials/userdata");
        if (!folder.exists() || !folder.isDirectory()) {
            return Result.err("EssentialsX userdata folder not found at: " + folder.getAbsolutePath());
        }

        File[] files = folder.listFiles(
                (dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            return Result.err("no EssentialsX userdata files found.");
        }

        for (File file : files) {
            String name = file.getName();
            String uuidStr = name.substring(0, name.length() - 4); // Strip .yml
            try {
                UUID uuid = UUID.fromString(uuidStr);
                org.bukkit.configuration.file.YamlConfiguration config =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                if (config.contains("money")) {
                    double moneyVal = config.getDouble("money");
                    if (moneyVal > 0) {
                        PlayerRef ref = new PlayerRef(uuid, uuidStr);
                        economyProvider.ensureAccount(ref, defaultCurrency);
                        economyProvider.credit(ref, Money.of(defaultCurrency, BigDecimal.valueOf(moneyVal)));
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore non-UUID filenames
            }
        }
        return Result.ok();
    }

    private Result<Unit, String> migrateVault() {
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp == null) {
            return Result.err("no Vault economy provider found registered on this server.");
        }

        net.milkbowl.vault.economy.Economy vaultEco = rsp.getProvider();
        if (vaultEco.getName().equalsIgnoreCase("uxmEssentials")) {
            return Result.err("currently active Vault provider is uxmEssentials itself. Cannot migrate from self.");
        }

        OfflinePlayer[] offlinePlayers = plugin.getServer().getOfflinePlayers();
        for (OfflinePlayer op : offlinePlayers) {
            if (vaultEco.hasAccount(op)) {
                double bal = vaultEco.getBalance(op);
                if (bal > 0) {
                    PlayerRef ref = new PlayerRef(
                            op.getUniqueId(),
                            op.getName() != null
                                    ? op.getName()
                                    : op.getUniqueId().toString());
                    economyProvider.ensureAccount(ref, defaultCurrency);
                    economyProvider.credit(ref, Money.of(defaultCurrency, BigDecimal.valueOf(bal)));
                }
            }
        }
        return Result.ok();
    }

    private Result<Unit, String> migratePlayerPoints() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
            return Result.err("PlayerPoints plugin is not enabled on this server.");
        }

        try {
            Class<?> clazz = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object api = clazz.getMethod("getAPI").invoke(null);
            java.lang.reflect.Method lookMethod = api.getClass().getMethod("look", UUID.class);

            OfflinePlayer[] offlinePlayers = plugin.getServer().getOfflinePlayers();
            for (OfflinePlayer op : offlinePlayers) {
                int points = (Integer) lookMethod.invoke(api, op.getUniqueId());
                if (points > 0) {
                    PlayerRef ref = new PlayerRef(
                            op.getUniqueId(),
                            op.getName() != null
                                    ? op.getName()
                                    : op.getUniqueId().toString());
                    economyProvider.ensureAccount(ref, defaultCurrency);
                    economyProvider.credit(ref, Money.of(defaultCurrency, BigDecimal.valueOf(points)));
                }
            }
            return Result.ok();
        } catch (Exception e) {
            return Result.err("failed to invoke PlayerPoints API: " + e.getMessage());
        }
    }
}
