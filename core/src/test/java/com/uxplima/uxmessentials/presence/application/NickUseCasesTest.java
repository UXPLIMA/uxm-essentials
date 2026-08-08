package com.uxplima.uxmessentials.presence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.presence.application.port.NickStore;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /nick}: set or clear a player's display name. A valid nick is stamped through the {@link NickStore}
 * and confirmed; an over-long or illegal nick is rejected before any stamp; clearing removes the stamp. The
 * use cases own the validation so the adapter stays a thin argument-mapper.
 */
class NickUseCasesTest {

    private RecordingNickStore store;
    private CapturingSink sink;
    private PlayerRef alice;

    @BeforeEach
    void setUp() {
        store = new RecordingNickStore();
        sink = new CapturingSink();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    private SetNick setNick() {
        return new SetNick(store, new Notifier(new KeyMessages(), sink));
    }

    private ClearNick clearNick() {
        return new ClearNick(store, new Notifier(new KeyMessages(), sink));
    }

    @Test
    void validNickIsStampedAndConfirmed() {
        setNick().set(alice, alice, "Ace");

        assertThat(store.nicks).containsEntry(alice.uuid(), "Ace");
        assertThat(sink.delivered("presence.nick-set")).isTrue();
    }

    @Test
    void emptyNickIsRejectedWithoutStamp() {
        setNick().set(alice, alice, "   ");

        assertThat(store.nicks).isEmpty();
        assertThat(sink.delivered("presence.nick-invalid")).isTrue();
    }

    @Test
    void overlongNickIsRejectedWithoutStamp() {
        setNick().set(alice, alice, "ThisNicknameIsFarTooLongToBeAccepted");

        assertThat(store.nicks).isEmpty();
        assertThat(sink.delivered("presence.nick-invalid")).isTrue();
    }

    @Test
    void settingForAnotherPlayerConfirmsTheOtherForm() {
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");

        setNick().set(alice, bob, "Bobby");

        assertThat(store.nicks).containsEntry(bob.uuid(), "Bobby");
        assertThat(sink.delivered("presence.nick-set-other")).isTrue();
    }

    @Test
    void clearingRemovesTheStamp() {
        store.nicks.put(alice.uuid(), "Ace");

        clearNick().clear(alice);

        assertThat(store.nicks).doesNotContainKey(alice.uuid());
        assertThat(sink.delivered("presence.nick-cleared")).isTrue();
    }

    private static final class RecordingNickStore implements NickStore {
        private final Map<UUID, String> nicks = new HashMap<>();

        @Override
        public void setNick(PlayerRef who, String nick) {
            nicks.put(who.uuid(), nick);
        }

        @Override
        public void clearNick(PlayerRef who) {
            nicks.remove(who.uuid());
        }
    }

    private static final class CapturingSink implements MessageSink {
        private final List<String> texts = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            texts.add(renderedText);
        }

        boolean delivered(String keyPrefix) {
            return texts.stream().anyMatch(t -> t.startsWith(keyPrefix));
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return placeholders.isEmpty() ? key.key() : key.key() + placeholders;
        }
    }
}
