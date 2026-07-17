package com.uxplima.uxmessentials.servertweaks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.servertweaks.domain.SignedChatDirective;
import com.uxplima.uxmessentials.servertweaks.domain.SignedSource;
import com.uxplima.uxmessentials.servertweaks.domain.SignedVelocityFrame;
import org.junit.jupiter.api.Test;

/**
 * Exercises the backend handshake queue as a port: offered directives come back FIFO, chat and command streams stay
 * separate, an empty stream yields nothing (the "no proxy ruling" case the listeners treat as leave-alone), a
 * disconnect forgets a player's buffered directives, and a runaway proxy cannot grow a bucket without bound.
 */
class SignedDirectiveQueueTest {

    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Test
    void pollOnAnEmptyStreamIsEmpty() {
        SignedDirectiveQueue queue = new SignedDirectiveQueue();

        assertThat(queue.poll(ALICE, SignedSource.CHAT)).isEmpty();
    }

    @Test
    void directivesComeBackInOrderPerStream() {
        SignedDirectiveQueue queue = new SignedDirectiveQueue();
        queue.offer(new SignedVelocityFrame(ALICE, SignedSource.CHAT, SignedChatDirective.cancel()));
        queue.offer(new SignedVelocityFrame(ALICE, SignedSource.CHAT, SignedChatDirective.modify("second")));

        assertThat(queue.poll(ALICE, SignedSource.CHAT))
                .get()
                .satisfies(d -> assertThat(d.cancelled()).isTrue());
        assertThat(queue.poll(ALICE, SignedSource.CHAT))
                .get()
                .satisfies(d -> assertThat(d.modifiedMessage()).contains("second"));
        assertThat(queue.poll(ALICE, SignedSource.CHAT)).isEmpty();
    }

    @Test
    void chatAndCommandStreamsAreIndependent() {
        SignedDirectiveQueue queue = new SignedDirectiveQueue();
        queue.offer(new SignedVelocityFrame(ALICE, SignedSource.COMMAND, SignedChatDirective.cancel()));

        // The command directive must not surface on the chat stream.
        assertThat(queue.poll(ALICE, SignedSource.CHAT)).isEmpty();
        assertThat(queue.poll(ALICE, SignedSource.COMMAND))
                .get()
                .satisfies(d -> assertThat(d.cancelled()).isTrue());
    }

    @Test
    void playersDoNotSeeEachOthersDirectives() {
        SignedDirectiveQueue queue = new SignedDirectiveQueue();
        queue.offer(new SignedVelocityFrame(ALICE, SignedSource.CHAT, SignedChatDirective.cancel()));

        assertThat(queue.poll(BOB, SignedSource.CHAT)).isEmpty();
    }

    @Test
    void forgetDropsAPlayersBufferedDirectives() {
        SignedDirectiveQueue queue = new SignedDirectiveQueue();
        queue.offer(new SignedVelocityFrame(ALICE, SignedSource.CHAT, SignedChatDirective.cancel()));
        queue.offer(new SignedVelocityFrame(ALICE, SignedSource.COMMAND, SignedChatDirective.cancel()));

        queue.forget(ALICE);

        assertThat(queue.poll(ALICE, SignedSource.CHAT)).isEmpty();
        assertThat(queue.poll(ALICE, SignedSource.COMMAND)).isEmpty();
    }

    @Test
    void aBucketNeverGrowsPastItsBound() {
        SignedDirectiveQueue queue = new SignedDirectiveQueue();
        for (int i = 0; i < SignedDirectiveQueue.MAX_PENDING_PER_KEY + 5; i++) {
            queue.offer(new SignedVelocityFrame(ALICE, SignedSource.CHAT, SignedChatDirective.cancel()));
        }

        int drained = 0;
        while (queue.poll(ALICE, SignedSource.CHAT).isPresent()) {
            drained++;
        }
        assertThat(drained).isEqualTo(SignedDirectiveQueue.MAX_PENDING_PER_KEY);
    }
}
