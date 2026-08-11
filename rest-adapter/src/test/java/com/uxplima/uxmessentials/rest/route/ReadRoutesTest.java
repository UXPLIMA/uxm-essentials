package com.uxplima.uxmessentials.rest.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.api.query.UxmKitsQuery;
import com.uxplima.uxmessentials.api.query.UxmMessagingQuery;
import com.uxplima.uxmessentials.api.query.UxmModerationQuery;
import com.uxplima.uxmessentials.api.query.UxmPresenceQuery;
import com.uxplima.uxmessentials.api.query.UxmRanksQuery;
import com.uxplima.uxmessentials.api.query.UxmRegionsQuery;
import com.uxplima.uxmessentials.api.query.UxmTradeQuery;
import com.uxplima.uxmessentials.api.query.UxmVanishQuery;
import com.uxplima.uxmessentials.api.query.UxmVoteQuery;
import com.uxplima.uxmessentials.api.query.UxmWarpsQuery;
import com.uxplima.uxmessentials.api.query.UxmWorldsQuery;
import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import com.uxplima.uxmessentials.api.view.UxmKit;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmMail;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.api.view.UxmPresence;
import com.uxplima.uxmessentials.api.view.UxmRank;
import com.uxplima.uxmessentials.api.view.UxmRankStanding;
import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmSanctionKind;
import com.uxplima.uxmessentials.api.view.UxmTrade;
import com.uxplima.uxmessentials.api.view.UxmVoteParty;
import com.uxplima.uxmessentials.api.view.UxmVotePeriod;
import com.uxplima.uxmessentials.api.view.UxmVoteTotals;
import com.uxplima.uxmessentials.api.view.UxmWarp;
import com.uxplima.uxmessentials.api.view.UxmWorld;
import com.uxplima.uxmessentials.api.view.UxmWorldAccess;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import org.junit.jupiter.api.Test;

/**
 * What the read routes answer.
 *
 * <p>Driven through {@link Calls}, so each test asserts on the response a client would receive, refusals included,
 * rather than on the return value of a handler method.
 */
class ReadRoutesTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String PLAYER_PATH = "/api/v1/players/" + PLAYER;
    private static final Instant WHEN = Instant.parse("2026-01-02T03:04:05Z");
    private static final UxmLocation SOMEWHERE = new UxmLocation("world", 1.5, 64, -2.5, 90f, 0f);

    @Test
    void aModuleThatIsOffIsAServiceUnavailableRatherThanAnEmptyList() {
        Calls.Answer answer = Calls.get(mock(UxmEssentialsApi.class), PLAYER_PATH + "/homes");

        assertThat(answer.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(answer.ok()).isFalse();
        assertThat(answer.code()).isEqualTo("module-off");
        assertThat(answer.message()).contains("homes");
    }

    @Test
    void aPathNobodyServesIsANotFound() {
        assertThat(Calls.get(mock(UxmEssentialsApi.class), "/api/v1/nowhere").status())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anIdThatIsNotAUuidIsABadRequest() {
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.homes()).thenReturn(Optional.of(mock(UxmHomesQuery.class)));

        Calls.Answer answer = Calls.get(api, "/api/v1/players/steve/homes");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.message()).contains("uuid");
    }

    @Test
    void homesComeBackWithTheQuotaBesideThem() {
        UxmHomesQuery homes = mock(UxmHomesQuery.class);
        when(homes.list(PLAYER)).thenReturn(CompletableFuture.completedFuture(List.of(home(0, "base"))));
        when(homes.count(PLAYER)).thenReturn(CompletableFuture.completedFuture(1));
        when(homes.limit(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.of(5)));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.homes()).thenReturn(Optional.of(homes));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/homes");

        assertThat(answer.object().get("count").getAsInt()).isEqualTo(1);
        assertThat(answer.object().get("limit").getAsInt()).isEqualTo(5);
        assertThat(answer.object().getAsJsonArray("homes")).hasSize(1);
    }

    @Test
    void aHomeIsAskedForByTheSlotNumberPlayersSee() {
        UxmHomesQuery homes = mock(UxmHomesQuery.class);
        when(homes.get(PLAYER, 0)).thenReturn(CompletableFuture.completedFuture(Optional.of(home(0, "base"))));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.homes()).thenReturn(Optional.of(homes));

        assertThat(Calls.get(api, PLAYER_PATH + "/homes/1")
                        .object()
                        .get("label")
                        .getAsString())
                .isEqualTo("base");
    }

    @Test
    void aHomeSlotThatIsNotSetIsANotFound() {
        UxmHomesQuery homes = mock(UxmHomesQuery.class);
        when(homes.get(PLAYER, 8)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.homes()).thenReturn(Optional.of(homes));

        assertThat(Calls.get(api, PLAYER_PATH + "/homes/9").status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aBalanceIsAnExactNumber() {
        UxmEconomyQuery economy = mock(UxmEconomyQuery.class);
        when(economy.balance(PLAYER))
                .thenReturn(CompletableFuture.completedFuture(new UxmMoney("coins", new BigDecimal("12345.67"))));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.economy()).thenReturn(Optional.of(economy));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/balance");

        assertThat(answer.object().get("amount").getAsBigDecimal()).isEqualTo(new BigDecimal("12345.67"));
        assertThat(answer.object().get("currency").getAsString()).isEqualTo("coins");
    }

    @Test
    void aCurrencyThisServerDoesNotHaveIsANotFoundRatherThanZero() {
        UxmEconomyQuery economy = mock(UxmEconomyQuery.class);
        when(economy.balance(PLAYER, "rubies")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.economy()).thenReturn(Optional.of(economy));

        assertThat(Calls.get(api, PLAYER_PATH + "/balance", Map.of("currency", "rubies"))
                        .status())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theLeaderboardIsCappedNoMatterWhatIsAskedFor() {
        UxmEconomyQuery economy = mock(UxmEconomyQuery.class);
        when(economy.top(anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.economy()).thenReturn(Optional.of(economy));

        Calls.get(api, "/api/v1/economy/top", Map.of("limit", "100000"));

        verify(economy).top(100);
    }

    @Test
    void aWarpLookupCarriesItsRating() {
        UxmWarpsQuery warps = mock(UxmWarpsQuery.class);
        when(warps.get("spawn")).thenReturn(CompletableFuture.completedFuture(Optional.of(warp("spawn"))));
        when(warps.averageRating("spawn")).thenReturn(CompletableFuture.completedFuture(4.5));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.warps()).thenReturn(Optional.of(warps));

        Calls.Answer answer = Calls.get(api, "/api/v1/warps/spawn");

        assertThat(answer.object().get("name").getAsString()).isEqualTo("spawn");
        assertThat(answer.object().get("average-rating").getAsDouble()).isEqualTo(4.5);
    }

    @Test
    void theWarpListNarrowsToOnePlayerWhenAskedTo() {
        UxmWarpsQuery warps = mock(UxmWarpsQuery.class);
        when(warps.visibleTo(PLAYER)).thenReturn(CompletableFuture.completedFuture(List.of(warp("mine"))));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.warps()).thenReturn(Optional.of(warps));

        Calls.Answer answer = Calls.get(api, "/api/v1/warps", Map.of("visible-to", PLAYER.toString()));

        assertThat(answer.array()).hasSize(1);
    }

    @Test
    void everyKitCarriesWhetherThisPlayerCanClaimItAndWhenTheyCan() {
        UxmKitsQuery kits = mock(UxmKitsQuery.class);
        when(kits.list()).thenReturn(List.of(kit("miner")));
        when(kits.canClaim(PLAYER, "miner")).thenReturn(CompletableFuture.completedFuture(false));
        when(kits.cooldownRemaining(PLAYER, "miner"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(Duration.ofMinutes(90))));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.kits()).thenReturn(Optional.of(kits));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/kits");

        assertThat(answer.array().get(0).getAsJsonObject().get("can-claim").getAsBoolean())
                .isFalse();
        assertThat(answer.array()
                        .get(0)
                        .getAsJsonObject()
                        .get("cooldown-remaining-seconds")
                        .getAsLong())
                .isEqualTo(5400);
    }

    @Test
    void aKitOffCooldownReportsNoRemainingTimeRatherThanZero() {
        UxmKitsQuery kits = mock(UxmKitsQuery.class);
        when(kits.list()).thenReturn(List.of(kit("miner")));
        when(kits.canClaim(PLAYER, "miner")).thenReturn(CompletableFuture.completedFuture(true));
        when(kits.cooldownRemaining(PLAYER, "miner")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.kits()).thenReturn(Optional.of(kits));

        assertThat(Calls.get(api, PLAYER_PATH + "/kits")
                        .array()
                        .get(0)
                        .getAsJsonObject()
                        .get("cooldown-remaining-seconds")
                        .isJsonNull())
                .isTrue();
    }

    @Test
    void standingPunishmentsAreOneAnswerWithNullsForWhatIsNotThere() {
        UxmModerationQuery moderation = mock(UxmModerationQuery.class);
        when(moderation.ban(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.of(ban())));
        when(moderation.mute(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(moderation.jail(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(moderation.warns(PLAYER)).thenReturn(CompletableFuture.completedFuture(List.of()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.moderation()).thenReturn(Optional.of(moderation));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/sanctions");

        assertThat(answer.object().getAsJsonObject("ban").get("permanent").getAsBoolean())
                .isTrue();
        assertThat(answer.object().get("mute").isJsonNull()).isTrue();
        assertThat(answer.object().getAsJsonArray("warns")).isEmpty();
    }

    @Test
    void presenceForSomebodyWhoIsNotOnlineIsANotFound() {
        UxmPresenceQuery presence = mock(UxmPresenceQuery.class);
        when(presence.of(PLAYER)).thenReturn(Optional.empty());
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.presence()).thenReturn(Optional.of(presence));

        assertThat(Calls.get(api, PLAYER_PATH + "/presence").status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theAfkListIsWhoeverIsAway() {
        UxmPresenceQuery presence = mock(UxmPresenceQuery.class);
        when(presence.afk()).thenReturn(List.of(new UxmPresence(PLAYER, true, Optional.of("lunch"), WHEN)));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.presence()).thenReturn(Optional.of(presence));

        Calls.Answer answer = Calls.get(api, "/api/v1/presence/afk");

        assertThat(answer.array()).hasSize(1);
        assertThat(answer.array().get(0).getAsJsonObject().get("afk-reason").getAsString())
                .isEqualTo("lunch");
    }

    @Test
    void vanishAnswersWhetherAndAtWhatLevel() {
        UxmVanishQuery vanish = mock(UxmVanishQuery.class);
        when(vanish.isVanished(PLAYER)).thenReturn(true);
        when(vanish.levelOf(PLAYER)).thenReturn(3);
        when(vanish.vanished()).thenReturn(Set.of(PLAYER));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.vanish()).thenReturn(Optional.of(vanish));

        assertThat(Calls.get(api, PLAYER_PATH + "/vanish").object().get("level").getAsInt())
                .isEqualTo(3);
        assertThat(Calls.get(api, "/api/v1/vanish").array()).hasSize(1);
    }

    @Test
    void worldAccessSaysWhyItIsRefused() {
        UxmWorldsQuery worlds = mock(UxmWorldsQuery.class);
        when(worlds.access(PLAYER, "nether")).thenReturn(CompletableFuture.completedFuture(UxmWorldAccess.DENIED_FULL));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.worlds()).thenReturn(Optional.of(worlds));

        Calls.Answer answer = Calls.get(api, "/api/v1/worlds/nether/access", Map.of("player", PLAYER.toString()));

        assertThat(answer.object().get("access").getAsString()).isEqualTo("DENIED_FULL");
        assertThat(answer.object().get("allowed").getAsBoolean()).isFalse();
    }

    @Test
    void worldAccessWithoutAPlayerIsABadRequest() {
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.worlds()).thenReturn(Optional.of(mock(UxmWorldsQuery.class)));

        assertThat(Calls.get(api, "/api/v1/worlds/nether/access").status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aWorldListIsRendered() {
        UxmWorldsQuery worlds = mock(UxmWorldsQuery.class);
        when(worlds.list())
                .thenReturn(CompletableFuture.completedFuture(List.of(new UxmWorld(
                        "world", Optional.of("Spawn"), "NORMAL", "NORMAL", Optional.of(42L), true, true, 7))));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.worlds()).thenReturn(Optional.of(worlds));

        Calls.Answer answer = Calls.get(api, "/api/v1/worlds");

        assertThat(answer.array().get(0).getAsJsonObject().get("display-name").getAsString())
                .isEqualTo("Spawn");
        assertThat(answer.array().get(0).getAsJsonObject().get("seed").getAsLong())
                .isEqualTo(42L);
    }

    @Test
    void theVotePeriodDefaultsToAllTimeAndRefusesOneThatDoesNotExist() {
        UxmVoteQuery vote = mock(UxmVoteQuery.class);
        when(vote.top(any(UxmVotePeriod.class), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.vote()).thenReturn(Optional.of(vote));

        Calls.get(api, "/api/v1/vote/top");
        verify(vote).top(UxmVotePeriod.ALL_TIME, 10);

        assertThat(Calls.get(api, "/api/v1/vote/top", Map.of("period", "fortnightly"))
                        .status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aVotePeriodMayBeWrittenWithADashOrAnUnderscore() {
        UxmVoteQuery vote = mock(UxmVoteQuery.class);
        when(vote.top(any(UxmVotePeriod.class), anyInt())).thenReturn(CompletableFuture.completedFuture(List.of()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.vote()).thenReturn(Optional.of(vote));

        Calls.get(api, "/api/v1/vote/top", Map.of("period", "all-time"));

        verify(vote).top(UxmVotePeriod.ALL_TIME, 10);
    }

    @Test
    void aStandingCarriesTheRungAboveItAndWhetherItIsInReach() {
        UxmRanksQuery ranks = mock(UxmRanksQuery.class);
        when(ranks.standingOf(PLAYER))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(new UxmRankStanding(
                        new UxmRank("citizen", "Citizen", 20, 0L),
                        Optional.of(new UxmRank("vip", "VIP", 30, 5000L)),
                        2))));
        when(ranks.canRankUp(PLAYER)).thenReturn(CompletableFuture.completedFuture(true));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.ranks()).thenReturn(Optional.of(ranks));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/rank");

        JsonObject standing = answer.object().getAsJsonObject("standing");
        assertThat(standing.getAsJsonObject("rank").get("id").getAsString()).isEqualTo("citizen");
        assertThat(standing.getAsJsonObject("next").get("cost").getAsLong()).isEqualTo(5000L);
        assertThat(standing.get("prestige").getAsInt()).isEqualTo(2);
        assertThat(standing.get("at-top").getAsBoolean()).isFalse();
        assertThat(answer.object().get("can-rank-up").getAsBoolean()).isTrue();
    }

    @Test
    void aPlayerWithNoStoredRankAnswersWithANullStandingRatherThanAMissingKey() {
        UxmRanksQuery ranks = mock(UxmRanksQuery.class);
        when(ranks.standingOf(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(ranks.canRankUp(PLAYER)).thenReturn(CompletableFuture.completedFuture(false));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.ranks()).thenReturn(Optional.of(ranks));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/rank");

        assertThat(answer.object().get("standing").isJsonNull()).isTrue();
    }

    @Test
    void theLadderIsListedInTheOrderTheServerKeepsIt() {
        UxmRanksQuery ranks = mock(UxmRanksQuery.class);
        when(ranks.ladder())
                .thenReturn(List.of(new UxmRank("first", "First", 10, 0L), new UxmRank("vip", "VIP", 30, 5000L)));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.ranks()).thenReturn(Optional.of(ranks));

        Calls.Answer answer = Calls.get(api, "/api/v1/ranks");

        assertThat(answer.array()).hasSize(2);
        assertThat(answer.array().get(0).getAsJsonObject().get("id").getAsString())
                .isEqualTo("first");
    }

    @Test
    void aTradeIsReadableForEitherSideAndIsNullForSomebodyNotInOne() {
        UxmTradeQuery trade = mock(UxmTradeQuery.class);
        UUID partner = UUID.randomUUID();
        UxmTrade open = new UxmTrade(UUID.randomUUID(), PLAYER, "Alice", partner, "Bob", true, false);
        when(trade.of(PLAYER)).thenReturn(Optional.of(open));
        when(trade.of(partner)).thenReturn(Optional.empty());
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.trade()).thenReturn(Optional.of(trade));

        JsonObject answer = Calls.get(api, PLAYER_PATH + "/trade").object();

        assertThat(answer.get("partner-name").getAsString()).isEqualTo("Bob");
        assertThat(answer.get("initiator-confirmed").getAsBoolean()).isTrue();
        assertThat(answer.get("both-confirmed").getAsBoolean()).isFalse();
        assertThat(Calls.get(api, "/api/v1/players/" + partner + "/trade")
                        .data()
                        .isJsonNull())
                .isTrue();
    }

    @Test
    void thePartyReportsHowManyVotesAreLeft() {
        UxmVoteQuery vote = mock(UxmVoteQuery.class);
        when(vote.party()).thenReturn(CompletableFuture.completedFuture(new UxmVoteParty(40, 100)));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.vote()).thenReturn(Optional.of(vote));

        assertThat(Calls.get(api, "/api/v1/vote/party")
                        .object()
                        .get("remaining")
                        .getAsInt())
                .isEqualTo(60);
    }

    @Test
    void aPlayersVotesCarryTheirQueuedRewards() {
        UxmVoteQuery vote = mock(UxmVoteQuery.class);
        when(vote.totals(PLAYER)).thenReturn(CompletableFuture.completedFuture(new UxmVoteTotals(12, 1, 4, 9, 3, 6)));
        when(vote.queuedRewards(PLAYER)).thenReturn(CompletableFuture.completedFuture(2));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.vote()).thenReturn(Optional.of(vote));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/votes");

        assertThat(answer.object().getAsJsonObject("totals").get("all-time").getAsLong())
                .isEqualTo(12);
        assertThat(answer.object().get("queued-rewards").getAsInt()).isEqualTo(2);
    }

    @Test
    void theMailboxCarriesItsUnreadCountAndTheSwitchesAroundIt() {
        UxmMessagingQuery messaging = mock(UxmMessagingQuery.class);
        when(messaging.mailbox(PLAYER))
                .thenReturn(CompletableFuture.completedFuture(
                        List.of(new UxmMail(1L, PLAYER, Optional.empty(), "server", "welcome", WHEN, false))));
        when(messaging.unreadMail(PLAYER)).thenReturn(CompletableFuture.completedFuture(1L));
        when(messaging.acceptsMessages(PLAYER)).thenReturn(true);
        when(messaging.isSocialSpying(PLAYER)).thenReturn(false);
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.messaging()).thenReturn(Optional.of(messaging));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/mail");

        assertThat(answer.object().get("unread").getAsLong()).isEqualTo(1);
        assertThat(answer.object().get("accepts-messages").getAsBoolean()).isTrue();
        assertThat(answer.object()
                        .getAsJsonArray("mail")
                        .get(0)
                        .getAsJsonObject()
                        .get("from-player")
                        .getAsBoolean())
                .isFalse();
    }

    @Test
    void affordabilityIsAnsweredByThePluginRatherThanLeftToTheCaller() {
        UxmEconomyQuery economy = mock(UxmEconomyQuery.class);
        when(economy.canAfford(PLAYER, new BigDecimal("250.5"))).thenReturn(CompletableFuture.completedFuture(true));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.economy()).thenReturn(Optional.of(economy));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/balance/afford", Map.of("amount", "250.5"));

        assertThat(answer.object().get("can-afford").getAsBoolean()).isTrue();
        assertThat(Calls.get(api, PLAYER_PATH + "/balance/afford").status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void regionSupportIsAskedAboutSeparatelyFromTheRegionsThemselves() {
        UxmRegionsQuery regions = mock(UxmRegionsQuery.class);
        when(regions.available()).thenReturn(false);
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.regions()).thenReturn(Optional.of(regions));

        // No provider installed is not the same as a world nobody protected, and the empty list cannot say which.
        assertThat(Calls.get(api, "/api/v1/regions").object().get("available").getAsBoolean())
                .isFalse();
    }

    @Test
    void whetherAViewerCanSeeAVanishedPlayerIsAskedWithTheViewer() {
        UUID viewer = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UxmVanishQuery vanish = mock(UxmVanishQuery.class);
        when(vanish.isVanished(PLAYER)).thenReturn(true);
        when(vanish.levelOf(PLAYER)).thenReturn(2);
        when(vanish.canSee(viewer, PLAYER)).thenReturn(false);
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.vanish()).thenReturn(Optional.of(vanish));

        assertThat(Calls.get(api, PLAYER_PATH + "/vanish").object().has("visible-to-viewer"))
                .isFalse();
        assertThat(Calls.get(api, PLAYER_PATH + "/vanish", Map.of("viewer", viewer.toString()))
                        .object()
                        .get("visible-to-viewer")
                        .getAsBoolean())
                .isFalse();
    }

    private static UxmHome home(int slot, String label) {
        return new UxmHome(PLAYER, slot, SOMEWHERE, Optional.of(label), Optional.empty(), false, WHEN, WHEN);
    }

    private static UxmWarp warp(String name) {
        return new UxmWarp(
                name,
                SOMEWHERE,
                PLAYER,
                "steve",
                WHEN,
                Optional.empty(),
                Optional.empty(),
                12L,
                false,
                false,
                Optional.empty(),
                Optional.empty());
    }

    private static UxmKit kit(String id) {
        return new UxmKit(
                id,
                "Miner",
                Duration.ofHours(2),
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                9,
                false,
                Optional.empty());
    }

    private static UxmSanction ban() {
        return new UxmSanction(
                UxmSanctionKind.BAN,
                PLAYER,
                new UxmIssuer(Optional.empty(), "Console"),
                Optional.of("griefing"),
                WHEN,
                Optional.empty());
    }
}
