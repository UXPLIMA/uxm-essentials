package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.moderation.adapter.SanctionRows;
import com.uxplima.uxmessentials.moderation.application.OnlineMutes;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A chat line whose speaker's mute cannot be read is held back, never let through: the speaker is told to try again
 * and the operator finds the failure in the log. A mute kept from before the database went down still stops the line,
 * and a line a plugin makes a player say on the server thread is answered from memory, never the database.
 */
class MutedChatListenerTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    private final SanctionRows store = new SanctionRows();
    private final List<String> errors = new ArrayList<>();
    private final List<String> told = new ArrayList<>();
    private final OnlineMutes mutes =
            new OnlineMutes(store, new AtOnce(), Clock.fixed(NOW, ZoneOffset.UTC), new Errors(errors));
    private final List<UUID> exempt = new ArrayList<>();
    private final MutedChatListener listener = new MutedChatListener(
            mutes.fresh(),
            mutes,
            who -> exempt.contains(who.uuid()),
            (viewer, key, placeholders) -> key.key(),
            (viewer, text) -> told.add(viewer.name() + ": " + text));

    private ServerMock server;
    private PlayerMock sam;

    @BeforeEach
    void mock() {
        server = MockBukkit.mock();
        sam = server.addPlayer("Sam");
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    @Test
    void aLineWhoseSpeakersMuteCannotBeReadIsHeldBack() {
        store.goDown();

        AsyncChatEvent line = chat(sam);

        assertThat(line.isCancelled()).isTrue();
        assertThat(told).containsExactly("Sam: msg.mute-unchecked");
        assertThat(errors).containsExactly("event=mute_check_read_failed player=Sam unlogged=0");
    }

    @Test
    void aMuteKeptBeforeTheDatabaseWentDownStillStopsTheLine() {
        store.mute(BukkitRefs.toRef(sam), muted());
        mutes.joined(BukkitRefs.toRef(sam));
        store.goDown();

        AsyncChatEvent line = chat(sam);

        assertThat(line.isCancelled()).isTrue();
        assertThat(told).containsExactly("Sam: moderation.muted-chat-blocked");
    }

    @Test
    void anExemptSpeakerTalksEvenWhenTheirMuteCannotBeRead() {
        exempt.add(sam.getUniqueId());
        store.goDown();

        AsyncChatEvent line = chat(sam);

        assertThat(line.isCancelled()).isFalse();
        assertThat(told).isEmpty();
    }

    @Test
    void aSpeakerWhoIsNotMutedTalks() {
        AsyncChatEvent line = chat(sam);

        assertThat(line.isCancelled()).isFalse();
        assertThat(told).isEmpty();
    }

    @Test
    void aLineSaidOnTheServerThreadIsAnsweredFromMemoryNotTheDatabase() {
        store.mute(BukkitRefs.toRef(sam), muted());
        mutes.joined(BukkitRefs.toRef(sam));
        // Lifted behind the kept copy's back: only a database read would see it.
        store.mute(BukkitRefs.toRef(sam), MuteState.none());

        AsyncChatEvent line = chat(sam, false);

        assertThat(line.isCancelled()).isTrue();
        assertThat(told).containsExactly("Sam: moderation.muted-chat-blocked");
    }

    @Test
    void aLineSaidOnTheServerThreadBeforeTheMuteIsKnownIsHeldBack() {
        AsyncChatEvent line = chat(sam, false);

        assertThat(line.isCancelled()).isTrue();
        assertThat(told).containsExactly("Sam: msg.mute-unchecked");
    }

    private static MuteState muted() {
        return MuteState.permanent(Issuer.console("Console"), Optional.empty(), NOW.minus(Duration.ofHours(1)));
    }

    /** A line typed by the player, which Paper raises on the chat thread. */
    private AsyncChatEvent chat(PlayerMock speaker) {
        return chat(speaker, true);
    }

    private AsyncChatEvent chat(PlayerMock speaker, boolean typed) {
        Component message = Component.text("hello");
        AsyncChatEvent event = new AsyncChatEvent(
                typed,
                speaker,
                new HashSet<>(server.getOnlinePlayers()),
                ChatRenderer.defaultRenderer(),
                message,
                message,
                SignedMessage.system("hello", message));
        listener.onChat(event);
        return event;
    }

    /** Runs every task at once, on the calling thread. */
    private static final class AtOnce implements Scheduler {

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Keeps each error line the operator would read. */
    private record Errors(List<String> lines) implements Logger {

        @Override
        public void info(String message, @Nullable Object... args) {}

        @Override
        public void warn(String message, @Nullable Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            lines.add(message);
        }

        @Override
        public void debug(String message, @Nullable Object... args) {}
    }
}
