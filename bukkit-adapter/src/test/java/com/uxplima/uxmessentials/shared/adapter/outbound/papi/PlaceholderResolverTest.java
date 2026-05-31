package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The placeholder resolution logic, exercised behind the {@link PlaceholderResolver} seam against fakes of
 * the context read seams — no live PlaceholderAPI and no Bukkit. It proves each placeholder maps to the
 * right read, that an unknown key resolves to {@code empty} (the raw-token signal), that a disabled context
 * degrades its placeholders to the empty/"-" default, and that the offline guard suppresses the
 * session-only presence placeholders.
 */
class PlaceholderResolverTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .format("#,##0.00")
            .build();

    @Test
    void unknownKeyResolvesEmptySoTheRawTokenStays() {
        PlaceholderResolver resolver =
                new PlaceholderResolver(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "not_a_placeholder")).isEmpty();
    }

    @Test
    void homesPlaceholdersReadCountLimitAndRemaining() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(2, 5)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "homes_limit")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "homes_left")).contains("3");
    }

    @Test
    void unlimitedHomeLimitRendersTheInfinityMarker() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(7, -1)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_limit")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "homes_left")).contains("∞");
    }

    @Test
    void economyPlaceholdersReadBalanceFormattedAndPosition() {
        EconomyPlaceholders economy = new EconomyPlaceholders() {
            @Override
            public Money balance(PlayerRef who) {
                return Money.of(COINS, new BigDecimal("1234.5"));
            }

            @Override
            public String formatted(PlayerRef who) {
                return "$1.23K";
            }

            @Override
            public OptionalInt baltopPosition(PlayerRef who) {
                return OptionalInt.of(4);
            }
        };
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "balance")).contains("1234.50");
        assertThat(resolver.resolve(ALICE, true, "balance_formatted")).contains("$1.23K");
        assertThat(resolver.resolve(ALICE, true, "baltop_position")).contains("4");
    }

    @Test
    void unrankedBaltopPositionDegradesToDash() {
        EconomyPlaceholders economy = new EconomyPlaceholders() {
            @Override
            public Money balance(PlayerRef who) {
                return Money.zero(COINS);
            }

            @Override
            public String formatted(PlayerRef who) {
                return "$0.00";
            }

            @Override
            public OptionalInt baltopPosition(PlayerRef who) {
                return OptionalInt.empty();
            }
        };
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "baltop_position")).contains("-");
    }

    @Test
    void presencePlaceholdersReadAfkDurationAndVanish() {
        PresencePlaceholders presence =
                who -> Optional.of(new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(90), true));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "afk")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "afk_duration")).contains("1m30s");
        assertThat(resolver.resolve(ALICE, true, "vanished")).contains("yes");
    }

    @Test
    void presencePlaceholdersDegradeWhenOffline() {
        PresencePlaceholders presence =
                who -> Optional.of(new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(90), true));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, false, "afk")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "afk_duration")).contains("-");
    }

    @Test
    void afkDurationIsDashWhenNotAfk() {
        PresencePlaceholders presence =
                who -> Optional.of(new PresencePlaceholders.Snapshot(false, Duration.ZERO, false));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "afk")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "afk_duration")).contains("-");
    }

    @Test
    void kitCooldownReadsRemainingAndIsDashForUnknownKit() {
        KitsPlaceholders kits =
                (who, kitId) -> kitId.equals("daily") ? Optional.of(Duration.ofSeconds(3_661)) : Optional.empty();
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("1h1m1s");
        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_ghost")).contains("-");
    }

    @Test
    void readyKitRendersZeroSeconds() {
        KitsPlaceholders kits = (who, kitId) -> Optional.of(Duration.ZERO);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("0s");
    }

    @Test
    void vaultsCountReadsThroughTheSeam() {
        VaultsPlaceholders vaults = who -> 6;
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().vaults(vaults).build());

        assertThat(resolver.resolve(ALICE, true, "vaults_count")).contains("6");
    }

    @Test
    void moderationPlaceholdersReadMutedAndJailed() {
        ModerationPlaceholders moderation = new ModerationPlaceholders() {
            @Override
            public boolean isMuted(PlayerRef who) {
                return true;
            }

            @Override
            public boolean isJailed(PlayerRef who) {
                return false;
            }
        };
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "muted")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "jailed")).contains("no");
    }

    @Test
    void disabledContextsDegradeToTheirEmptyDefault() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "homes_count")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "balance")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "afk")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "vaults_count")).contains("-");
        // A disabled moderation module means "no one is sanctioned", not the dash.
        assertThat(resolver.resolve(ALICE, true, "muted")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "jailed")).contains("no");
    }

    private static PlaceholderResolver resolverWith(PlaceholderContexts contexts) {
        return new PlaceholderResolver(contexts);
    }

    private static HomesPlaceholders fakeHomes(int count, int limit) {
        return new HomesPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return count;
            }

            @Override
            public int limit(PlayerRef who) {
                return limit;
            }
        };
    }
}
