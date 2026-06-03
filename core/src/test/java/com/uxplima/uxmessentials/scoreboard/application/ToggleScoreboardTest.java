package com.uxplima.uxmessentials.scoreboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.event.ScoreboardVisibilityToggled;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class ToggleScoreboardTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void firstToggleHidesAndConfirmsHidden() {
        FakeStore store = new FakeStore();
        CapturingSink sink = new CapturingSink();
        RecordingEvents events = new RecordingEvents();
        ToggleScoreboard toggle = new ToggleScoreboard(store, new ScoreboardNotifier(new EchoMessages(), sink), events);

        boolean hidden = toggle.toggle(ALICE);

        assertThat(hidden).isTrue();
        assertThat(store.hidden(ALICE)).isTrue();
        assertThat(sink.lastDelivered).isEqualTo("scoreboard.hidden");
        assertThat(events.published).containsExactly(new ScoreboardVisibilityToggled(ALICE, true));
    }

    @Test
    void secondToggleShowsAgainAndConfirmsShown() {
        FakeStore store = new FakeStore();
        CapturingSink sink = new CapturingSink();
        RecordingEvents events = new RecordingEvents();
        ToggleScoreboard toggle = new ToggleScoreboard(store, new ScoreboardNotifier(new EchoMessages(), sink), events);

        toggle.toggle(ALICE); // hide
        boolean hidden = toggle.toggle(ALICE); // show again

        assertThat(hidden).isFalse();
        assertThat(store.hidden(ALICE)).isFalse();
        assertThat(sink.lastDelivered).isEqualTo("scoreboard.shown");
        assertThat(events.published)
                .containsExactly(
                        new ScoreboardVisibilityToggled(ALICE, true), new ScoreboardVisibilityToggled(ALICE, false));
    }

    /** An in-memory visibility store, default shown. */
    private static final class FakeStore implements ScoreboardVisibilityStore {
        private final Map<UUID, Boolean> hidden = new ConcurrentHashMap<>();

        @Override
        public boolean hidden(PlayerRef who) {
            return hidden.getOrDefault(who.uuid(), false);
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return hidden.compute(who.uuid(), (uuid, current) -> !(current != null && current));
        }

        @Override
        public void forget(PlayerRef who) {
            hidden.remove(who.uuid());
        }
    }

    /** A sink that records the last resolved string handed to it. */
    private static final class CapturingSink implements MessageSink {
        private String lastDelivered = "";

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            this.lastDelivered = renderedText;
        }
    }

    /** Resolves a key to its catalog lookup string, so the test asserts which key was sent. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records every published event for equality assertions. */
    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
