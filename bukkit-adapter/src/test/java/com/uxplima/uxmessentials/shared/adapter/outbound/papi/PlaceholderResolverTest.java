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
