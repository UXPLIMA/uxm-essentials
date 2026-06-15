package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
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
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .format("#,##0.00")
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems"))
            .symbol("g")
            .plural("gems")
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
        FakeEconomy economy =
                new FakeEconomy().balance(ALICE, "1234.5").formatted("$1.23K").position(4);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "balance")).contains("1234.50");
        assertThat(resolver.resolve(ALICE, true, "balance_formatted")).contains("$1.23K");
        assertThat(resolver.resolve(ALICE, true, "baltop_position")).contains("4");
        // The economy_-prefixed aliases resolve the same scalars.
        assertThat(resolver.resolve(ALICE, true, "economy_balance")).contains("1234.50");
        assertThat(resolver.resolve(ALICE, true, "economy_balance_formatted")).contains("$1.23K");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_position")).contains("4");
    }

    @Test
    void unrankedBaltopPositionDegradesToDash() {
        FakeEconomy economy = new FakeEconomy().position(-1);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "baltop_position")).contains("-");
    }

    @Test
    void economyCompactAndShortRenderTheAbbreviatedBalance() {
        FakeEconomy economy = new FakeEconomy().balance(ALICE, "1234500").compactValue("$1.23M");
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_balance_compact")).contains("$1.23M");
        assertThat(resolver.resolve(ALICE, true, "economy_balance_short")).contains("$1.23M");
    }

    @Test
    void economyCurrencyNameAndSymbolReadTheDefaultCurrency() {
        FakeEconomy economy = new FakeEconomy().currency(COINS);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_currency_name")).contains("coins");
        assertThat(resolver.resolve(ALICE, true, "economy_currency_symbol")).contains("$");
    }

    @Test
    void perCurrencyBalanceResolvesTheNamedCurrencyAndDashesTheUnknown() {
        FakeEconomy economy = new FakeEconomy().currency(GEMS).balance(ALICE, GEMS, "50");
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_balance_gems")).contains("50.00");
        assertThat(resolver.resolve(ALICE, true, "economy_balance_formatted_gems"))
                .contains("g50.00");
        assertThat(resolver.resolve(ALICE, true, "economy_balance_doubloons")).contains("-");
    }

    @Test
    void indexedBaltopReadsRowFieldsAndDashesOutOfRange() {
        FakeEconomy economy = new FakeEconomy()
                .currency(COINS)
                .baltopRow(COINS, 1, new BaltopRow(BOB, Money.of(COINS, new BigDecimal("9000"))));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_baltop_1_name")).contains("Bob");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_1_amount")).contains("9000.00");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_1_formatted")).contains("$9,000.00");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_1_uuid"))
                .contains(BOB.uuid().toString());
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_2_name")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_x_name")).contains("-");
    }

    @Test
    void perCurrencyIndexedBaltopReadsTheNamedCurrencyLeaderboard() {
        FakeEconomy economy = new FakeEconomy()
                .currency(GEMS)
                .baltopRow(GEMS, 1, new BaltopRow(BOB, Money.of(GEMS, new BigDecimal("12"))));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_baltop_gems_1_name")).contains("Bob");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_gems_1_amount"))
                .contains("12.00");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_doubloons_1_name"))
                .contains("-");
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
    void votePlaceholdersReadPeriodicTotals() {
        VotePlaceholders vote = new VotePlaceholders() {
            @Override
            public long countFor(PlayerRef who, VotePeriod period) {
                return switch (period) {
                    case ALLTIME -> 100L;
                    case DAILY -> 3L;
                    case WEEKLY -> 14L;
                    case MONTHLY -> 42L;
                };
            }

            @Override
            public int partyCount() {
                return 18;
            }

            @Override
            public int partyThreshold() {
                return 25;
            }
        };
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().vote(vote).build());

        assertThat(resolver.resolve(ALICE, true, "votes_alltime")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "votes_daily")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "votes_weekly")).contains("14");
        assertThat(resolver.resolve(ALICE, true, "votes_monthly")).contains("42");
        assertThat(resolver.resolve(ALICE, true, "voteparty_current")).contains("18");
        assertThat(resolver.resolve(ALICE, true, "voteparty_required")).contains("25");
        assertThat(resolver.resolve(ALICE, true, "voteparty_remaining")).contains("7");
    }

    @Test
    void votePlaceholdersDegradeWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "votes_alltime")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "votes_monthly")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "voteparty_current")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "voteparty_remaining")).contains("-");
    }

    @Test
    void unknownVotesPeriodDegradesToDash() {
        VotePlaceholders vote = new VotePlaceholders() {
            @Override
            public long countFor(PlayerRef who, VotePeriod period) {
                return 5L;
            }

            @Override
            public int partyCount() {
                return 0;
            }

            @Override
            public int partyThreshold() {
                return 25;
            }
        };
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().vote(vote).build());

        assertThat(resolver.resolve(ALICE, true, "votes_unknown")).contains("-");
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

    /** A configurable {@link EconomyPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeEconomy implements EconomyPlaceholders {

        private final java.util.Map<PlayerRef, Money> defaultBalances = new java.util.HashMap<>();
        private final java.util.Map<String, Money> currencyBalances = new java.util.HashMap<>();
        private final java.util.Map<String, Currency> currencies = new java.util.HashMap<>();
        private final java.util.Map<String, BaltopRow> rows = new java.util.HashMap<>();
        private Currency defaultCurrency = COINS;
        private String formatted = "$0.00";
        private String compact = "$0.00";
        private int position = -1;

        FakeEconomy balance(PlayerRef who, String amount) {
            defaultBalances.put(who, Money.of(COINS, new BigDecimal(amount)));
            return this;
        }

        FakeEconomy balance(PlayerRef who, Currency currency, String amount) {
            currencyBalances.put(key(who, currency), Money.of(currency, new BigDecimal(amount)));
            return this;
        }

        FakeEconomy currency(Currency currency) {
            currencies.put(currency.id().value(), currency);
            this.defaultCurrency = currency;
            return this;
        }

        FakeEconomy formatted(String value) {
            this.formatted = value;
            return this;
        }

        FakeEconomy compactValue(String value) {
            this.compact = value;
            return this;
        }

        FakeEconomy position(int value) {
            this.position = value;
            return this;
        }

        FakeEconomy baltopRow(Currency currency, int rank, BaltopRow row) {
            rows.put(currency.id().value() + "#" + rank, row);
            return this;
        }

        @Override
        public Money balance(PlayerRef who) {
            return defaultBalances.getOrDefault(who, Money.zero(COINS));
        }

        @Override
        public String formatted(PlayerRef who) {
            return formatted;
        }

        @Override
        public String compact(PlayerRef who) {
            return compact;
        }

        @Override
        public OptionalInt baltopPosition(PlayerRef who) {
            return position >= 1 ? OptionalInt.of(position) : OptionalInt.empty();
        }

        @Override
        public Optional<Currency> currency(String currencyId) {
            return Optional.ofNullable(currencies.get(currencyId.toLowerCase(java.util.Locale.ROOT)));
        }

        @Override
        public Currency defaultCurrency() {
            return defaultCurrency;
        }

        @Override
        public Money balance(PlayerRef who, Currency currency) {
            return currencyBalances.getOrDefault(key(who, currency), Money.zero(currency));
        }

        @Override
        public Optional<BaltopRow> baltopRow(Currency currency, int rank) {
            return Optional.ofNullable(rows.get(currency.id().value() + "#" + rank));
        }

        private static String key(PlayerRef who, Currency currency) {
            return who.uuid() + "#" + currency.id().value();
        }
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
