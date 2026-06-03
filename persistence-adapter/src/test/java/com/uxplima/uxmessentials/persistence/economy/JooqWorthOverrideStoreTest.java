package com.uxplima.uxmessentials.persistence.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;

import com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.NoopLogger;
import com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.SqliteConfig;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWorthOverrideStore} against the default embedded SQLite backend with the
 * Flyway baseline applied (including the V12 worth-override table) — the tested default of the backend-parity
 * matrix. It proves the round-trip (set → find) preserves the price, the case-folded lookup, the
 * material-key upsert (a re-set overwrites the price in place rather than inserting), the {@code exists} check,
 * and the remove contract (true on the first removal, false on the second).
 */
class JooqWorthOverrideStoreTest {

    private Persistence persistence;
    private JooqWorthOverrideStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(
                new SqliteConfig(), dataFolder, EconomyTestSupport.baselineMigrations(), new NoopLogger());
        store = new JooqWorthOverrideStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void setsAndFindsAPriceRoundTrip() {
        store.set("diamond", new BigDecimal("25.0000"));

        assertThat(store.find("diamond"))
                .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo(new BigDecimal("25")));
    }

    @Test
    void findFoldsTheLookupNameCase() {
        store.set("diamond", new BigDecimal("25.0000"));

        assertThat(store.find("DIAMOND")).isPresent();
    }

    @Test
    void setUpsertsOnTheMaterialKeyRatherThanInserting() {
        store.set("diamond", new BigDecimal("10.0000"));
        store.set("diamond", new BigDecimal("30.0000"));

        assertThat(store.find("diamond"))
                .hasValueSatisfying(price -> assertThat(price).isEqualByComparingTo(new BigDecimal("30")));
    }

    @Test
    void existsReflectsWhetherAnOverrideIsStored() {
        assertThat(store.exists("diamond")).isFalse();

        store.set("diamond", new BigDecimal("10.0000"));

        assertThat(store.exists("diamond")).isTrue();
    }

    @Test
    void removeReturnsTrueOnceThenFalse() {
        store.set("diamond", new BigDecimal("10.0000"));

        assertThat(store.remove("diamond")).isTrue();
        assertThat(store.remove("diamond")).isFalse();
        assertThat(store.exists("diamond")).isFalse();
    }
}
