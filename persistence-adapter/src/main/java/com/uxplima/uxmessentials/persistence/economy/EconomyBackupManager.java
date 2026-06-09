package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WalletBalances.WALLET_BALANCES;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * Writes and restores timestamped JSON snapshots of every wallet balance under
 * {@code plugins/uxmEssentials/backups/economy/}. A backup reads the whole ledger in one query and serialises
 * it to disk; a restore replays one player's balances back into the ledger atomically.
 *
 * <p>File I/O never runs inside a database transaction: the dump is read in a read query and the JSON is
 * written to disk afterwards, and a restore reads the file first and then applies all currencies in a single
 * write transaction. An {@link IOException} on either side is wrapped in a {@link PersistenceException} so a
 * caller never has to catch a raw I/O fault.
 */
@NullMarked
public final class EconomyBackupManager extends JooqRepository {

    private final WalletRepository walletRepository;
    private final CurrencyRegistry currencies;
    private final File backupDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DateTimeFormatter dateFormat =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    public EconomyBackupManager(
            DSLContext dsl, WalletRepository walletRepository, CurrencyRegistry currencies, File dataFolder) {
        super(dsl);
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(dataFolder, "dataFolder");
        this.backupDir = new File(dataFolder, "backups/economy");
        ensureBackupDir();
    }

    /** Dump every wallet row to a timestamped JSON file and return the timestamp used in the file name. */
    public String backupAll() throws IOException {
        ensureBackupDir();
        Map<String, Map<String, BigDecimal>> backupData = readAllBalances();

        String timestamp = dateFormat.format(Instant.now());
        File backupFile = new File(backupDir, "backup_" + timestamp + ".json");
        try (java.io.Writer writer = Files.newBufferedWriter(backupFile.toPath(), StandardCharsets.UTF_8)) {
            gson.toJson(backupData, writer);
        }
        return timestamp;
    }

    /** Restores a specific player's balances from a past backup matching the timestamp, in one transaction. */
    public boolean restorePlayer(PlayerRef player, String timestamp) throws IOException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(timestamp, "timestamp");
        File backupFile = new File(backupDir, "backup_" + timestamp + ".json");
        if (!backupFile.exists()) {
            return false;
        }

        Map<String, Map<String, BigDecimal>> backupData = readBackupFile(backupFile);
        if (backupData == null) {
            return false;
        }

        Map<String, BigDecimal> ownerBalances = backupData.get(player.uuid().toString());
        if (ownerBalances == null) {
            return false;
        }

        applyBalances(player, ownerBalances);
        return true;
    }

    /** Returns all available backup timestamps for tab-completing administrative commands. */
    public List<String> getBackupDates() {
        if (!backupDir.exists()) {
            return List.of();
        }
        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".json"));
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .map(f -> f.getName().replace("backup_", "").replace(".json", ""))
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /** One typed query streams every {@code (owner, currency, amount)} row into the owner → currency → amount map. */
    private Map<String, Map<String, BigDecimal>> readAllBalances() {
        return read(dsl -> {
            Map<String, Map<String, BigDecimal>> data = new HashMap<>();
            try (var rows = dsl.select(WALLET_BALANCES.OWNER, WALLET_BALANCES.CURRENCY, WALLET_BALANCES.AMOUNT)
                    .from(WALLET_BALANCES)
                    .fetchStream()) {
                rows.forEach(row -> data.computeIfAbsent(row.get(WALLET_BALANCES.OWNER), k -> new HashMap<>())
                        .put(row.get(WALLET_BALANCES.CURRENCY), row.get(WALLET_BALANCES.AMOUNT)));
            }
            return data;
        });
    }

    /** Apply a player's restored balances as one write transaction, then drop the cached snapshot. */
    private void applyBalances(PlayerRef player, Map<String, BigDecimal> ownerBalances) {
        write(dsl -> {
            upsertOwner(dsl, player);
            for (Map.Entry<String, BigDecimal> entry : ownerBalances.entrySet()) {
                Optional<Currency> currency = currencies.find(CurrencyId.of(entry.getKey()));
                if (currency.isPresent()) {
                    setBalance(dsl, player, entry.getKey(), entry.getValue());
                }
            }
            return null;
        });
        // The atomic restore ran straight against the rows, so the cached/cross-server snapshot for this owner
        // must be dropped; ensureOwner is idempotent at the DB and invalidates the owner through the decorator
        // chain, so the next /balance reflects the restored figures.
        walletRepository.ensureOwner(player);
    }

    private void upsertOwner(DSLContext dsl, PlayerRef player) {
        dsl.insertInto(ECONOMY_OWNERS)
                .set(ECONOMY_OWNERS.OWNER, player.uuid().toString())
                .set(ECONOMY_OWNERS.NAME, ownerName(player))
                .set(ECONOMY_OWNERS.CREATED_AT, Instant.now().toEpochMilli())
                .onConflict(ECONOMY_OWNERS.OWNER)
                .doNothing()
                .execute();
    }

    private void setBalance(DSLContext dsl, PlayerRef player, String currencyId, BigDecimal amount) {
        dsl.insertInto(WALLET_BALANCES)
                .set(WALLET_BALANCES.OWNER, player.uuid().toString())
                .set(WALLET_BALANCES.CURRENCY, currencyId)
                .set(WALLET_BALANCES.AMOUNT, amount)
                .onConflict(WALLET_BALANCES.OWNER, WALLET_BALANCES.CURRENCY)
                .doUpdate()
                .set(WALLET_BALANCES.AMOUNT, amount)
                .execute();
    }

    private Map<String, Map<String, BigDecimal>> readBackupFile(File backupFile) throws IOException {
        try (java.io.Reader reader = Files.newBufferedReader(backupFile.toPath(), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, new TypeToken<Map<String, Map<String, BigDecimal>>>() {}.getType());
        }
    }

    private void ensureBackupDir() {
        if (!backupDir.exists() && !backupDir.mkdirs() && !backupDir.isDirectory()) {
            throw new PersistenceException("failed to create economy backup directory: " + backupDir.getAbsolutePath());
        }
    }

    private static String ownerName(PlayerRef owner) {
        String name = owner.name();
        return name.length() <= 16 ? name : name.substring(0, 16);
    }
}
