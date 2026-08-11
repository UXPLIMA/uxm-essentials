package com.uxplima.uxmessentials.rest.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletCreditEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerMuteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldLoadEvent;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.junit.jupiter.api.Test;

class EventJsonTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant WHEN = Instant.parse("2026-01-02T03:04:05Z");
    private static final UxmLocation SOMEWHERE = new UxmLocation("world", 1.5, 64, -2.5, 90f, 0f);

    @Test
    void anEventCarriesItsOwnFieldsAndThePlayerItIsAbout() {
        JsonObject json = EventJson.of(new UxmHomeCreateEvent(PLAYER, "steve", 0, SOMEWHERE));

        assertThat(json.get("player-id").getAsString()).isEqualTo(PLAYER.toString());
        assertThat(json.get("player-name").getAsString()).isEqualTo("steve");
        assertThat(json.get("slot").getAsInt()).isZero();
        assertThat(json.get("slot-number").getAsInt()).isEqualTo(1);
        assertThat(json.getAsJsonObject("location").get("world").getAsString()).isEqualTo("world");
    }

    /** Money and locations look the same over a socket as they do from a {@code GET}, which is the point of Views. */
    @Test
    void aValueTheReadsAlreadyRenderIsRenderedTheSameWay() {
        JsonObject json = EventJson.of(new UxmWalletCreditEvent(
                PLAYER,
                "steve",
                new UxmMoney("coins", new BigDecimal("25.00")),
                new UxmMoney("coins", new BigDecimal("125.00")),
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                WHEN));

        JsonObject amount = json.getAsJsonObject("amount");
        assertThat(amount.get("currency").getAsString()).isEqualTo("coins");
        assertThat(amount.get("amount").getAsBigDecimal()).isEqualByComparingTo("25.00");
        assertThat(json.get("occurred-at").getAsString()).isEqualTo("2026-01-02T03:04:05Z");
    }

    /** A value that is not there is present and null, so the shape never depends on the data. */
    @Test
    void anAbsentValueIsNullRatherThanAMissingKey() {
        JsonObject json = EventJson.of(new UxmPlayerMuteEvent(
                PLAYER, "steve", new UxmIssuer(Optional.empty(), "Console"), Optional.empty(), Optional.empty(), WHEN));

        assertThat(json.has("until")).isTrue();
        assertThat(json.get("until").isJsonNull()).isTrue();
        assertThat(json.getAsJsonObject("issuer").get("console").getAsBoolean()).isTrue();
    }

    @Test
    void anEventAboutNoPlayerCarriesOnlyItsOwnFields() {
        JsonObject json = EventJson.of(new UxmWorldLoadEvent("nether"));

        assertThat(json.keySet()).containsExactly("world-name");
    }

    /** A handle to a player cannot be written down, and the id beside it already says who. */
    @Test
    void aLivePlayerHandleIsNotAField() {
        assertThat(EventJson.fieldsOf(UxmHomeCreateEvent.class))
                .extracting(EventJson::nameOf)
                .doesNotContain("player", "offline-player");
    }
}
