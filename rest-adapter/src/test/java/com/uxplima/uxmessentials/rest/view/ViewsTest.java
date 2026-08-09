package com.uxplima.uxmessentials.rest.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.view.UxmBackCause;
import com.uxplima.uxmessentials.api.view.UxmBackPoint;
import com.uxplima.uxmessentials.api.view.UxmBaltopEntry;
import com.uxplima.uxmessentials.api.view.UxmIgnore;
import com.uxplima.uxmessentials.api.view.UxmIgnoreScope;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.api.view.UxmPlayerState;
import com.uxplima.uxmessentials.api.view.UxmPlaytime;
import com.uxplima.uxmessentials.api.view.UxmSanctionAction;
import com.uxplima.uxmessentials.api.view.UxmSanctionRecord;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequest;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequestDirection;
import com.uxplima.uxmessentials.api.view.UxmVault;
import com.uxplima.uxmessentials.api.view.UxmVoteRank;
import org.junit.jupiter.api.Test;

/** The three conventions every rendered view follows, and the types no route test happens to cover. */
class ViewsTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID OTHER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant WHEN = Instant.parse("2026-03-04T05:06:07Z");

    @Test
    void aTimeIsWrittenAsAnIsoInstant() {
        JsonObject point = Views.backPoint(
                        new UxmBackPoint(new UxmLocation("world", 0, 64, 0), UxmBackCause.DEATH, WHEN))
                .getAsJsonObject();

        assertThat(point.get("captured-at").getAsString()).isEqualTo("2026-03-04T05:06:07Z");
        assertThat(point.get("cause").getAsString()).isEqualTo("DEATH");
    }

    @Test
    void aDurationIsWholeSecondsUnderANameThatSaysSo() {
        JsonObject played = Views.playtime(new UxmPlaytime(
                        Duration.ofMinutes(90),
                        Duration.ZERO,
                        Duration.ofHours(10),
                        Duration.ZERO,
                        Duration.ofHours(40),
                        Duration.ZERO,
                        Duration.ofHours(100),
                        Duration.ofHours(3)))
                .getAsJsonObject();

        assertThat(played.get("today-active-seconds").getAsLong()).isEqualTo(5400);
        assertThat(played.get("total-afk-seconds").getAsLong()).isEqualTo(10800);
    }

    @Test
    void anAbsentValueIsPresentAndNullRatherThanMissing() {
        JsonObject vault = Views.vault(new UxmVault(PLAYER, 2, Optional.empty(), Optional.empty()))
                .getAsJsonObject();

        assertThat(vault.has("display-name")).isTrue();
        assertThat(vault.get("display-name").isJsonNull()).isTrue();
        assertThat(vault.get("label").getAsString()).isEqualTo("2");
    }

    @Test
    void moneyKeepsEveryDigitItArrivedWith() {
        JsonObject money = Views.money(new UxmMoney("coins", new BigDecimal("0.100000000000000000001")))
                .getAsJsonObject();

        assertThat(money.get("amount").getAsBigDecimal()).isEqualTo(new BigDecimal("0.100000000000000000001"));
    }

    @Test
    void aLocationCarriesTheWholeStance() {
        JsonObject location = Views.location(new UxmLocation("nether", 1.5, 70, -3.25, 180f, -45f))
                .getAsJsonObject();

        assertThat(location.get("world").getAsString()).isEqualTo("nether");
        assertThat(location.get("z").getAsDouble()).isEqualTo(-3.25);
        assertThat(location.get("pitch").getAsFloat()).isEqualTo(-45f);
    }

    @Test
    void theConsoleIsAnIssuerWithNoUuidAndSaysSo() {
        JsonObject issuer =
                Views.issuer(new UxmIssuer(Optional.empty(), "Console")).getAsJsonObject();

        assertThat(issuer.get("uuid").isJsonNull()).isTrue();
        assertThat(issuer.get("console").getAsBoolean()).isTrue();
    }

    @Test
    void aHistoryRowNamesTheActionAndWhoTookIt() {
        JsonObject row = Views.sanctionRecord(new UxmSanctionRecord(
                        UxmSanctionAction.UNBAN,
                        PLAYER,
                        new UxmIssuer(Optional.of(OTHER), "admin"),
                        Optional.of("appeal accepted"),
                        WHEN,
                        Optional.empty()))
                .getAsJsonObject();

        assertThat(row.get("action").getAsString()).isEqualTo("UNBAN");
        assertThat(row.getAsJsonObject("actor").get("name").getAsString()).isEqualTo("admin");
        assertThat(row.get("reason").getAsString()).isEqualTo("appeal accepted");
        assertThat(row.get("expiry").isJsonNull()).isTrue();
    }

    @Test
    void aTeleportRequestSaysWhoMovesAndWhoStays() {
        JsonObject request = Views.teleportRequest(new UxmTeleportRequest(
                        PLAYER, "steve", OTHER, "alex", UxmTeleportRequestDirection.TO_TARGET, WHEN))
                .getAsJsonObject();

        assertThat(request.get("mover-id").getAsString()).isEqualTo(PLAYER.toString());
        assertThat(request.get("anchor-id").getAsString()).isEqualTo(OTHER.toString());
    }

    @Test
    void aLeaderboardRowCarriesItsRankAndName() {
        JsonObject entry = Views.baltopEntry(
                        new UxmBaltopEntry(1, PLAYER, "steve", new UxmMoney("coins", BigDecimal.TEN)))
                .getAsJsonObject();

        assertThat(entry.get("rank").getAsInt()).isEqualTo(1);
        assertThat(entry.get("player-name").getAsString()).isEqualTo("steve");
        assertThat(entry.getAsJsonObject("balance").get("amount").getAsInt()).isEqualTo(10);
    }

    @Test
    void aVoteRankAndAnIgnoreAreRenderedWhole() {
        assertThat(Views.voteRank(new UxmVoteRank(2, PLAYER, "steve", 40L))
                        .getAsJsonObject()
                        .get("votes")
                        .getAsLong())
                .isEqualTo(40L);
        assertThat(Views.ignore(new UxmIgnore(OTHER, UxmIgnoreScope.ALL))
                        .getAsJsonObject()
                        .get("scope")
                        .getAsString())
                .isEqualTo("ALL");
    }

    @Test
    void aPlayerStateReportsTheSwitchesAndTheSpeeds() {
        JsonObject state = Views.playerState(new UxmPlayerState(PLAYER, true, false, Optional.empty(), 0.2f, 0.1f))
                .getAsJsonObject();

        assertThat(state.get("god-mode").getAsBoolean()).isTrue();
        assertThat(state.get("game-mode").isJsonNull()).isTrue();
        assertThat(state.get("walk-speed").getAsFloat()).isEqualTo(0.2f);
    }

    @Test
    void aListOfStringsIsAnArrayOfStrings() {
        assertThat(Views.strings(List.of("coins", "gems"))).hasSize(2);
    }
}
