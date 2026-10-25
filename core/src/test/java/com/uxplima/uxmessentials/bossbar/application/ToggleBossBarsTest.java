package com.uxplima.uxmessentials.bossbar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.domain.event.BossBarVisibilityToggled;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class ToggleBossBarsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    private final FakeStore store = new FakeStore();
    private final CapturingSink sink = new CapturingSink();
    private final RecordingEvents events = new RecordingEvents();
    private final ToggleBossBars toggle = new ToggleBossBars(store, new Notifier(new EchoMessages(), sink), events);

    @Test
    void aToggleHidesShownBarsAndSaysSo() {
        assertThat(toggle.toggle(ALICE)).isTrue();

        assertThat(store.hidden(ALICE)).isTrue();
        assertThat(sink.lastDelivered).isEqualTo("bossbar.hidden");
        assertThat(events.published).containsExactly(new BossBarVisibilityToggled(ALICE, true));
    }

    @Test
    void aSecondToggleShowsThemAgain() {
        toggle.toggle(ALICE);

        assertThat(toggle.toggle(ALICE)).isFalse();
        assertThat(sink.lastDelivered).isEqualTo("bossbar.shown");
    }

    @Test
    void settingTheStateTheyAreInKeepsItAndStillSaysSo() {
        assertThat(toggle.set(ALICE, false)).isFalse();

        assertThat(store.hidden(ALICE)).isFalse();
        assertThat(sink.lastDelivered).isEqualTo("bossbar.shown");
        // Nothing changed, so another plugin listening for a change hears nothing.
        assertThat(events.published).isEmpty();
    }

    @Test
    void settingHiddenHidesThem() {
        assertThat(toggle.set(ALICE, true)).isTrue();

        assertThat(store.hidden(ALICE)).isTrue();
        assertThat(sink.lastDelivered).isEqualTo("bossbar.hidden");
    }

    private static final class FakeStore implements BossBarVisibilityStore {
        private final Map<UUID, Boolean> hidden = new ConcurrentHashMap<>();

        @Override
        public boolean hidden(PlayerRef who) {
            return hidden.getOrDefault(who.uuid(), false);
        }

        @Override
        public void setHidden(PlayerRef who, boolean value) {
            hidden.put(who.uuid(), value);
        }
    }

    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    private static final class CapturingSink implements MessageSink {
        private String lastDelivered = "";

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            this.lastDelivered = renderedText;
        }
    }

    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
