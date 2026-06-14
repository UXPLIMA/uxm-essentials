package com.uxplima.uxmessentials.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.SourceRegistry;
import com.uxplima.uxmessentials.migration.convert.essentialsx.EssentialsXConvert;
import com.uxplima.uxmessentials.migration.convert.live.BalanceFeed;
import com.uxplima.uxmessentials.migration.convert.live.LiveBalanceConvert;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import org.junit.jupiter.api.Test;

/**
 * The source-registry drift guard (docs/12-migration §8.3). It keeps the multi-source machinery honest:
 * the built sources are EssentialsX plus the live economy sources Vault and PlayerPoints; the six planned
 * sources have a reserved id but no registry entry (planned ≠ stubbed, §1.2). The registry keys, the
 * per-source mapping tables, and the built-source roster stay in lock-step — a {@code Convert} impl with
 * no mapping table, or a planned id that tab-completes, fails here.
 */
class MigrationSourceRegistryDriftTest {

    /** The reserved ids of the planned sources — documented in §1.2, deliberately not built. */
    private static final List<String> PLANNED =
            List.of("cmi", "huskhomes", "playervaultx", "coinsengine", "sunlight", "axvault");

    private final SourceRegistry registry = new SourceRegistry(List.of(
            new EssentialsXConvert(name -> Optional.empty()),
            new LiveBalanceConvert(SourceId.of("vault"), "Vault", "the live Vault economy provider", emptyFeed()),
            new LiveBalanceConvert(
                    SourceId.of("playerpoints"),
                    "PlayerPoints",
                    "the live PlayerPoints economy provider",
                    emptyFeed())));

    private static BalanceFeed emptyFeed() {
        return new BalanceFeed() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public Stream<ImportedUser> users() {
                return Stream.empty();
            }
        };
    }

    @Test
    void theBuiltSourcesAreEssentialsxVaultAndPlayerpointsAndResolve() {
        assertThat(registry.builtIds())
                .containsExactlyInAnyOrder(
                        SourceId.of("essentialsx"), SourceId.of("vault"), SourceId.of("playerpoints"));
        assertThat(registry.resolve(SourceId.of("essentialsx")))
                .map(Convert::id)
                .contains(SourceId.of("essentialsx"));
    }

    @Test
    void everyBuiltSourceHasAMappingTable() {
        for (SourceId id : registry.builtIds()) {
            assertThat(SupportedMappings.rows(id))
                    .as("built source %s must contribute a SupportedMappings table", id)
                    .isNotEmpty();
        }
        assertThat(SupportedMappings.builtSources()).containsExactlyInAnyOrderElementsOf(registry.builtIds());
    }

    @Test
    void plannedSourcesNeverResolveAndContributeNoRows() {
        for (String planned : PLANNED) {
            SourceId id = SourceId.of(planned);
            assertThat(registry.resolve(id))
                    .as("planned source %s must not resolve (planned != stubbed)", planned)
                    .isEmpty();
            assertThat(SupportedMappings.rows(id))
                    .as("planned source %s must contribute no mapping rows", planned)
                    .isEmpty();
            assertThat(registry.builtIds())
                    .as("planned source %s must not tab-complete", planned)
                    .doesNotContain(id);
        }
    }

    @Test
    void anUnknownSourceResolvesToEmptyNotASilentNoOp() {
        assertThat(registry.resolve(SourceId.of("nonsense"))).isEmpty();
    }

    @Test
    void registryRejectsDuplicateSources() {
        EssentialsXConvert one = new EssentialsXConvert(name -> Optional.empty());
        EssentialsXConvert two = new EssentialsXConvert(name -> Optional.empty());
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new SourceRegistry(List.of(one, two)));
    }
}
