package com.uxplima.uxmessentials.rest.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.action.UxmEconomyActions;
import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmHomeActions;
import com.uxplima.uxmessentials.api.action.UxmKitActions;
import com.uxplima.uxmessentials.api.action.UxmMessagingActions;
import com.uxplima.uxmessentials.api.action.UxmModerationActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmPlayerStateActions;
import com.uxplima.uxmessentials.api.action.UxmPresenceActions;
import com.uxplima.uxmessentials.api.action.UxmRanksActions;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.action.UxmTeleportActions;
import com.uxplima.uxmessentials.api.action.UxmVoteActions;
import com.uxplima.uxmessentials.api.action.UxmWarpActions;
import com.uxplima.uxmessentials.api.action.UxmWorldsActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.view.UxmGameMode;
import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmSanctionKind;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import org.junit.jupiter.api.Test;

/**
 * What the write routes do with a body, and what they answer.
 *
 * <p>Two things are being pinned here. One is the translation: a path, a JSON body and the published action the two
 * of them add up to, including the choices a route makes on the caller's behalf (a duration meaning temporary, a
 * missing {@code save} meaning save). The other is the shape of the answer, where a refusal the server understood
 * is a {@code 200} carrying the plugin's own code rather than an HTTP error, because a consumer should branch on
 * the same string over HTTP as in process.
 */
class WriteRoutesTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
    private static final String PLAYER_PATH = "/api/v1/players/" + PLAYER;
    private static final Instant WHEN = Instant.parse("2026-01-02T03:04:05Z");
    private static final UxmLocation SOMEWHERE = new UxmLocation("world", 1.5, 64, -2.5, 90f, 0f);
    private static final UxmLocation FACING_NOWHERE = new UxmLocation("world", 1.5, 64, -2.5, 0f, 0f);
    private static final String PLACE = """
            {"location":{"world":"world","x":1.5,"y":64,"z":-2.5,"yaw":90}}""";

    @Test
    void aDepositAnswersWithTheBalanceItLeftBehind() {
        UxmEconomyActions economy = mock(UxmEconomyActions.class);
        when(economy.deposit(PLAYER, new BigDecimal("250.5")))
                .thenReturn(CompletableFuture.completedFuture(
                        UxmResult.ok(new UxmMoney("coins", new BigDecimal("1250.50")))));

        Calls.Answer answer =
                Calls.post(mock(UxmEssentialsApi.class), economy(economy), PLAYER_PATH + "/balance/deposit", """
                {"amount":250.5}""");

        assertThat(answer.status()).isEqualTo(HttpStatus.OK);
        assertThat(answer.ok()).isTrue();
        assertThat(answer.object().get("amount").getAsBigDecimal()).isEqualByComparingTo("1250.50");
    }

    @Test
    void anAmountInANamedCurrencyGoesToThatCurrency() {
        UxmEconomyActions economy = mock(UxmEconomyActions.class);
        when(economy.withdraw(PLAYER, new BigDecimal("5"), "gems"))
                .thenReturn(CompletableFuture.completedFuture(UxmResult.ok(new UxmMoney("gems", BigDecimal.ONE))));

        Calls.post(mock(UxmEssentialsApi.class), economy(economy), PLAYER_PATH + "/balance/withdraw", """
                {"amount":5,"currency":"gems"}""");

        verify(economy).withdraw(PLAYER, new BigDecimal("5"), "gems");
    }

    @Test
    void aRefusalTheServerUnderstoodIsAnAnswerRatherThanAnError() {
        UxmEconomyActions economy = mock(UxmEconomyActions.class);
        when(economy.withdraw(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        UxmResult.failed(UxmFailure.of(UxmFailure.INSUFFICIENT_FUNDS, "not enough coins"))));

        Calls.Answer answer =
                Calls.post(mock(UxmEssentialsApi.class), economy(economy), PLAYER_PATH + "/balance/withdraw", """
                {"amount":9000}""");

        assertThat(answer.status()).isEqualTo(HttpStatus.OK);
        assertThat(answer.ok()).isFalse();
        assertThat(answer.code()).isEqualTo(UxmFailure.INSUFFICIENT_FUNDS);
        assertThat(answer.message()).isEqualTo("not enough coins");
    }

    @Test
    void aTransferNamesBothEnds() {
        UxmEconomyActions economy = mock(UxmEconomyActions.class);
        when(economy.transfer(PLAYER, OTHER, new BigDecimal("10")))
                .thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.Answer answer = Calls.post(
                mock(UxmEssentialsApi.class),
                economy(economy),
                "/api/v1/economy/transfer",
                "{\"from\":\"" + PLAYER + "\",\"to\":\"" + OTHER + "\",\"amount\":10}");

        assertThat(answer.ok()).isTrue();
        verify(economy).transfer(PLAYER, OTHER, new BigDecimal("10"));
    }

    @Test
    void aFieldTheBodyLeftOutIsABadRequestThatNamesIt() {
        Calls.Answer answer = Calls.post(
                mock(UxmEssentialsApi.class), economy(mock(UxmEconomyActions.class)), PLAYER_PATH + "/balance/deposit");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.message()).contains("amount");
    }

    @Test
    void aWriteToAModuleThatIsOffIsAServiceUnavailable() {
        Calls.Answer answer =
                Calls.post(mock(UxmEssentialsApi.class), mock(UxmActions.class), PLAYER_PATH + "/balance/deposit", """
                {"amount":1}""");

        assertThat(answer.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(answer.code()).isEqualTo("module-off");
        assertThat(answer.message()).contains("economy");
    }

    @Test
    void aHomeSlotInThePathCountsFromOneAndReachesTheApiCountingFromZero() {
        UxmHomeActions homes = mock(UxmHomeActions.class);
        when(homes.set(eq(PLAYER), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(UxmResult.ok(
                        new UxmHome(PLAYER, 0, SOMEWHERE, Optional.of("base"), Optional.empty(), false, WHEN, WHEN))));

        Calls.Answer answer =
                Calls.post(mock(UxmEssentialsApi.class), homes(homes), PLAYER_PATH + "/homes/1/set", PLACE);

        assertThat(answer.ok()).isTrue();
        verify(homes).set(PLAYER, 0, SOMEWHERE);
    }

    /** A body with coordinates and no facing gets a facing of zero rather than a refusal. */
    @Test
    void aPlaceWithNoFacingFacesNorth() {
        UxmHomeActions homes = mock(UxmHomeActions.class);
        when(homes.set(eq(PLAYER), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(UxmResult.ok(new UxmHome(
                        PLAYER, 0, FACING_NOWHERE, Optional.of("base"), Optional.empty(), false, WHEN, WHEN))));

        Calls.post(mock(UxmEssentialsApi.class), homes(homes), PLAYER_PATH + "/homes/1/set", """
                {"location":{"world":"world","x":1.5,"y":64,"z":-2.5}}""");

        verify(homes).set(PLAYER, 0, FACING_NOWHERE);
    }

    @Test
    void aSlotBelowOneIsABadRequestRatherThanASlotBefore() {
        Calls.Answer answer = Calls.post(
                mock(UxmEssentialsApi.class), homes(mock(UxmHomeActions.class)), PLAYER_PATH + "/homes/0/delete");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.message()).contains("count from one");
    }

    @Test
    void aLocationMissingItsWorldIsABadRequestRatherThanAHomeInTheVoid() {
        Calls.Answer answer = Calls.post(
                mock(UxmEssentialsApi.class), homes(mock(UxmHomeActions.class)), PLAYER_PATH + "/homes/1/set", """
                {"location":{"x":1,"y":2,"z":3}}""");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.message()).contains("world");
    }

    @Test
    void aBanWithADurationIsATemporaryOne() {
        UxmModerationActions moderation = mock(UxmModerationActions.class);
        when(moderation.tempBan(PLAYER, Duration.ofHours(1), "griefing"))
                .thenReturn(CompletableFuture.completedFuture(UxmResult.ok(sanction())));

        Calls.Answer answer =
                Calls.post(mock(UxmEssentialsApi.class), moderation(moderation), PLAYER_PATH + "/ban", """
                {"reason":"griefing","duration-seconds":3600}""");

        assertThat(answer.ok()).isTrue();
        verify(moderation).tempBan(PLAYER, Duration.ofHours(1), "griefing");
    }

    @Test
    void aBanWithoutADurationIsAPermanentOne() {
        UxmModerationActions moderation = mock(UxmModerationActions.class);
        when(moderation.ban(PLAYER, "griefing"))
                .thenReturn(CompletableFuture.completedFuture(UxmResult.ok(sanction())));

        Calls.post(mock(UxmEssentialsApi.class), moderation(moderation), PLAYER_PATH + "/ban", """
                {"reason":"griefing"}""");

        verify(moderation).ban(PLAYER, "griefing");
    }

    @Test
    void aBanWithNoReasonAtAllUsesTheOverloadThatTakesNone() {
        UxmModerationActions moderation = mock(UxmModerationActions.class);
        when(moderation.ban(PLAYER)).thenReturn(CompletableFuture.completedFuture(UxmResult.ok(sanction())));

        Calls.post(mock(UxmEssentialsApi.class), moderation(moderation), PLAYER_PATH + "/ban");

        verify(moderation).ban(PLAYER);
    }

    @Test
    void aWarnHasToSayWhy() {
        Calls.Answer answer = Calls.post(
                mock(UxmEssentialsApi.class), moderation(mock(UxmModerationActions.class)), PLAYER_PATH + "/warn");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.message()).contains("reason");
    }

    @Test
    void anUnbanHandsBackNothingButSaysItWorked() {
        UxmModerationActions moderation = mock(UxmModerationActions.class);
        when(moderation.unban(PLAYER)).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.Answer answer = Calls.post(mock(UxmEssentialsApi.class), moderation(moderation), PLAYER_PATH + "/unban");

        assertThat(answer.ok()).isTrue();
        assertThat(answer.envelope().has("data")).isFalse();
    }

    @Test
    void aGameModeIsReadHoweverItWasCapitalised() {
        UxmPlayerStateActions state = mock(UxmPlayerStateActions.class);
        when(state.setGameMode(PLAYER, UxmGameMode.CREATIVE))
                .thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), playerState(state), PLAYER_PATH + "/state/gamemode", """
                {"mode":"Creative"}""");

        verify(state).setGameMode(PLAYER, UxmGameMode.CREATIVE);
    }

    @Test
    void aGameModeNobodyHasIsABadRequestListingTheOnesThatExist() {
        Calls.Answer answer = Calls.post(
                mock(UxmEssentialsApi.class),
                playerState(mock(UxmPlayerStateActions.class)),
                PLAYER_PATH + "/state/gamemode",
                """
                {"mode":"hardcore"}""");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.message()).contains("survival");
    }

    @Test
    void aSwitchWithNoBodyTurnsTheThingOn() {
        UxmPlayerStateActions state = mock(UxmPlayerStateActions.class);
        when(state.setGodMode(PLAYER, true)).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), playerState(state), PLAYER_PATH + "/state/god");

        verify(state).setGodMode(PLAYER, true);
    }

    @Test
    void unloadingAWorldSavesItUnlessToldOtherwise() {
        UxmWorldsActions worlds = mock(UxmWorldsActions.class);
        when(worlds.unload(any(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), worlds(worlds), "/api/v1/worlds/nether/unload");

        verify(worlds).unload("nether", true);
    }

    @Test
    void theTokenLabelIsWhatTheWriteIsAttributedTo() {
        UxmModerationActions moderation = mock(UxmModerationActions.class);
        when(moderation.unban(PLAYER)).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));
        UxmActions actions = moderation(moderation);
        StringBuilder asked = new StringBuilder();

        Calls.postThrough(
                mock(UxmEssentialsApi.class),
                caller -> {
                    asked.append(caller);
                    return actions;
                },
                PLAYER_PATH + "/unban");

        assertThat(asked.toString()).isEqualTo(Calls.CALLER);
    }

    @Test
    void aWarpIsMadeWhereTheBodySays() {
        UxmWarpActions warps = mock(UxmWarpActions.class);
        when(warps.create("spawn", SOMEWHERE))
                .thenReturn(CompletableFuture.completedFuture(UxmResult.failed(
                        UxmFailure.of(UxmFailure.ALREADY_EXISTS, "there is already a warp called spawn"))));

        Calls.Answer answer =
                Calls.post(mock(UxmEssentialsApi.class), warps(warps), "/api/v1/warps/spawn/create", PLACE);

        assertThat(answer.ok()).isFalse();
        assertThat(answer.code()).isEqualTo(UxmFailure.ALREADY_EXISTS);
        verify(warps).create("spawn", SOMEWHERE);
    }

    @Test
    void givingAKitAndClaimingOneAreDifferentActions() {
        UxmKitActions kits = mock(UxmKitActions.class);
        when(kits.give(any(), any())).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));
        when(kits.claim(any(), any())).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), kits(kits), PLAYER_PATH + "/kits/miner/give");
        Calls.post(mock(UxmEssentialsApi.class), kits(kits), PLAYER_PATH + "/kits/miner/claim");

        verify(kits).give(PLAYER, "miner");
        verify(kits).claim(PLAYER, "miner");
    }

    @Test
    void aReasonForBeingAwayMeansAway() {
        UxmPresenceActions presence = mock(UxmPresenceActions.class);
        when(presence.setAfk(PLAYER, "dinner")).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), presence(presence), PLAYER_PATH + "/presence/afk", """
                {"reason":"dinner"}""");

        verify(presence).setAfk(PLAYER, "dinner");
    }

    @Test
    void comingBackIsSaidWithAwayFalse() {
        UxmPresenceActions presence = mock(UxmPresenceActions.class);
        when(presence.setAfk(PLAYER, false)).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), presence(presence), PLAYER_PATH + "/presence/afk", """
                {"away":false}""");

        verify(presence).setAfk(PLAYER, false);
    }

    @Test
    void oneCallToTheVoteRouteMeansOneVote() {
        UxmVoteActions vote = mock(UxmVoteActions.class);
        when(vote.giveVote(PLAYER, 1)).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), vote(vote), PLAYER_PATH + "/votes");

        verify(vote).giveVote(PLAYER, 1);
    }

    @Test
    void theThreeRankVerbsEachReachTheirOwnAction() {
        UxmRanksActions ranks = mock(UxmRanksActions.class);
        when(ranks.rankUp(PLAYER)).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));
        when(ranks.setRank(PLAYER, "vip")).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));
        when(ranks.prestige(PLAYER)).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), ranks(ranks), PLAYER_PATH + "/rank/rankup");
        Calls.post(mock(UxmEssentialsApi.class), ranks(ranks), PLAYER_PATH + "/rank/set", """
                {"rank":"vip"}""");
        Calls.post(mock(UxmEssentialsApi.class), ranks(ranks), PLAYER_PATH + "/rank/prestige");

        verify(ranks).rankUp(PLAYER);
        verify(ranks).setRank(PLAYER, "vip");
        verify(ranks).prestige(PLAYER);
    }

    @Test
    void aSetWithNoRankNamedSaysWhichFieldIsMissing() {
        UxmRanksActions ranks = mock(UxmRanksActions.class);

        Calls.Answer answer = Calls.post(mock(UxmEssentialsApi.class), ranks(ranks), PLAYER_PATH + "/rank/set");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.message()).contains("rank");
    }

    @Test
    void mailWithNoSenderComesFromTheServer() {
        UxmMessagingActions messaging = mock(UxmMessagingActions.class);
        when(messaging.sendMail(PLAYER, "your appeal was accepted"))
                .thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(mock(UxmEssentialsApi.class), messaging(messaging), PLAYER_PATH + "/mail", """
                {"body":"your appeal was accepted"}""");

        verify(messaging).sendMail(PLAYER, "your appeal was accepted");
    }

    @Test
    void mailWithASenderComesFromThatPlayer() {
        UxmMessagingActions messaging = mock(UxmMessagingActions.class);
        when(messaging.sendMail(OTHER, PLAYER, "see you tonight"))
                .thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.post(
                mock(UxmEssentialsApi.class),
                messaging(messaging),
                PLAYER_PATH + "/mail",
                "{\"from\":\"" + OTHER + "\",\"body\":\"see you tonight\"}");

        verify(messaging).sendMail(OTHER, PLAYER, "see you tonight");
    }

    @Test
    void aTeleportGoesWhereTheBodySays() {
        UxmTeleportActions teleport = mock(UxmTeleportActions.class);
        when(teleport.teleport(PLAYER, SOMEWHERE)).thenReturn(CompletableFuture.completedFuture(UxmOutcome.ok()));

        Calls.Answer answer =
                Calls.post(mock(UxmEssentialsApi.class), teleport(teleport), PLAYER_PATH + "/teleport", PLACE);

        assertThat(answer.ok()).isTrue();
        verify(teleport).teleport(PLAYER, SOMEWHERE);
    }

    @Test
    void aPlayerWithNowhereToGoBackToIsARefusalRatherThanASuccess() {
        UxmTeleportActions teleport = mock(UxmTeleportActions.class);
        when(teleport.back(PLAYER))
                .thenReturn(CompletableFuture.completedFuture(
                        UxmOutcome.failed(UxmFailure.NOT_FOUND, "nowhere to go back to")));

        Calls.Answer answer = Calls.post(mock(UxmEssentialsApi.class), teleport(teleport), PLAYER_PATH + "/back");

        assertThat(answer.status()).isEqualTo(HttpStatus.OK);
        assertThat(answer.ok()).isFalse();
        assertThat(answer.code()).isEqualTo(UxmFailure.NOT_FOUND);
    }

    private static UxmSanction sanction() {
        return new UxmSanction(
                UxmSanctionKind.BAN,
                PLAYER,
                new UxmIssuer(Optional.empty(), "Console"),
                Optional.of("griefing"),
                WHEN,
                Optional.empty());
    }

    @Test
    void aPunishmentIsAnnouncedUnlessTheBodyAsksForQuiet() {
        UxmModerationActions loud = mock(UxmModerationActions.class);
        UxmModerationActions quiet = mock(UxmModerationActions.class);
        when(loud.silently()).thenReturn(quiet);
        when(loud.ban(PLAYER)).thenReturn(CompletableFuture.completedFuture(UxmResult.ok(sanction())));
        when(quiet.ban(PLAYER)).thenReturn(CompletableFuture.completedFuture(UxmResult.ok(sanction())));

        Calls.post(mock(UxmEssentialsApi.class), moderation(loud), PLAYER_PATH + "/ban");
        verify(loud).ban(PLAYER);

        Calls.post(mock(UxmEssentialsApi.class), moderation(loud), PLAYER_PATH + "/ban", """
                {"silent":true}""");
        verify(quiet).ban(PLAYER);
    }

    private static UxmActions economy(UxmEconomyActions economy) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.economy()).thenReturn(Optional.of(economy));
        return actions;
    }

    private static UxmActions homes(UxmHomeActions homes) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.homes()).thenReturn(Optional.of(homes));
        return actions;
    }

    private static UxmActions moderation(UxmModerationActions moderation) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.moderation()).thenReturn(Optional.of(moderation));
        return actions;
    }

    private static UxmActions playerState(UxmPlayerStateActions state) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.playerState()).thenReturn(Optional.of(state));
        return actions;
    }

    private static UxmActions worlds(UxmWorldsActions worlds) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.worlds()).thenReturn(Optional.of(worlds));
        return actions;
    }

    private static UxmActions warps(UxmWarpActions warps) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.warps()).thenReturn(Optional.of(warps));
        return actions;
    }

    private static UxmActions kits(UxmKitActions kits) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.kits()).thenReturn(Optional.of(kits));
        return actions;
    }

    private static UxmActions presence(UxmPresenceActions presence) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.presence()).thenReturn(Optional.of(presence));
        return actions;
    }

    private static UxmActions vote(UxmVoteActions vote) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.vote()).thenReturn(Optional.of(vote));
        return actions;
    }

    private static UxmActions ranks(UxmRanksActions ranks) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.ranks()).thenReturn(Optional.of(ranks));
        return actions;
    }

    private static UxmActions messaging(UxmMessagingActions messaging) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.messaging()).thenReturn(Optional.of(messaging));
        return actions;
    }

    private static UxmActions teleport(UxmTeleportActions teleport) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.teleport()).thenReturn(Optional.of(teleport));
        return actions;
    }
}
