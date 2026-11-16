package com.uxplima.uxmessentials.persistence.vaults;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemReturns;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository.Columns;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import com.uxplima.uxmessentials.vaults.domain.VaultStamp;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What a vault cost to create, and the guards on its writes, through the real migrations. */
@EveryBackend
class VaultPaymentStoreTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e7"), "Alex");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final VaultPayment FIVE_COINS = new VaultPayment(new BigDecimal("5"), "coins");

    private DSLContext dsl;
    private JooqVaultRepository vaults;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        vaults = new JooqVaultRepository(dsl);
    }

    private static VaultId vault(int index) {
        return VaultId.of(ALEX, index);
    }

    private Vault stored(int index) {
        return vaults.find(vault(index)).orElseThrow();
    }

    @Test
    void aCreatedVaultKeepsWhatItCostAndTheCurrency() {

        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS));
        vaults.create(Vault.allocate(vault(2), new VaultSize(1), NOW));

        assertThat(stored(1).paid()).hasValueSatisfying(paid -> {
            assertThat(paid.amount()).isEqualByComparingTo("5");
            assertThat(paid.currencyId()).isEqualTo("coins");
        });
        assertThat(stored(2).paid()).isEmpty();
    }

    @Test
    void savingAVaultNeverChangesWhatItCost() {
        Vault created = Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS);
        vaults.create(created);

        vaults.save(
                created.withContents(ALEX, VaultContents.of(new byte[] {1}), NOW)
                        .result(),
                "staff");
        vaults.save(
                Vault.allocate(vault(1), new VaultSize(2), NOW, new VaultPayment(new BigDecimal("99"), "gems")),
                "staff");

        assertThat(stored(1).paid()).hasValueSatisfying(paid -> {
            assertThat(paid.amount()).isEqualByComparingTo("5");
            assertThat(paid.currencyId()).isEqualTo("coins");
        });
        assertThat(stored(1).size()).isEqualTo(new VaultSize(2));
    }

    @Test
    void anUpsertThatBringsBackADeletedVaultCarriesNoPayment() {
        Vault created = Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS);
        vaults.create(created);

        assertThat(vaults.delete(vault(1), stored(1).paid())).isTrue();
        vaults.save(
                created.withContents(ALEX, VaultContents.of(new byte[] {1}), NOW)
                        .result(),
                "staff");

        assertThat(stored(1).paid()).isEmpty();
    }

    @Test
    void anUpdateNeverBringsBackADeletedVaultAndNeverChangesWhatItCost() {
        Vault created = Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS);
        vaults.create(created);

        assertThat(vaults.update(created.renamedTo("loot"), Columns.PRESENTATION))
                .isTrue();
        assertThat(stored(1).displayName()).isEqualTo("loot");
        assertThat(stored(1).paid().orElseThrow().amount()).isEqualByComparingTo("5");

        vaults.delete(vault(1), stored(1).paid());
        assertThat(vaults.update(created.renamedTo("again"), Columns.PRESENTATION))
                .isFalse();
        assertThat(vaults.find(vault(1))).isEmpty();
    }

    @Test
    void aDeleteRemovesTheRowOnlyWhileItStillRecordsTheExpectedPayment() {
        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS));
        Optional<VaultPayment> read = stored(1).paid();

        assertThat(vaults.delete(vault(1), Optional.empty())).isFalse();
        assertThat(vaults.delete(vault(1), Optional.of(new VaultPayment(new BigDecimal("5"), "gems"))))
                .isFalse();
        assertThat(vaults.delete(vault(1), Optional.of(new VaultPayment(new BigDecimal("4"), "coins"))))
                .isFalse();
        assertThat(vaults.find(vault(1))).isPresent();

        assertThat(vaults.delete(vault(1), read)).isTrue();
        assertThat(vaults.delete(vault(1), read)).isFalse();
        assertThat(vaults.find(vault(1))).isEmpty();
    }

    @Test
    void aDeleteExpectingNoPaymentRemovesAFreeVaultButNotARowBroughtBackWithADifferentOne() {
        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW));
        vaults.create(Vault.allocate(vault(2), new VaultSize(1), NOW, FIVE_COINS));

        assertThat(vaults.delete(vault(1), Optional.empty())).isTrue();
        assertThat(vaults.delete(vault(2), Optional.empty())).isFalse();
        assertThat(vaults.find(vault(2))).isPresent();
    }

    @Test
    void aDeleteExpectingAFinerPaymentMatchesTheRoundedStoredOne() {
        VaultPayment fine = new VaultPayment(new BigDecimal("1.23459"), "coins");
        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW, fine));

        assertThat(vaults.delete(vault(1), Optional.of(fine))).isTrue();
    }

    @Test
    void creatingOverAnExistingVaultReportsItAndKeepsTheVaultThatWasThere() {
        assertThat(vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS)))
                .isTrue();
        vaults.writeContents(
                stored(1)
                        .withContents(ALEX, VaultContents.of(new byte[] {7}), NOW)
                        .result(),
                "close",
                null);

        assertThat(vaults.create(Vault.allocate(
                        vault(1), new VaultSize(1), NOW, new VaultPayment(new BigDecimal("50"), "coins"))))
                .isFalse();

        assertThat(stored(1).paid().orElseThrow().amount()).isEqualByComparingTo("5");
        assertThat(stored(1).contents()).isEqualTo(VaultContents.of(new byte[] {7}));
    }

    @Test
    void aPaymentFinerThanTheColumnIsStoredRoundedDown() {

        vaults.create(
                Vault.allocate(vault(1), new VaultSize(1), NOW, new VaultPayment(new BigDecimal("1.23459"), "coins")));

        assertThat(stored(1).paid().orElseThrow().amount()).isEqualByComparingTo("1.2345");
    }

    @Test
    void aFractionalPaymentReadBackFromTheStoreDeletesTheRow() {
        vaults.create(
                Vault.allocate(vault(1), new VaultSize(1), NOW, new VaultPayment(new BigDecimal("1.2345"), "coins")));

        Optional<VaultPayment> read = vaults.find(vault(1)).orElseThrow().paid();

        assertThat(read.orElseThrow().amount()).isEqualByComparingTo("1.2345");
        assertThat(vaults.delete(vault(1), read)).isTrue();
        assertThat(vaults.find(vault(1))).isEmpty();
    }

    @Test
    void keptItemsAreHandedOutOnceInTheOrderTheyWereKept() {
        JooqVaultItemReturns earlier = new JooqVaultItemReturns(dsl, Clock.fixed(NOW, ZoneOffset.UTC));
        JooqVaultItemReturns returns = new JooqVaultItemReturns(dsl, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        VaultContents first = VaultContents.of(new byte[] {1, 2});
        VaultContents second = VaultContents.of(new byte[] {3});
        returns.keep(ALEX.uuid(), second);
        earlier.keep(ALEX.uuid(), first);
        returns.keep(ALEX.uuid(), VaultContents.empty());

        assertThat(returns.kept(UUID.randomUUID())).isEmpty();
        List<VaultItemReturns.Kept> kept = returns.kept(ALEX.uuid());
        assertThat(kept).extracting(VaultItemReturns.Kept::items).containsExactly(first, second);

        assertThat(returns.claim(kept.get(0).id())).isTrue();
        assertThat(returns.claim(kept.get(0).id())).isFalse();
        assertThat(returns.kept(ALEX.uuid()))
                .extracting(VaultItemReturns.Kept::items)
                .containsExactly(second);
    }

    @Test
    void eachWriteTouchesOnlyItsOwnColumns() {
        Vault created = Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS);
        vaults.create(created);
        Vault opened = stored(1);

        vaults.update(opened.renamedTo("loot").iconSet("DIAMOND"), Columns.PRESENTATION);
        Instant later = NOW.plusSeconds(60);
        vaults.writeContents(
                opened.openedAt(new VaultSize(3))
                        .withContents(ALEX, VaultContents.of(new byte[] {9}), later)
                        .result(),
                "close",
                null);

        Vault after = stored(1);
        assertThat(after.displayName()).isEqualTo("loot");
        assertThat(after.iconMaterial()).isEqualTo("DIAMOND");
        assertThat(after.contents()).isEqualTo(VaultContents.of(new byte[] {9}));
        assertThat(after.size()).isEqualTo(new VaultSize(3));
        assertThat(after.lastTouched()).isEqualTo(later);

        vaults.update(opened.openedAt(new VaultSize(6)).renamedTo("stale"), Columns.SIZE);
        vaults.update(opened.renamedTo("fresh"), Columns.PRESENTATION);

        assertThat(stored(1).size()).isEqualTo(new VaultSize(6));
        assertThat(stored(1).displayName()).isEqualTo("fresh");
        assertThat(stored(1).contents()).isEqualTo(VaultContents.of(new byte[] {9}));
    }

    @Test
    void anUpsertOverAnExistingRowWritesTheItemsButLeavesTheName() {
        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW));
        Vault opened = stored(1);
        vaults.update(opened.renamedTo("loot"), Columns.PRESENTATION);

        vaults.save(
                opened.withContents(ALEX, VaultContents.of(new byte[] {4}), NOW).result(), "staff");

        assertThat(stored(1).displayName()).isEqualTo("loot");
        assertThat(stored(1).contents()).isEqualTo(VaultContents.of(new byte[] {4}));
    }

    @Test
    void aVaultBoughtAgainAfterADeleteIsANewRowWhoseVersionsAreNotComparedWithTheOldOne() {
        Vault bought = Vault.allocate(vault(1), new VaultSize(1), NOW);
        vaults.create(bought);
        Vault first = stored(1);
        vaults.writeContents(
                first.withContents(ALEX, VaultContents.of(new byte[] {1}), NOW).result(), "close", null);
        Vault written = stored(1);
        vaults.delete(vault(1), written.paid());

        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW));
        Vault again = stored(1);

        assertThat(first.stamp()).isEqualTo(bought.stamp());
        assertThat(written.stamp().version()).isEqualTo(1L);
        assertThat(again.stamp().version()).isZero();
        assertThat(again.stamp().sameRow(written.stamp())).isFalse();
        assertThat(again.stamp().before(written.stamp())).isFalse();
        assertThat(vaults.writeContents(written, "close", null).orElseThrow().version())
                .isEqualTo(1L);
    }

    @Test
    void aStaffUpsertThatInsertsARowGivesItALineageAndAFirstVersion() {

        VaultStamp inserted = vaults.save(
                Vault.of(vault(1), new VaultSize(1), VaultContents.of(new byte[] {3}), null, null, NOW), "staff");

        assertThat(inserted.lineage()).isNotNull();
        assertThat(inserted.version()).isEqualTo(1L);
    }

    @Test
    void aStaffUpsertThatBringsBackADeletedVaultStartsANewLineage() {
        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW));
        Vault old = stored(1);
        vaults.writeContents(
                old.withContents(ALEX, VaultContents.of(new byte[] {1}), NOW).result(), "close", null);
        vaults.delete(vault(1), old.paid());

        VaultStamp back = vaults.save(
                old.withContents(ALEX, VaultContents.of(new byte[] {2}), NOW).result(), "staff");

        assertThat(back.lineage()).isNotEqualTo(old.stamp().lineage());
        assertThat(old.stamp().sameRow(back)).isFalse();
        assertThat(stored(1).stamp()).isEqualTo(back);
    }

    @Test
    void aRetryWritesOnlyOverTheRowItWasWrittenAgainstOrOverItsOwnEarlierAttempt() {
        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW));
        Vault base = stored(1);
        Vault mine =
                base.withContents(ALEX, VaultContents.of(new byte[] {1}), NOW).result();
        Vault theirs =
                base.withContents(ALEX, VaultContents.of(new byte[] {2}), NOW).result();

        VaultStamp first = vaults.writeContents(mine, "mine", base.stamp()).orElseThrow();
        Optional<VaultStamp> again = vaults.writeContents(mine, "mine", base.stamp());
        VaultStamp other = vaults.writeContents(theirs, "theirs", null).orElseThrow();
        Optional<VaultStamp> late = vaults.writeContents(mine, "mine", base.stamp());

        assertThat(first.version()).isEqualTo(base.stamp().version() + 1);
        assertThat(again).contains(new VaultStamp(base.stamp().lineage(), first.version() + 1));
        assertThat(late).isEmpty();
        assertThat(stored(1).contents()).isEqualTo(VaultContents.of(new byte[] {2}));
        assertThat(vaults.lastWrite(vault(1))).contains(new VaultRepository.LastWrite(other, "theirs"));
    }

    @Test
    void theLastWriterOfARowIsReadWithItsStamp() {
        assertThat(vaults.lastWrite(vault(1))).isEmpty();
        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW));
        Vault created = stored(1);

        assertThat(vaults.lastWrite(vault(1))).contains(new VaultRepository.LastWrite(created.stamp(), null));
        VaultStamp kept = vaults.save(
                created.withContents(ALEX, VaultContents.of(new byte[] {3}), NOW)
                        .result(),
                "staff");
        assertThat(vaults.lastWrite(vault(1))).contains(new VaultRepository.LastWrite(kept, "staff"));
        assertThat(vaults.update(created.renamedTo("loot"), Columns.PRESENTATION))
                .isTrue();
        assertThat(vaults.lastWrite(vault(1))).contains(new VaultRepository.LastWrite(kept, "staff"));
    }
}
