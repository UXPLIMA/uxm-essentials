package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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
    void homesListJoinsNamesAndDashesWhenEmpty() {
        List<HomesPlaceholders.HomeView> homes = List.of(
                new HomesPlaceholders.HomeView("base", "world", 10, 64, -20),
                new HomesPlaceholders.HomeView("mine", "world_nether", 1, 30, 2));
        PlaceholderResolver withHomes = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(2, 5, homes)).build());
        PlaceholderResolver noHomes = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(0, 5, List.of())).build());

        assertThat(withHomes.resolve(ALICE, true, "homes_list")).contains("base, mine");
        assertThat(noHomes.resolve(ALICE, true, "homes_list")).contains("-");
    }

    @Test
    void indexedHomeReadsNameWorldAndCoordinates() {
        List<HomesPlaceholders.HomeView> homes = List.of(
                new HomesPlaceholders.HomeView("base", "world", 10, 64, -20),
                new HomesPlaceholders.HomeView("mine", "world_nether", 1, 30, 2));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(2, 5, homes)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_1")).contains("base");
        assertThat(resolver.resolve(ALICE, true, "homes_1_world")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "homes_1_x")).contains("10");
        assertThat(resolver.resolve(ALICE, true, "homes_1_y")).contains("64");
        assertThat(resolver.resolve(ALICE, true, "homes_1_z")).contains("-20");
        assertThat(resolver.resolve(ALICE, true, "homes_2_world")).contains("world_nether");
    }

    @Test
    void indexedHomeDashesOutOfRangeAndUnparseable() {
        List<HomesPlaceholders.HomeView> homes = List.of(new HomesPlaceholders.HomeView("base", "world", 0, 0, 0));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(1, 5, homes)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_3")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "homes_0")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "homes_abc")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "homes_1_unknown")).contains("-");
    }

    @Test
    void homeExistsReportsYesOrNoByLabel() {
        List<HomesPlaceholders.HomeView> homes = List.of(new HomesPlaceholders.HomeView("Base", "world", 0, 0, 0));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(1, 5, homes)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_exists_base")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "homes_exists_mine")).contains("no");
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
        PresencePlaceholders presence = presenceSeam(
                new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(90), Optional.of("lunch"), true, "Ace"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "afk")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "afk_duration")).contains("1m30s");
        assertThat(resolver.resolve(ALICE, true, "vanished")).contains("yes");
    }

    @Test
    void presencePrefixReadsNicknameRealnameReasonAndStatus() {
        PresencePlaceholders presence = presenceSeam(
                new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(90), Optional.of("lunch"), true, "Ace"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "presence_nickname")).contains("Ace");
        assertThat(resolver.resolve(ALICE, true, "presence_realname")).contains("Alice");
        assertThat(resolver.resolve(ALICE, true, "presence_afk")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "presence_afk_since")).contains("1m30s");
        assertThat(resolver.resolve(ALICE, true, "presence_afk_reason")).contains("lunch");
        assertThat(resolver.resolve(ALICE, true, "presence_vanished")).contains("yes");
    }

    @Test
    void presenceAfkReasonIsDashWithoutAReason() {
        PresencePlaceholders presence = presenceSeam(
                new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(5), Optional.empty(), false, "Alice"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "presence_afk_reason")).contains("-");
    }

    @Test
    void presencePlaceholdersDegradeWhenOffline() {
        PresencePlaceholders presence = presenceSeam(
                new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(90), Optional.of("afk"), true, "Ace"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, false, "afk")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "afk_duration")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "presence_nickname")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "presence_realname")).contains("-");
    }

    @Test
    void afkDurationIsDashWhenNotAfk() {
        PresencePlaceholders presence =
                presenceSeam(new PresencePlaceholders.Snapshot(false, Duration.ZERO, Optional.empty(), false, "Alice"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "afk")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "afk_duration")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "presence_afk_reason")).contains("-");
    }

    @Test
    void kitCooldownReadsRemainingAndIsDashForUnknownKit() {
        FakeKits kits = new FakeKits().cooldown("daily", Duration.ofSeconds(3_661));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("1h1m1s");
        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily_formatted"))
                .contains("1h1m1s");
        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_ghost")).contains("-");
    }

    @Test
    void readyKitRendersZeroSeconds() {
        FakeKits kits = new FakeKits().cooldown("daily", Duration.ZERO);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("0s");
    }

    @Test
    void kitAvailableAndHasReadThroughTheSeam() {
        FakeKits kits = new FakeKits()
                .cooldown("daily", Duration.ZERO)
                .available("daily", true)
                .hasPermission("daily", true)
                .available("vip", false)
                .hasPermission("vip", false);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_available_daily")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "kit_has_daily")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "kit_available_vip")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "kit_has_vip")).contains("no");
        // An unknown kit degrades to the dash, never a misleading "no".
        assertThat(resolver.resolve(ALICE, true, "kit_available_ghost")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kit_has_ghost")).contains("-");
    }

    @Test
    void kitCostRendersFreeOrTheAmountAndDashesUnknown() {
        FakeKits kits = new FakeKits()
                .cooldown("daily", Duration.ZERO)
                .cost("daily", new BigDecimal("0"))
                .cooldown("crate", Duration.ZERO)
                .cost("crate", new BigDecimal("250"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_cost_daily")).contains("free");
        assertThat(resolver.resolve(ALICE, true, "kit_cost_crate")).contains("250");
        assertThat(resolver.resolve(ALICE, true, "kit_cost_ghost")).contains("-");
    }

    @Test
    void kitClaimsLeftRendersUnlimitedAndTheRemainingCount() {
        FakeKits kits = new FakeKits()
                .cooldown("daily", Duration.ZERO)
                .claimsLeft("daily", -1)
                .cooldown("starter", Duration.ZERO)
                .claimsLeft("starter", 1)
                .cooldown("welcome", Duration.ZERO)
                .claimsLeft("welcome", 0);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_claims_left_daily")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "kit_claims_left_starter")).contains("1");
        assertThat(resolver.resolve(ALICE, true, "kit_claims_left_welcome")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "kit_claims_left_ghost")).contains("-");
    }

    @Test
    void kitsListJoinsUsableIdsAndDashesWhenNone() {
        FakeKits withKits = new FakeKits().usable("daily", "starter");
        FakeKits noKits = new FakeKits();
        PlaceholderResolver withResolver =
                resolverWith(PlaceholderContexts.builder().kits(withKits).build());
        PlaceholderResolver noResolver =
                resolverWith(PlaceholderContexts.builder().kits(noKits).build());

        assertThat(withResolver.resolve(ALICE, true, "kits_list")).contains("daily, starter");
        assertThat(noResolver.resolve(ALICE, true, "kits_list")).contains("-");
    }

    @Test
    void kitFamilyDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kit_available_daily")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kit_cost_daily")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kits_list")).contains("-");
        // An unknown kit_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "kit_unknown")).contains("-");
    }

    @Test
    void warpsCountAndListReadThroughTheSeam() {
        FakeWarps warps = new FakeWarps()
                .visible("spawn", warpView("world", 0, 64, 0, 12, "Admin", "0"))
                .visible("shop", warpView("world", 100, 70, -50, 4, "Admin", "250"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().warps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "warps_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "warps_list")).contains("spawn, shop");
    }

    @Test
    void warpsListAndCountDashWhenNoneVisible() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().warps(new FakeWarps()).build());

        assertThat(resolver.resolve(ALICE, true, "warps_count")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "warps_list")).contains("-");
    }

    @Test
    void perWarpFieldsReadWorldCoordinatesVisitsOwnerAndCost() {
        FakeWarps warps = new FakeWarps().visible("shop", warpView("world_nether", 100, 70, -50, 4, "Admin", "250"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().warps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "warp_shop_world")).contains("world_nether");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_x")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_y")).contains("70");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_z")).contains("-50");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_visits")).contains("4");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_owner")).contains("Admin");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_cost")).contains("250");
    }

    @Test
    void perWarpFieldHandlesNamesWithUnderscores() {
        FakeWarps warps = new FakeWarps().visible("pvp_arena", warpView("world", 1, 2, 3, 0, "Admin", "0"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().warps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "warp_pvp_arena_world")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "warp_pvp_arena_x")).contains("1");
    }

    @Test
    void perWarpFieldDashesUnknownHiddenAndMalformed() {
        FakeWarps warps = new FakeWarps().visible("spawn", warpView("world", 0, 64, 0, 0, "Admin", "0"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().warps(warps).build());

        // A warp the player cannot see (not in the visible set) degrades to the dash, never leaking its data.
        assertThat(resolver.resolve(ALICE, true, "warp_secret_world")).contains("-");
        // An unknown field on a visible warp, and a tail with no field segment, both degrade to the dash.
        assertThat(resolver.resolve(ALICE, true, "warp_spawn_unknown")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "warp_spawn")).contains("-");
    }

    @Test
    void warpsDegradeWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "warps_count")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "warps_list")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "warp_spawn_world")).contains("-");
        // An unknown warps_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "warps_unknown")).contains("-");
    }

    @Test
    void vaultsCountMaxLeftAndSizeReadThroughTheSeam() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().vaults(fakeVaults(2, 5, 6)).build());

        assertThat(resolver.resolve(ALICE, true, "vaults_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "vaults_max")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "vaults_left")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "vaults_size")).contains("6");
    }

    @Test
    void unlimitedVaultMaxRendersTheInfinityMarker() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().vaults(fakeVaults(7, -1, 3)).build());

        assertThat(resolver.resolve(ALICE, true, "vaults_max")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "vaults_left")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "vaults_size")).contains("3");
    }

    @Test
    void unknownVaultsTailDegradesToDash() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().vaults(fakeVaults(1, 5, 6)).build());

        assertThat(resolver.resolve(ALICE, true, "vaults_unknown")).contains("-");
    }

    @Test
    void playerwarpsCountLimitLeftAndListReadThroughTheSeam() {
        FakePlayerwarps warps = new FakePlayerwarps(2, 5)
                .owned("base", playerWarpView("base", "Alice", "world", 10, 64, -20, 7))
                .owned("mine", playerWarpView("mine", "Alice", "world_nether", 1, 30, 2, 0));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerwarps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "playerwarps_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_limit")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_left")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_list")).contains("base, mine");
    }

    @Test
    void unlimitedPlayerwarpLimitRendersTheInfinityMarker() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .playerwarps(new FakePlayerwarps(7, -1))
                .build());

        assertThat(resolver.resolve(ALICE, true, "playerwarps_limit")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_left")).contains("∞");
    }

    @Test
    void playerwarpsListDashesWhenNoneOwned() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .playerwarps(new FakePlayerwarps(0, 5))
                .build());

        assertThat(resolver.resolve(ALICE, true, "playerwarps_count")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_list")).contains("-");
    }

    @Test
    void perPlayerwarpFieldsReadOwnerWorldCoordinatesAndVisits() {
        FakePlayerwarps warps = new FakePlayerwarps(1, 5)
                .owned("shop", playerWarpView("shop", "Alice", "world_nether", 100, 70, -50, 4));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerwarps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_owner")).contains("Alice");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_world")).contains("world_nether");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_x")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_y")).contains("70");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_z")).contains("-50");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_visits")).contains("4");
    }

    @Test
    void perPlayerwarpFieldHandlesNamesWithUnderscores() {
        FakePlayerwarps warps =
                new FakePlayerwarps(1, 5).owned("pvp_arena", playerWarpView("pvp_arena", "Alice", "world", 1, 2, 3, 0));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerwarps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "playerwarp_pvp_arena_world")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_pvp_arena_x")).contains("1");
    }

    @Test
    void perPlayerwarpFieldDashesUnknownAndMalformed() {
        FakePlayerwarps warps =
                new FakePlayerwarps(1, 5).owned("base", playerWarpView("base", "Alice", "world", 0, 64, 0, 0));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerwarps(warps).build());

        // A warp the player does not own degrades to the dash, never leaking another owner's data.
        assertThat(resolver.resolve(ALICE, true, "playerwarp_secret_world")).contains("-");
        // An unknown field on an owned warp, and a tail with no field segment, both degrade to the dash.
        assertThat(resolver.resolve(ALICE, true, "playerwarp_base_unknown")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_base")).contains("-");
    }

    @Test
    void playerwarpsDegradeWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "playerwarps_count")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_list")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_base_world")).contains("-");
        // An unknown playerwarps_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "playerwarps_unknown")).contains("-");
    }

    @Test
    void moderationPlaceholdersReadMutedAndJailed() {
        FakeModeration moderation = new FakeModeration().muted(true).jailed(false);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "muted")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "jailed")).contains("no");
    }

    @Test
    void moderationBanPlaceholdersReadReasonRemainingAndIssuer() {
        FakeModeration moderation = new FakeModeration()
                .ban(new ModerationPlaceholders.SanctionView(Optional.of(Duration.ofSeconds(90)), "griefing", "Mod"));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_banned")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_reason")).contains("griefing");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_issuer")).contains("Mod");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining")).contains("90");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining_formatted"))
                .contains("1m30s");
    }

    @Test
    void moderationPermanentBanRendersPermanentRemaining() {
        FakeModeration moderation = new FakeModeration()
                .ban(new ModerationPlaceholders.SanctionView(Optional.empty(), "cheating", "Console"));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_banned")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining")).contains("permanent");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining_formatted"))
                .contains("permanent");
    }

    @Test
    void moderationMutePlaceholdersReadReasonRemainingAndIssuer() {
        FakeModeration moderation = new FakeModeration()
                .mute(new ModerationPlaceholders.SanctionView(Optional.of(Duration.ofMinutes(2)), "spam", "Helper"));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "muted")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_reason")).contains("spam");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_issuer")).contains("Helper");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_remaining")).contains("120");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_remaining_formatted"))
                .contains("2m");
    }

    @Test
    void moderationFrozenAndWarnsReadThroughTheSeam() {
        FakeModeration moderation = new FakeModeration().frozen(true).warns(3);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_frozen")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_warns")).contains("3");
    }

    @Test
    void moderationDetailKeysDashWhenNotSanctioned() {
        FakeModeration moderation = new FakeModeration();
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_banned")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_reason")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_reason")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_remaining")).contains("-");
    }

    @Test
    void moderationFamilyDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        // A disabled moderation module means "no one is sanctioned" for the booleans, the dash for details.
        assertThat(resolver.resolve(ALICE, true, "moderation_banned")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "moderation_frozen")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "moderation_warns")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_reason")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_remaining")).contains("-");
    }

    @Test
    void teleportCooldownAndWarmupRenderRawAndFormattedRemaining() {
        FakeTeleport teleport =
                new FakeTeleport().cooldown(Duration.ofSeconds(90)).warmup(Duration.ofSeconds(3));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().teleport(teleport).build());

        assertThat(resolver.resolve(ALICE, true, "teleport_cooldown_remaining")).contains("90");
        assertThat(resolver.resolve(ALICE, true, "teleport_cooldown_remaining_formatted"))
                .contains("1m30s");
        assertThat(resolver.resolve(ALICE, true, "teleport_warmup_remaining")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "teleport_warmup_remaining_formatted"))
                .contains("3s");
    }

    @Test
    void teleportCooldownAndWarmupRenderZeroWhenNothingInFlight() {
        FakeTeleport teleport = new FakeTeleport();
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().teleport(teleport).build());

        assertThat(resolver.resolve(ALICE, true, "teleport_cooldown_remaining")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "teleport_cooldown_remaining_formatted"))
                .contains("0s");
        assertThat(resolver.resolve(ALICE, true, "teleport_warmup_remaining")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "teleport_warmup_remaining_formatted"))
                .contains("0s");
    }

    @Test
    void teleportBackPlaceholdersReadCaptureAndDashWhenNone() {
        FakeTeleport withBack = new FakeTeleport().back(new TeleportPlaceholders.BackView("world_nether", 10, 64, -20));
        FakeTeleport noBack = new FakeTeleport();
        PlaceholderResolver withResolver =
                resolverWith(PlaceholderContexts.builder().teleport(withBack).build());
        PlaceholderResolver noResolver =
                resolverWith(PlaceholderContexts.builder().teleport(noBack).build());

        assertThat(withResolver.resolve(ALICE, true, "teleport_back_available")).contains("yes");
        assertThat(withResolver.resolve(ALICE, true, "teleport_back_world")).contains("world_nether");
        assertThat(withResolver.resolve(ALICE, true, "teleport_back_x")).contains("10");
        assertThat(withResolver.resolve(ALICE, true, "teleport_back_y")).contains("64");
        assertThat(withResolver.resolve(ALICE, true, "teleport_back_z")).contains("-20");
        assertThat(noResolver.resolve(ALICE, true, "teleport_back_available")).contains("no");
        assertThat(noResolver.resolve(ALICE, true, "teleport_back_world")).contains("-");
        assertThat(noResolver.resolve(ALICE, true, "teleport_back_x")).contains("-");
    }

    @Test
    void teleportRequestPlaceholdersReadIncomingOutgoingAndAccepting() {
        FakeTeleport teleport = new FakeTeleport().incoming(2).outgoing(true).accepting(false);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().teleport(teleport).build());

        assertThat(resolver.resolve(ALICE, true, "teleport_tpa_incoming")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "teleport_tpa_pending")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "teleport_accepting")).contains("no");
    }

    @Test
    void teleportSessionPlaceholdersDegradeOfflineAndWhenDisabled() {
        FakeTeleport teleport = new FakeTeleport()
                .warmup(Duration.ofSeconds(3))
                .incoming(1)
                .outgoing(true)
                .accepting(true);
        PlaceholderResolver withSeam =
                resolverWith(PlaceholderContexts.builder().teleport(teleport).build());
        PlaceholderResolver noSeam = resolverWith(PlaceholderContexts.builder().build());

        // Offline: the session-only warmup/request/accept keys cannot be queried, so they degrade to the dash.
        assertThat(withSeam.resolve(ALICE, false, "teleport_warmup_remaining")).contains("-");
        assertThat(withSeam.resolve(ALICE, false, "teleport_tpa_incoming")).contains("-");
        assertThat(withSeam.resolve(ALICE, false, "teleport_tpa_pending")).contains("-");
        assertThat(withSeam.resolve(ALICE, false, "teleport_accepting")).contains("-");
        // Disabled module: every teleport key degrades to the dash.
        assertThat(noSeam.resolve(ALICE, true, "teleport_cooldown_remaining")).contains("-");
        assertThat(noSeam.resolve(ALICE, true, "teleport_back_available")).contains("-");
        // An unknown teleport_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(withSeam.resolve(ALICE, true, "teleport_unknown")).contains("-");
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
    void messagingPlaceholdersReadMailReplyToggleSpyAndIgnore() {
        FakeMessaging messaging = new FakeMessaging()
                .unread(3)
                .total(8)
                .replyTarget("Bob")
                .accepting(false)
                .spying(true)
                .ignoring(2);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().messaging(messaging).build());

        assertThat(resolver.resolve(ALICE, true, "messaging_mail_unread")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "messaging_mail_total")).contains("8");
        assertThat(resolver.resolve(ALICE, true, "messaging_reply_target")).contains("Bob");
        assertThat(resolver.resolve(ALICE, true, "messaging_msgtoggle")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "messaging_socialspy")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "messaging_ignoring_count")).contains("2");
    }

    @Test
    void messagingReplyTargetDashesWhenNoConversation() {
        FakeMessaging messaging = new FakeMessaging().accepting(true);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().messaging(messaging).build());

        assertThat(resolver.resolve(ALICE, true, "messaging_reply_target")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "messaging_msgtoggle")).contains("yes");
    }

    @Test
    void messagingSessionKeysDashOfflineButDurableKeysStillRead() {
        FakeMessaging messaging = new FakeMessaging()
                .unread(4)
                .total(4)
                .ignoring(1)
                .replyTarget("Bob")
                .accepting(true)
                .spying(true);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().messaging(messaging).build());

        // The DB-backed mail and ignore reads answer for an offline player.
        assertThat(resolver.resolve(ALICE, false, "messaging_mail_unread")).contains("4");
        assertThat(resolver.resolve(ALICE, false, "messaging_mail_total")).contains("4");
        assertThat(resolver.resolve(ALICE, false, "messaging_ignoring_count")).contains("1");
        // The session-only reads degrade to the dash for an offline player.
        assertThat(resolver.resolve(ALICE, false, "messaging_reply_target")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "messaging_msgtoggle")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "messaging_socialspy")).contains("-");
    }

    @Test
    void messagingDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "messaging_mail_unread")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "messaging_msgtoggle")).contains("-");
        // An unknown messaging_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "messaging_unknown")).contains("-");
    }

    @Test
    void staffPlaceholdersReadModeAndOnlineCount() {
        FakeStaff staff = new FakeStaff().mode(ALICE, true).onlineCount(3);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().staff(staff).build());

        assertThat(resolver.resolve(ALICE, true, "staff_mode")).contains("yes");
        assertThat(resolver.resolve(BOB, true, "staff_mode")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "staff_online")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "staff_count")).contains("3");
    }

    @Test
    void staffModeDegradesOfflineButOnlineCountStillReads() {
        FakeStaff staff = new FakeStaff().mode(ALICE, true).onlineCount(2);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().staff(staff).build());

        // staff_mode is session-only: an offline requester holds no marker, so it reads "no".
        assertThat(resolver.resolve(ALICE, false, "staff_mode")).contains("no");
        // The server-wide roster count does not depend on the requester's session, so it still answers.
        assertThat(resolver.resolve(ALICE, false, "staff_online")).contains("2");
    }

    @Test
    void staffDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "staff_mode")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "staff_online")).contains("-");
        // An unknown staff_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "staff_unknown")).contains("-");
    }

    @Test
    void discordlinkPlaceholdersReadLinkedAndId() {
        FakeDiscordlink linked = new FakeDiscordlink().link(ALICE, "123456789012345678");
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().discordlink(linked).build());

        assertThat(resolver.resolve(ALICE, true, "discordlink_linked")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "discordlink_id")).contains("123456789012345678");
        // An unlinked account reports no and dashes the id, both readable offline since the binding is DB-backed.
        assertThat(resolver.resolve(BOB, false, "discordlink_linked")).contains("no");
        assertThat(resolver.resolve(BOB, false, "discordlink_id")).contains("-");
        // The linked account reads identically for an offline requester.
        assertThat(resolver.resolve(ALICE, false, "discordlink_linked")).contains("yes");
        assertThat(resolver.resolve(ALICE, false, "discordlink_id")).contains("123456789012345678");
    }

    @Test
    void discordlinkDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "discordlink_linked")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "discordlink_id")).contains("-");
        // An unknown discordlink_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "discordlink_unknown")).contains("-");
    }

    @Test
    void hologramsCountReadsTheServerWideTotal() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().holograms(() -> 4).build());

        // The count is server-wide, so it reads identically for any requester, online or offline.
        assertThat(resolver.resolve(ALICE, true, "holograms_count")).contains("4");
        assertThat(resolver.resolve(BOB, false, "holograms_count")).contains("4");
    }

    @Test
    void hologramsDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "holograms_count")).contains("-");
        // An unknown holograms_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "holograms_unknown")).contains("-");
    }

    @Test
    void scoreboardVisiblePlaceholderReadsTheShownState() {
        FakeScoreboard shown = new FakeScoreboard().visible(ALICE, true);
        FakeScoreboard hidden = new FakeScoreboard().visible(ALICE, false);
        PlaceholderResolver shownResolver =
                resolverWith(PlaceholderContexts.builder().scoreboard(shown).build());
        PlaceholderResolver hiddenResolver =
                resolverWith(PlaceholderContexts.builder().scoreboard(hidden).build());

        assertThat(shownResolver.resolve(ALICE, true, "scoreboard_visible")).contains("yes");
        assertThat(hiddenResolver.resolve(ALICE, true, "scoreboard_visible")).contains("no");
    }

    @Test
    void scoreboardVisibleDegradesOfflineSinceTheBoardIsSessionScoped() {
        FakeScoreboard shown = new FakeScoreboard().visible(ALICE, true);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().scoreboard(shown).build());

        // The board belongs to a live player, so an offline requester has nothing to show.
        assertThat(resolver.resolve(ALICE, false, "scoreboard_visible")).contains("-");
    }

    @Test
    void scoreboardDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "scoreboard_visible")).contains("-");
        // An unknown scoreboard_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "scoreboard_unknown")).contains("-");
    }

    @Test
    void communicationPlaceholdersReadChatLockAndBroadcastSubscription() {
        FakeCommunication open = new FakeCommunication().chatEnabled(true).receivesBroadcasts(ALICE, true);
        FakeCommunication locked = new FakeCommunication().chatEnabled(false).receivesBroadcasts(ALICE, false);
        PlaceholderResolver openResolver =
                resolverWith(PlaceholderContexts.builder().communication(open).build());
        PlaceholderResolver lockedResolver =
                resolverWith(PlaceholderContexts.builder().communication(locked).build());

        assertThat(openResolver.resolve(ALICE, true, "communication_chat_enabled"))
                .contains("yes");
        assertThat(openResolver.resolve(ALICE, true, "communication_broadcasts"))
                .contains("yes");
        assertThat(lockedResolver.resolve(ALICE, true, "communication_chat_enabled"))
                .contains("no");
        assertThat(lockedResolver.resolve(ALICE, true, "communication_broadcasts"))
                .contains("no");
    }

    @Test
    void communicationBroadcastsDegradeOfflineButChatEnabledStillReads() {
        FakeCommunication open = new FakeCommunication().chatEnabled(true).receivesBroadcasts(ALICE, true);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().communication(open).build());

        // The chat lock is server-wide, so it answers for an offline requester too.
        assertThat(resolver.resolve(ALICE, false, "communication_chat_enabled")).contains("yes");
        // The per-player subscription is session-meaningful, so it degrades to the dash when offline.
        assertThat(resolver.resolve(ALICE, false, "communication_broadcasts")).contains("-");
    }

    @Test
    void communicationDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "communication_chat_enabled")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "communication_broadcasts")).contains("-");
        // An unknown communication_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "communication_unknown")).contains("-");
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

    @Test
    void playerstatePlaceholdersReadLiveState() {
        PlayerstatePlaceholders.Snapshot snapshot = new PlayerstatePlaceholders.Snapshot(
                "creative",
                true,
                false,
                true,
                0.2f,
                0.1f,
                18.0,
                20.0,
                17,
                30,
                0.25f,
                "world",
                100,
                64,
                -200,
                "plains",
                Duration.ofHours(5).plusMinutes(30));
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .playerstate(who -> Optional.of(snapshot))
                .build());

        assertThat(resolver.resolve(ALICE, true, "playerstate_gamemode")).contains("creative");
        assertThat(resolver.resolve(ALICE, true, "playerstate_fly")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "playerstate_flying")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "playerstate_god")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "playerstate_health")).contains("18");
        assertThat(resolver.resolve(ALICE, true, "playerstate_max_health")).contains("20");
        assertThat(resolver.resolve(ALICE, true, "playerstate_food")).contains("17");
        assertThat(resolver.resolve(ALICE, true, "playerstate_level")).contains("30");
        assertThat(resolver.resolve(ALICE, true, "playerstate_xp")).contains("0.25");
        assertThat(resolver.resolve(ALICE, true, "playerstate_world")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "playerstate_x")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "playerstate_y")).contains("64");
        assertThat(resolver.resolve(ALICE, true, "playerstate_z")).contains("-200");
        assertThat(resolver.resolve(ALICE, true, "playerstate_biome")).contains("plains");
        assertThat(resolver.resolve(ALICE, true, "playerstate_playtime")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "playerstate_playtime_formatted"))
                .contains("5h30m");
    }

    @Test
    void playerstateSpeedFollowsWhetherTheyAreFlying() {
        PlayerstatePlaceholders.Snapshot walking = new PlayerstatePlaceholders.Snapshot(
                "survival",
                false,
                false,
                false,
                0.2f,
                0.1f,
                20.0,
                20.0,
                20,
                0,
                0f,
                "world",
                0,
                0,
                0,
                "plains",
                Duration.ZERO);
        PlayerstatePlaceholders.Snapshot flying = new PlayerstatePlaceholders.Snapshot(
                "survival",
                true,
                true,
                false,
                0.2f,
                0.1f,
                20.0,
                20.0,
                20,
                0,
                0f,
                "world",
                0,
                0,
                0,
                "plains",
                Duration.ZERO);

        assertThat(resolverWith(PlaceholderContexts.builder()
                                .playerstate(who -> Optional.of(walking))
                                .build())
                        .resolve(ALICE, true, "playerstate_speed"))
                .contains("0.2");
        assertThat(resolverWith(PlaceholderContexts.builder()
                                .playerstate(who -> Optional.of(flying))
                                .build())
                        .resolve(ALICE, true, "playerstate_speed"))
                .contains("0.1");
    }

    @Test
    void playerstatePlaceholdersDegradeOfflineAndWhenDisabled() {
        PlayerstatePlaceholders empty = who -> Optional.empty();
        PlaceholderResolver withSeam =
                resolverWith(PlaceholderContexts.builder().playerstate(empty).build());
        PlaceholderResolver noSeam = resolverWith(PlaceholderContexts.builder().build());

        // Offline: the seam reports no snapshot, so every key is the dash.
        assertThat(withSeam.resolve(ALICE, true, "playerstate_gamemode")).contains("-");
        // The offline guard short-circuits before the seam is even consulted.
        assertThat(withSeam.resolve(ALICE, false, "playerstate_health")).contains("-");
        // Disabled module: no seam at all.
        assertThat(noSeam.resolve(ALICE, true, "playerstate_world")).contains("-");
        assertThat(noSeam.resolve(ALICE, true, "playerstate_unknown")).contains("-");
    }

    @Test
    void serverMetricsReadGlobalsIgnoringTheRequester() {
        FakeServerMetrics metrics = new FakeServerMetrics()
                .online(12)
                .maxPlayers(50)
                .version("1.21.11")
                .uptime(Duration.ofHours(1).plusMinutes(30))
                .tps(19.97, 19.5, 18.2)
                .ram(2048, 4096, 1500)
                .worldPlayers("world", 8);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().serverMetrics(metrics).build());

        // The requesting player and online flag are irrelevant — every value is server-wide.
        assertThat(resolver.resolve(ALICE, false, "server_online")).contains("12");
        assertThat(resolver.resolve(BOB, true, "server_max_players")).contains("50");
        assertThat(resolver.resolve(ALICE, true, "server_version")).contains("1.21.11");
        assertThat(resolver.resolve(ALICE, true, "server_uptime")).contains("90");
        assertThat(resolver.resolve(ALICE, true, "server_uptime_formatted")).contains("1h30m");
        assertThat(resolver.resolve(ALICE, true, "server_ram_used")).contains("2048");
        assertThat(resolver.resolve(ALICE, true, "server_ram_max")).contains("4096");
        assertThat(resolver.resolve(ALICE, true, "server_ram_free")).contains("1500");
        assertThat(resolver.resolve(ALICE, true, "server_world_players_world")).contains("8");
    }

    @Test
    void serverTpsReadsEachWindowClampedAndTrimmed() {
        // The 1-minute window over-reports above 20 on a fresh server; it is clamped to the 20.0 ceiling.
        FakeServerMetrics metrics = new FakeServerMetrics().tps(20.04, 19.5, 15.0);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().serverMetrics(metrics).build());

        assertThat(resolver.resolve(ALICE, true, "server_tps")).contains("20");
        assertThat(resolver.resolve(ALICE, true, "server_tps_5m")).contains("19.5");
        assertThat(resolver.resolve(ALICE, true, "server_tps_15m")).contains("15");
    }

    @Test
    void serverTpsColoredWrapsTheRateInGreenYellowOrRed() {
        PlaceholderResolver healthy = resolverWith(PlaceholderContexts.builder()
                .serverMetrics(new FakeServerMetrics().tps(19.9, 19.9, 19.9))
                .build());
        PlaceholderResolver strained = resolverWith(PlaceholderContexts.builder()
                .serverMetrics(new FakeServerMetrics().tps(16.0, 16.0, 16.0))
                .build());
        PlaceholderResolver lagging = resolverWith(PlaceholderContexts.builder()
                .serverMetrics(new FakeServerMetrics().tps(9.0, 9.0, 9.0))
                .build());

        assertThat(healthy.resolve(ALICE, true, "server_tps_colored")).contains("<green>19.9</green>");
        assertThat(strained.resolve(ALICE, true, "server_tps_colored")).contains("<yellow>16</yellow>");
        assertThat(lagging.resolve(ALICE, true, "server_tps_colored")).contains("<red>9</red>");
    }

    @Test
    void serverWorldPlayersDashesUnknownWorldAndBlankName() {
        FakeServerMetrics metrics = new FakeServerMetrics().worldPlayers("world", 4);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().serverMetrics(metrics).build());

        assertThat(resolver.resolve(ALICE, true, "server_world_players_world")).contains("4");
        assertThat(resolver.resolve(ALICE, true, "server_world_players_void")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "server_world_players_")).contains("-");
    }

    @Test
    void serverMetricsDegradeWhenSeamAbsentButUnknownKeyStaysRaw() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        // No seam wired: every server_ key degrades to the dash rather than the raw token.
        assertThat(resolver.resolve(ALICE, true, "server_online")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "server_tps")).contains("-");
        // An unknown server_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "server_unknown")).contains("-");
    }

    private static PresencePlaceholders presenceSeam(PresencePlaceholders.Snapshot snapshot) {
        return who -> Optional.of(snapshot);
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

    /** A configurable {@link TeleportPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeTeleport implements TeleportPlaceholders {

        private Optional<Duration> cooldown = Optional.empty();
        private Optional<Duration> warmup = Optional.empty();
        private Optional<BackView> back = Optional.empty();
        private int incoming;
        private boolean outgoing;
        private boolean accepting;

        FakeTeleport cooldown(Duration remaining) {
            this.cooldown = Optional.of(remaining);
            return this;
        }

        FakeTeleport warmup(Duration remaining) {
            this.warmup = Optional.of(remaining);
            return this;
        }

        FakeTeleport back(BackView view) {
            this.back = Optional.of(view);
            return this;
        }

        FakeTeleport incoming(int count) {
            this.incoming = count;
            return this;
        }

        FakeTeleport outgoing(boolean pending) {
            this.outgoing = pending;
            return this;
        }

        FakeTeleport accepting(boolean value) {
            this.accepting = value;
            return this;
        }

        @Override
        public Optional<Duration> cooldownRemaining(PlayerRef who) {
            return cooldown;
        }

        @Override
        public Optional<Duration> warmupRemaining(PlayerRef who) {
            return warmup;
        }

        @Override
        public Optional<BackView> backLocation(PlayerRef who) {
            return back;
        }

        @Override
        public int incomingRequests(PlayerRef who) {
            return incoming;
        }

        @Override
        public boolean hasOutgoingRequest(PlayerRef who) {
            return outgoing;
        }

        @Override
        public boolean acceptingRequests(PlayerRef who) {
            return accepting;
        }
    }

    /** A configurable {@link ModerationPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeModeration implements ModerationPlaceholders {

        private boolean muted;
        private boolean jailed;
        private boolean frozen;
        private int warns;
        private Optional<SanctionView> ban = Optional.empty();
        private Optional<SanctionView> mute = Optional.empty();

        FakeModeration muted(boolean value) {
            this.muted = value;
            return this;
        }

        FakeModeration jailed(boolean value) {
            this.jailed = value;
            return this;
        }

        FakeModeration frozen(boolean value) {
            this.frozen = value;
            return this;
        }

        FakeModeration warns(int count) {
            this.warns = count;
            return this;
        }

        FakeModeration ban(SanctionView view) {
            this.ban = Optional.of(view);
            return this;
        }

        FakeModeration mute(SanctionView view) {
            this.mute = Optional.of(view);
            this.muted = true;
            return this;
        }

        @Override
        public boolean isMuted(PlayerRef who) {
            return muted;
        }

        @Override
        public boolean isJailed(PlayerRef who) {
            return jailed;
        }

        @Override
        public boolean isFrozen(PlayerRef who) {
            return frozen;
        }

        @Override
        public int warnCount(PlayerRef who) {
            return warns;
        }

        @Override
        public Optional<SanctionView> activeBan(PlayerRef who) {
            return ban;
        }

        @Override
        public Optional<SanctionView> activeMute(PlayerRef who) {
            return mute;
        }
    }

    /** A configurable {@link KitsPlaceholders} fake — every read returns the value the test seeded, else empty. */
    private static final class FakeKits implements KitsPlaceholders {

        private final java.util.Map<String, Duration> cooldowns = new java.util.HashMap<>();
        private final java.util.Map<String, Boolean> available = new java.util.HashMap<>();
        private final java.util.Map<String, Boolean> permission = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> costs = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> claimsLeft = new java.util.HashMap<>();
        private List<String> usableIds = List.of();

        FakeKits cooldown(String kitId, Duration remaining) {
            cooldowns.put(kitId, remaining);
            return this;
        }

        FakeKits available(String kitId, boolean value) {
            available.put(kitId, value);
            return this;
        }

        FakeKits hasPermission(String kitId, boolean value) {
            permission.put(kitId, value);
            return this;
        }

        FakeKits cost(String kitId, BigDecimal amount) {
            costs.put(kitId, amount);
            return this;
        }

        FakeKits claimsLeft(String kitId, int value) {
            claimsLeft.put(kitId, value);
            return this;
        }

        FakeKits usable(String... ids) {
            this.usableIds = List.of(ids);
            return this;
        }

        @Override
        public Optional<Duration> cooldownRemaining(PlayerRef who, String kitId) {
            return Optional.ofNullable(cooldowns.get(kitId));
        }

        @Override
        public Optional<Boolean> available(PlayerRef who, String kitId) {
            return Optional.ofNullable(available.get(kitId));
        }

        @Override
        public Optional<Boolean> hasPermission(PlayerRef who, String kitId) {
            return Optional.ofNullable(permission.get(kitId));
        }

        @Override
        public Optional<BigDecimal> cost(String kitId) {
            return Optional.ofNullable(costs.get(kitId));
        }

        @Override
        public Optional<Integer> claimsLeft(PlayerRef who, String kitId) {
            return Optional.ofNullable(claimsLeft.get(kitId));
        }

        @Override
        public List<String> usableIds(PlayerRef who) {
            return usableIds;
        }
    }

    /** A configurable {@link WarpsPlaceholders} fake — only the seeded (visible) warps are counted/listed/found. */
    private static final class FakeWarps implements WarpsPlaceholders {

        private final java.util.LinkedHashMap<String, WarpView> visible = new java.util.LinkedHashMap<>();

        FakeWarps visible(String name, WarpView view) {
            visible.put(name, view);
            return this;
        }

        @Override
        public int count(PlayerRef who) {
            return visible.size();
        }

        @Override
        public List<String> accessibleNames(PlayerRef who) {
            return List.copyOf(visible.keySet());
        }

        @Override
        public Optional<WarpView> find(PlayerRef who, String name) {
            return Optional.ofNullable(visible.get(name.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    private static WarpsPlaceholders.WarpView warpView(
            String world, int x, int y, int z, long visits, String owner, String cost) {
        return new WarpsPlaceholders.WarpView(world, x, y, z, visits, owner, new BigDecimal(cost));
    }

    /** A configurable {@link PlayerwarpsPlaceholders} fake — only the seeded (owned) warps are counted/listed/found. */
    private static final class FakePlayerwarps implements PlayerwarpsPlaceholders {

        private final java.util.LinkedHashMap<String, PlayerWarpView> owned = new java.util.LinkedHashMap<>();
        private final int count;
        private final int limit;

        FakePlayerwarps(int count, int limit) {
            this.count = count;
            this.limit = limit;
        }

        FakePlayerwarps owned(String name, PlayerWarpView view) {
            owned.put(name, view);
            return this;
        }

        @Override
        public int count(PlayerRef who) {
            return count;
        }

        @Override
        public int limit(PlayerRef who) {
            return limit;
        }

        @Override
        public List<PlayerWarpView> list(PlayerRef who) {
            return List.copyOf(owned.values());
        }

        @Override
        public Optional<PlayerWarpView> find(PlayerRef who, String name) {
            return Optional.ofNullable(owned.get(name.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    private static PlayerwarpsPlaceholders.PlayerWarpView playerWarpView(
            String name, String owner, String world, int x, int y, int z, long visits) {
        return new PlayerwarpsPlaceholders.PlayerWarpView(name, owner, world, x, y, z, visits);
    }

    /** A configurable {@link MessagingPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeMessaging implements MessagingPlaceholders {

        private long unread;
        private long total;
        private Optional<String> replyTarget = Optional.empty();
        private boolean accepting = true;
        private boolean spying;
        private int ignoring;

        FakeMessaging unread(long value) {
            this.unread = value;
            return this;
        }

        FakeMessaging total(long value) {
            this.total = value;
            return this;
        }

        FakeMessaging replyTarget(String name) {
            this.replyTarget = Optional.of(name);
            return this;
        }

        FakeMessaging accepting(boolean value) {
            this.accepting = value;
            return this;
        }

        FakeMessaging spying(boolean value) {
            this.spying = value;
            return this;
        }

        FakeMessaging ignoring(int value) {
            this.ignoring = value;
            return this;
        }

        @Override
        public long unreadMail(PlayerRef who) {
            return unread;
        }

        @Override
        public long totalMail(PlayerRef who) {
            return total;
        }

        @Override
        public Optional<String> replyTarget(PlayerRef who) {
            return replyTarget;
        }

        @Override
        public boolean acceptingMessages(PlayerRef who) {
            return accepting;
        }

        @Override
        public boolean socialSpy(PlayerRef who) {
            return spying;
        }

        @Override
        public int ignoringCount(PlayerRef who) {
            return ignoring;
        }
    }

    /** A configurable {@link StaffPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeStaff implements StaffPlaceholders {

        private final java.util.Set<PlayerRef> inMode = new java.util.HashSet<>();
        private int onlineCount;

        FakeStaff mode(PlayerRef who, boolean value) {
            if (value) {
                inMode.add(who);
            } else {
                inMode.remove(who);
            }
            return this;
        }

        FakeStaff onlineCount(int value) {
            this.onlineCount = value;
            return this;
        }

        @Override
        public boolean inStaffMode(PlayerRef who) {
            return inMode.contains(who);
        }

        @Override
        public int onlineStaffCount() {
            return onlineCount;
        }
    }

    /** A configurable {@link ScoreboardPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeScoreboard implements ScoreboardPlaceholders {

        private final java.util.Set<PlayerRef> shown = new java.util.HashSet<>();

        FakeScoreboard visible(PlayerRef who, boolean value) {
            if (value) {
                shown.add(who);
            } else {
                shown.remove(who);
            }
            return this;
        }

        @Override
        public boolean visible(PlayerRef who) {
            return shown.contains(who);
        }
    }

    /** A configurable {@link DiscordlinkPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeDiscordlink implements DiscordlinkPlaceholders {

        private final java.util.Map<PlayerRef, String> ids = new java.util.HashMap<>();

        FakeDiscordlink link(PlayerRef who, String discordId) {
            ids.put(who, discordId);
            return this;
        }

        @Override
        public boolean linked(PlayerRef who) {
            return ids.containsKey(who);
        }

        @Override
        public Optional<String> discordId(PlayerRef who) {
            return Optional.ofNullable(ids.get(who));
        }
    }

    /** A configurable {@link CommunicationPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeCommunication implements CommunicationPlaceholders {

        private final java.util.Set<PlayerRef> receiving = new java.util.HashSet<>();
        private boolean chatEnabled = true;

        FakeCommunication chatEnabled(boolean value) {
            this.chatEnabled = value;
            return this;
        }

        FakeCommunication receivesBroadcasts(PlayerRef who, boolean value) {
            if (value) {
                receiving.add(who);
            } else {
                receiving.remove(who);
            }
            return this;
        }

        @Override
        public boolean chatEnabled() {
            return chatEnabled;
        }

        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return receiving.contains(who);
        }
    }

    private static VaultsPlaceholders fakeVaults(int count, int max, int size) {
        return new VaultsPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return count;
            }

            @Override
            public int max(PlayerRef who) {
                return max;
            }

            @Override
            public int size(PlayerRef who) {
                return size;
            }
        };
    }

    /** A configurable {@link ServerMetricsPlaceholders} fake — every read returns the value the test seeded. */
    private static final class FakeServerMetrics implements ServerMetricsPlaceholders {

        private final java.util.Map<String, Integer> worldPlayers = new java.util.HashMap<>();
        private int online;
        private int maxPlayers;
        private String version = "1.21.11";
        private Duration uptime = Duration.ZERO;
        private double[] tps = {20.0, 20.0, 20.0};
        private long ramUsed;
        private long ramMax;
        private long ramFree;

        FakeServerMetrics online(int value) {
            this.online = value;
            return this;
        }

        FakeServerMetrics maxPlayers(int value) {
            this.maxPlayers = value;
            return this;
        }

        FakeServerMetrics version(String value) {
            this.version = value;
            return this;
        }

        FakeServerMetrics uptime(Duration value) {
            this.uptime = value;
            return this;
        }

        FakeServerMetrics tps(double m1, double m5, double m15) {
            this.tps = new double[] {m1, m5, m15};
            return this;
        }

        FakeServerMetrics ram(long used, long max, long free) {
            this.ramUsed = used;
            this.ramMax = max;
            this.ramFree = free;
            return this;
        }

        FakeServerMetrics worldPlayers(String world, int count) {
            this.worldPlayers.put(world, count);
            return this;
        }

        @Override
        public int onlinePlayers() {
            return online;
        }

        @Override
        public int maxPlayers() {
            return maxPlayers;
        }

        @Override
        public String minecraftVersion() {
            return version;
        }

        @Override
        public Duration uptime() {
            return uptime;
        }

        @Override
        public double[] tps() {
            return tps.clone();
        }

        @Override
        public long ramUsedMb() {
            return ramUsed;
        }

        @Override
        public long ramMaxMb() {
            return ramMax;
        }

        @Override
        public long ramFreeMb() {
            return ramFree;
        }

        @Override
        public OptionalInt worldPlayers(String world) {
            Integer count = worldPlayers.get(world);
            return count == null ? OptionalInt.empty() : OptionalInt.of(count);
        }
    }

    private static HomesPlaceholders fakeHomes(int count, int limit) {
        return fakeHomes(count, limit, List.of());
    }

    private static HomesPlaceholders fakeHomes(int count, int limit, List<HomesPlaceholders.HomeView> homes) {
        return new HomesPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return count;
            }

            @Override
            public int limit(PlayerRef who) {
                return limit;
            }

            @Override
            public List<HomesPlaceholders.HomeView> list(PlayerRef who) {
                return homes;
            }
        };
    }
}
