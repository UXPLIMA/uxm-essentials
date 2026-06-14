package com.uxplima.uxmessentials.migration.convert.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LiveBalanceConvert}: it surfaces only {@code balance}, detects on the feed's
 * availability rather than any on-disk tree, and turns the feed's balance-only users into
 * {@link ImportRecord.UserRecord}s without dropping or reshaping them.
 */
class LiveBalanceConvertTest {

    private static final ImportOptions OPTIONS =
            new ImportOptions(Path.of("."), ImportMode.LIVE, ConflictPolicy.SKIP, BalancePolicy.SKIP_IF_PRESENT);

    private static ImportedUser balanceUser(String name, String amount) {
        PlayerRef owner = new PlayerRef(UUID.randomUUID(), name);
        return new ImportedUser(owner, List.of(), Optional.of(new BigDecimal(amount)), List.of());
    }

    private static LiveBalanceConvert convertOver(BalanceFeed feed) {
        return new LiveBalanceConvert(SourceId.of("vault"), "Vault", "the live Vault economy provider", feed);
    }

    @Test
    void planStreamsEveryFeedUserAsAUserRecord() {
        ImportedUser alice = balanceUser("Alice", "100");
        ImportedUser bob = balanceUser("Bob", "250");
        LiveBalanceConvert convert = convertOver(new FakeFeed(true, List.of(alice, bob)));

        try (ImportPlan plan = convert.plan(OPTIONS)) {
            List<ImportRecord> records = plan.records().toList();
            assertThat(records).hasSize(2).allMatch(r -> r instanceof ImportRecord.UserRecord);
            assertThat(records).map(r -> ((ImportRecord.UserRecord) r).user()).containsExactly(alice, bob);
            assertThat(records)
                    .map(r -> ((ImportRecord.UserRecord) r).user().balance().orElseThrow())
                    .containsExactly(new BigDecimal("100"), new BigDecimal("250"));
        }
    }

    @Test
    void idAndSurfacesDescribeABalanceOnlySource() {
        LiveBalanceConvert convert = convertOver(new FakeFeed(true, List.of()));
        assertThat(convert.id()).isEqualTo(SourceId.of("vault"));
        assertThat(convert.describe().surfaces()).isEqualTo(List.of("balance"));
    }

    @Test
    void detectMirrorsTheFeedAvailability() {
        assertThat(convertOver(new FakeFeed(true, List.of())).detect(Path.of(".")))
                .isTrue();
        assertThat(convertOver(new FakeFeed(false, List.of())).detect(Path.of(".")))
                .isFalse();
    }

    private static final class FakeFeed implements BalanceFeed {

        private final boolean available;
        private final List<ImportedUser> users;

        private FakeFeed(boolean available, List<ImportedUser> users) {
            this.available = available;
            this.users = users;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Stream<ImportedUser> users() {
            return users.stream();
        }
    }
}
