package com.uxplima.uxmessentials.migration;

import static com.uxplima.uxmessentials.migration.EssentialsXFixture.ALEX;
import static com.uxplima.uxmessentials.migration.EssentialsXFixture.STEVE;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.migration.convert.map.ImportedMail;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class UserRecordWriterTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef STEVE_REF = new PlayerRef(STEVE, "Steve");
    private static final ImportOptions SKIP = ImportOptions.live(Path.of("Essentials"));
    private static final KitId WELCOME = KitId.of("welcome");

    /** The rule the plugin's text renderer applies to typed text: every {@code <} and backslash is escaped. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    private final InMemoryHomes homes = new InMemoryHomes();
    private final InMemoryWallets wallets = new InMemoryWallets();
    private final InMemoryMail mail = new InMemoryMail();
    private final InMemoryKitClaims kitClaims = new InMemoryKitClaims();

    private UserRecordWriter writer(Optional<MailRepository> mailStore) {
        return writer(mailStore, Duration.ZERO);
    }

    private UserRecordWriter writer(Optional<MailRepository> mailStore, Duration retention) {
        return new UserRecordWriter(
                homes,
                wallets,
                mailStore,
                kitClaims,
                retention,
                UserRecordWriterTest::escape,
                COINS,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UserRecordWriter writer() {
        return writer(Optional.of(mail));
    }

    @Test
    void theFixtureMailLandsEscapedWithItsSenderTimeAndReadFlagAndARerunAddsNothing() {
        ImportedUser steve = EssentialsXFixture.steve();

        RecordResult first = writer().write(steve, SKIP);
        RecordResult second = writer().write(steve, SKIP);

        assertThat(mail.all())
                .containsExactly(
                        stored(
                                1,
                                new MailSender(Optional.of(ALEX), "Alex"),
                                "Meet me at \\<spawn> \\\\o/",
                                at(1_700_000_000_000L),
                                true),
                        stored(
                                2,
                                new MailSender(Optional.empty(), "Console"),
                                "Server restarts tonight",
                                at(1_700_000_600_000L),
                                false),
                        stored(3, new MailSender(Optional.empty(), "Notch"), "old style mail", NOW, false),
                        stored(4, new MailSender(Optional.empty(), "Jeb_"), "plain string mail", NOW, false),
                        stored(
                                5,
                                new MailSender(Optional.empty(), "EssentialsX"),
                                "just words without a sender",
                                NOW,
                                false));
        assertThat(first.outcome()).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(first.notes())
                .containsExactly(
                        "homes_written=1",
                        "homes_kept=0",
                        "balance=written",
                        "mail_written=5",
                        "mail_kept=0",
                        "mail_expired=1");
        assertThat(second.outcome()).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(second.notes())
                .containsExactly(
                        "homes_written=0",
                        "homes_kept=1",
                        "balance=kept",
                        "mail_written=0",
                        "mail_kept=5",
                        "mail_expired=1");
    }

    @Test
    void aHomeKeptUnderSkipMakesTheRecordPartialAndLeavesTheExistingHome() {
        Home existing = home(STEVE_REF, 100);
        homes.save(existing);
        ImportedUser user = new ImportedUser(
                STEVE_REF, List.of(home(STEVE_REF, 5)), Optional.empty(), List.of(mailFrom("Alex", "hi")));

        RecordResult result = writer().write(user, SKIP);

        assertThat(result.outcome()).isEqualTo(RecordOutcome.PARTIAL);
        assertThat(homes.all()).containsExactly(existing);
        assertThat(mail.all()).hasSize(1);
    }

    @Test
    void nothingToWriteIsReportedAsSkippedRatherThanWritten() {
        homes.save(home(STEVE_REF, 100));
        ImportedUser user = new ImportedUser(STEVE_REF, List.of(home(STEVE_REF, 5)), Optional.empty(), List.of());

        assertThat(writer().write(user, SKIP).outcome()).isEqualTo(RecordOutcome.SKIPPED);
    }

    @Test
    void withTheMessagingModuleOffMailIsSkippedAndTheAuditSaysWhy() {
        ImportedUser steve = EssentialsXFixture.steve();
        ImportedUser onlyMail = new ImportedUser(STEVE_REF, List.of(), Optional.empty(), steve.mail());

        RecordResult full = writer(Optional.empty()).write(steve, SKIP);
        RecordResult mailOnly = writer(Optional.empty()).write(onlyMail, SKIP);

        assertThat(full.outcome()).isEqualTo(RecordOutcome.PARTIAL);
        assertThat(full.notes()).contains("homes_written=1", "balance=written", "mail_skipped=messaging_disabled");
        assertThat(homes.all()).hasSize(1);
        assertThat(wallets.balance(STEVE_REF, COINS)).contains(Money.of(COINS, new BigDecimal("1250.5")));
        assertThat(mailOnly.outcome()).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(mailOnly.notes()).contains("mail_skipped=messaging_disabled");
    }

    @Test
    void twoIdenticalUndatedMessagesLandTwiceOnTheFirstRunAndNeverAgain() {
        ImportedUser user = new ImportedUser(
                STEVE_REF, List.of(), Optional.empty(), List.of(mailFrom("Alex", "same"), mailFrom("Alex", "same")));

        writer().write(user, SKIP);
        RecordResult rerun = writer().write(user, SKIP);

        assertThat(mail.all()).hasSize(2);
        assertThat(rerun.outcome()).isEqualTo(RecordOutcome.SKIPPED);
    }

    @Test
    void aDryRunReportsWhatALiveRunWouldAndWritesNothing() {
        ImportedUser steve = EssentialsXFixture.steve();

        RecordResult preview = writer().preview(steve, SKIP);

        assertThat(preview)
                .isEqualTo(new UserRecordWriter(
                                new InMemoryHomes(),
                                new InMemoryWallets(),
                                Optional.of(new InMemoryMail()),
                                new InMemoryKitClaims(),
                                Duration.ZERO,
                                UserRecordWriterTest::escape,
                                COINS,
                                Clock.fixed(NOW, ZoneOffset.UTC))
                        .write(steve, SKIP));
        assertThat(homes.all()).isEmpty();
        assertThat(wallets.findByOwner(STEVE_REF)).isEmpty();
        assertThat(mail.all()).isEmpty();
    }

    @Test
    void textThatOnlyFitsTheColumnBeforeEscapingIsShortenedAfterIt() {
        ImportedUser user =
                new ImportedUser(STEVE_REF, List.of(), Optional.empty(), List.of(mailFrom("Alex", "<".repeat(300))));

        writer().write(user, SKIP);

        assertThat(mail.all())
                .singleElement()
                .extracting(item -> item.body().value())
                .isEqualTo("\\<".repeat(128));
    }

    @Test
    void mailTheSweepWouldDeleteIsHeldBackSoItIsNotImportedAgainOnEveryRun() {
        ImportedUser steve = EssentialsXFixture.steve();

        RecordResult first = writer(Optional.of(mail), Duration.ofDays(30)).write(steve, SKIP);
        RecordResult second = writer(Optional.of(mail), Duration.ofDays(30)).write(steve, SKIP);

        assertThat(mail.all()).extracting(item -> item.sender().name()).containsExactly("Notch", "Jeb_", "EssentialsX");
        assertThat(first.outcome()).isEqualTo(RecordOutcome.PARTIAL);
        assertThat(first.notes()).contains("mail_written=3", "mail_past_retention=2");
        assertThat(second.outcome()).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(second.notes()).contains("mail_kept=3", "mail_past_retention=2");
    }

    @Test
    void aSenderNameIsCutBeforeEscapingSoNoEscapeIsLeftHalfDone() {
        ImportedUser user =
                new ImportedUser(STEVE_REF, List.of(), Optional.empty(), List.of(mailFrom("abcdefghijklmno<", "hi")));

        writer().write(user, SKIP);

        assertThat(mail.all())
                .singleElement()
                .extracting(item -> item.sender().name())
                .isEqualTo("abcdefghijklmno");
    }

    @Test
    void cuttingLongTextToTheColumnNeverSplitsACharacterInTwo() {
        String emoji = new String(Character.toChars(0x1F600));
        String text = "a".repeat(MessageBody.MAX_LENGTH - 1) + emoji + "tail";
        ImportedUser user = new ImportedUser(STEVE_REF, List.of(), Optional.empty(), List.of(mailFrom("Alex", text)));

        writer().write(user, SKIP);

        assertThat(mail.all())
                .singleElement()
                .extracting(item -> item.body().value())
                .isEqualTo("a".repeat(MessageBody.MAX_LENGTH - 1));
    }

    @Test
    void underSkipIfPresentAWalletThatHoldsMoneyKeepsItsBalance() {
        wallets.upsertBalance(STEVE_REF, Money.of(COINS, 500));

        RecordResult result = writer().write(onlyBalance("1250.5"), SKIP);

        assertThat(wallets.balance(STEVE_REF, COINS)).contains(Money.of(COINS, 500));
        assertThat(result.outcome()).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(result.notes()).contains("balance=kept");
    }

    @Test
    void aWalletAtZeroHoldsNoBalanceSoTheImportedOneLandsUnderSkipIfPresent() {
        wallets.upsertBalance(STEVE_REF, Money.of(COINS, 0));

        RecordResult result = writer().write(onlyBalance("1250.5"), SKIP);

        assertThat(wallets.balance(STEVE_REF, COINS)).contains(Money.of(COINS, new BigDecimal("1250.5")));
        assertThat(result.outcome()).isEqualTo(RecordOutcome.WRITTEN);
    }

    @Test
    void underOverwriteTheBalanceIsSetToTheSourceAndARerunNeverAddsToIt() {
        wallets.upsertBalance(STEVE_REF, Money.of(COINS, 500));
        ImportOptions overwrite =
                new ImportOptions(Path.of("Essentials"), ImportMode.LIVE, ConflictPolicy.SKIP, BalancePolicy.OVERWRITE);

        RecordResult first = writer().write(onlyBalance("1250.5"), overwrite);
        RecordResult second = writer().write(onlyBalance("1250.5"), overwrite);

        assertThat(wallets.balance(STEVE_REF, COINS)).contains(Money.of(COINS, new BigDecimal("1250.5")));
        assertThat(first.outcome()).isEqualTo(RecordOutcome.OVERWRITTEN);
        assertThat(second.outcome()).isEqualTo(RecordOutcome.OVERWRITTEN);
    }

    @Test
    void theBalanceKnobIsSeparateSoOverwritingRecordsStillKeepsAnExistingBalance() {
        homes.save(home(STEVE_REF, 100));
        wallets.upsertBalance(STEVE_REF, Money.of(COINS, 500));
        ImportOptions overwriteRecords = new ImportOptions(
                Path.of("Essentials"), ImportMode.LIVE, ConflictPolicy.OVERWRITE, BalancePolicy.SKIP_IF_PRESENT);
        ImportedUser user = new ImportedUser(
                STEVE_REF, List.of(home(STEVE_REF, 5)), Optional.of(new BigDecimal("1250.5")), List.of());

        RecordResult result = writer().write(user, overwriteRecords);

        assertThat(homes.all()).containsExactly(home(STEVE_REF, 5));
        assertThat(wallets.balance(STEVE_REF, COINS)).contains(Money.of(COINS, 500));
        assertThat(result.outcome()).isEqualTo(RecordOutcome.PARTIAL);
        assertThat(result.notes()).contains("homes_written=1", "balance=kept");
    }

    @Test
    void underOverwriteAnOccupiedHomeSlotTakesTheImportedHome() {
        homes.save(home(STEVE_REF, 100));
        ImportOptions overwrite = new ImportOptions(
                Path.of("Essentials"), ImportMode.LIVE, ConflictPolicy.OVERWRITE, BalancePolicy.SKIP_IF_PRESENT);
        ImportedUser user = new ImportedUser(STEVE_REF, List.of(home(STEVE_REF, 5)), Optional.empty(), List.of());

        RecordResult result = writer().write(user, overwrite);

        assertThat(homes.all()).containsExactly(home(STEVE_REF, 5));
        assertThat(result.outcome()).isEqualTo(RecordOutcome.OVERWRITTEN);
    }

    @Test
    void aOneTimeKitClaimLandsWithItsTimeAndARerunKeepsIt() {
        ImportedUser user = withKitClaim(WELCOME, at(1_700_000_100_000L));

        RecordResult first = writer().write(user, SKIP);
        RecordResult second = writer().write(user, SKIP);

        assertThat(kitClaims.of(STEVE_REF)).containsExactly(Map.entry(WELCOME, at(1_700_000_100_000L)));
        assertThat(first.outcome()).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(first.notes()).contains("kit_claims_written=1", "kit_claims_kept=0");
        assertThat(second.outcome()).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(second.notes()).contains("kit_claims_written=0", "kit_claims_kept=1");
    }

    @Test
    void aClaimAlreadyRecordedHereStandsUnderEveryPolicyAndIsNotHeldBack() {
        kitClaims.record(STEVE_REF, WELCOME, at(1_800_000_000_000L));
        ImportOptions overwrite = new ImportOptions(
                Path.of("Essentials"), ImportMode.LIVE, ConflictPolicy.OVERWRITE, BalancePolicy.OVERWRITE);

        RecordResult result = writer().write(withKitClaim(WELCOME, at(1_700_000_100_000L)), overwrite);

        assertThat(kitClaims.of(STEVE_REF)).containsExactly(Map.entry(WELCOME, at(1_800_000_000_000L)));
        assertThat(result.outcome()).isEqualTo(RecordOutcome.SKIPPED);
    }

    @Test
    void aDryRunCountsTheKitClaimsALiveRunWouldRecordAndRecordsNone() {
        kitClaims.record(STEVE_REF, KitId.of("starter"), at(1_700_000_000_000L));
        ImportedUser user = new ImportedUser(
                STEVE_REF,
                List.of(),
                Optional.empty(),
                List.of(),
                Map.of(WELCOME, at(1_700_000_100_000L), KitId.of("starter"), at(1_700_000_000_000L)));

        RecordResult preview = writer().preview(user, SKIP);

        assertThat(preview.notes()).contains("kit_claims_written=1", "kit_claims_kept=1");
        assertThat(preview.outcome()).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(kitClaims.of(STEVE_REF)).containsOnlyKeys(KitId.of("starter"));
    }

    private static ImportedUser withKitClaim(KitId kit, Instant claimedAt) {
        return new ImportedUser(STEVE_REF, List.of(), Optional.empty(), List.of(), Map.of(kit, claimedAt));
    }

    private static ImportedUser onlyBalance(String amount) {
        return new ImportedUser(STEVE_REF, List.of(), Optional.of(new BigDecimal(amount)), List.of());
    }

    private static ImportedMail mailFrom(String sender, String text) {
        return new ImportedMail(Optional.empty(), sender, text, Optional.empty(), Optional.empty(), false);
    }

    private static Home home(PlayerRef owner, double x) {
        WorldRef world = new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000a001"), "world");
        return Home.create(owner, HomeSlot.of(0), new Position(world, x, 64, 0, 0f, 0f), Instant.EPOCH);
    }

    private static Instant at(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis);
    }

    private static MailItem stored(long id, MailSender sender, String body, Instant sentAt, boolean read) {
        return new MailItem(MailId.of(id), STEVE_REF, sender, new MessageBody(body), sentAt, read);
    }
}
