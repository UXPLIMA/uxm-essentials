package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.moderation.domain.event.JailLocationRemoved;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.FakeJailLocationStore;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes.RecordingEvents;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * {@code /jail del}: removes a stored jail. A stored name is deleted, publishes {@link JailLocationRemoved},
 * audits a {@code jail_location_delete} line and confirms with {@code DELJAIL_DELETED}; an unknown name is a
 * not-found notice that publishes nothing and audits nothing.
 */
class DelJailTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "op");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = new Position(WORLD, 10, 64, 20, 0f, 0f);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);

    @Test
    void deletesAStoredJailAndPublishesRemoval() {
        FakeJailLocationStore store = new FakeJailLocationStore();
        store.save(StoredJail.of("spawnjail", AT));
        RecordingEvents events = new RecordingEvents();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        DelJail delJail = new DelJail(store, ModerationFakes.notifier(), audit, events, CLOCK);

        delJail.delete(ACTOR, "SpawnJail");

        assertThat(store.exists("spawnjail")).isFalse();
        assertThat(events.events).singleElement().isInstanceOf(JailLocationRemoved.class);
        assertThat(((JailLocationRemoved) events.events.get(0)).jail()).isEqualTo("spawnjail");
        assertThat(audit.lines).singleElement().satisfies(line -> {
            assertThat(line.event()).isEqualTo("jail_location_delete");
            assertThat(line.ok()).isTrue();
        });
    }

    @Test
    void anUnknownNameReportsNotFoundAndPublishesNothing() {
        FakeJailLocationStore store = new FakeJailLocationStore();
        RecordingEvents events = new RecordingEvents();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        DelJail delJail = new DelJail(store, ModerationFakes.notifier(), audit, events, CLOCK);

        delJail.delete(ACTOR, "missing");

        assertThat(events.events).isEmpty();
        assertThat(audit.lines).isEmpty();
    }
}
