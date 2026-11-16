package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerTempbanEvent;
import com.uxplima.uxmessentials.moderation.adapter.outbound.api.ModerationEventBridges;
import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerTempbanned;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/** The published ban event says whether the ban is permanent, so a listener need not read the stored expiry. */
class TempbanEventPermanenceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final PlayerRef STAFF =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final PlayerRef TARGET =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");

    private final EventBridgeRegistry registry = new EventBridgeRegistry();

    TempbanEventPermanenceTest() {
        ModerationEventBridges.register(registry, (viewer, key, placeholders) -> key.key());
    }

    private UxmPlayerTempbanEvent published(Instant until) {
        TempbanState.Active ban =
                (TempbanState.Active) TempbanState.active(until, Issuer.of(STAFF), Optional.empty(), NOW);
        PlayerTempbanned fact = new PlayerTempbanned(TARGET, ban, NOW);
        EventBridgeRegistry.Entry<PlayerTempbanned> entry = Objects.requireNonNull(registry.lookup(fact));
        return (UxmPlayerTempbanEvent) entry.mapper().apply(fact);
    }

    @Test
    void aPermanentBanIsPublishedAsPermanent() {
        UxmPlayerTempbanEvent event = published(NOW.plus(Ban.PERMANENT_SPAN));

        assertThat(event.isPermanent()).isTrue();
    }

    @Test
    void aTimedBanIsPublishedWithItsEnd() {
        Instant end = NOW.plus(Duration.ofHours(2));

        UxmPlayerTempbanEvent event = published(end);

        assertThat(event.isPermanent()).isFalse();
        assertThat(event.getUntil()).isEqualTo(end);
    }
}
