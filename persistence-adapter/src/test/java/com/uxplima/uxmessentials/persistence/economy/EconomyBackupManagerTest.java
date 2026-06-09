package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.COINS;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.CURRENCIES;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.coins;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.randomPlayer;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage of {@link EconomyBackupManager} against the embedded SQLite backend: a backup dumps every wallet
 * row in one read query, and a restore replays one player's balances back atomically and refreshes the cache.
 */
class EconomyBackupManagerTest {

    private Persistence persistence;
    private WalletRepository walletRepository;
    private EconomyBackupManager backupManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(
                new EconomyTestSupport.SqliteConfig(),
                dataFolder,
                EconomyTestSupport.baselineMigrations(),
                new EconomyTestSupport.NoopLogger());
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        walletRepository = new CachedWalletRepository(new JooqWalletRepository(persistence.dsl(), CURRENCIES, clock));
        backupManager = new EconomyBackupManager(persistence.dsl(), walletRepository, CURRENCIES, dataFolder.toFile());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void backupAndRestoreRoundTripsAPlayerBalance() throws Exception {
        PlayerRef owner = randomPlayer();
        walletRepository.credit(owner, coins(500));

        String timestamp = backupManager.backupAll();
        assertThat(backupManager.getBackupDates()).contains(timestamp);

        // Move the live balance after the snapshot, then restore: the restored figure must win.
        walletRepository.debit(owner, coins(200));
        assertThat(walletRepository.findByOwner(owner).orElseThrow().balanceOf(COINS))
                .isEqualTo(coins(300));

        assertThat(backupManager.restorePlayer(owner, timestamp)).isTrue();
        assertThat(walletRepository.findByOwner(owner).orElseThrow().balanceOf(COINS))
                .isEqualTo(coins(500));
    }

    @Test
    void restoreReturnsFalseForUnknownTimestamp() throws Exception {
        assertThat(backupManager.restorePlayer(randomPlayer(), "2000-01-01_00-00-00"))
                .isFalse();
    }

    @Test
    void restoreReturnsFalseForPlayerNotInBackup() throws Exception {
        walletRepository.credit(randomPlayer(), coins(10));
        String timestamp = backupManager.backupAll();

        assertThat(backupManager.restorePlayer(randomPlayer(), timestamp)).isFalse();
    }

    @Test
    void backupCapturesEveryOwnerInOneSnapshot() throws Exception {
        PlayerRef first = randomPlayer();
        PlayerRef second = randomPlayer();
        walletRepository.credit(first, coins(11));
        walletRepository.credit(second, coins(22));

        String timestamp = backupManager.backupAll();
        // Drain both, restore both from the same snapshot.
        walletRepository.debit(first, coins(11));
        walletRepository.debit(second, coins(22));

        assertThat(backupManager.restorePlayer(first, timestamp)).isTrue();
        assertThat(backupManager.restorePlayer(second, timestamp)).isTrue();
        assertThat(walletRepository.findByOwner(first).orElseThrow().balanceOf(COINS))
                .isEqualTo(coins(11));
        assertThat(walletRepository.findByOwner(second).orElseThrow().balanceOf(COINS))
                .isEqualTo(coins(22));
    }
}
