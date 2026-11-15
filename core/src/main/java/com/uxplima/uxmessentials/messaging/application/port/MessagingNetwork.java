package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.messaging.application.RemoteAnswer;
import com.uxplima.uxmessentials.messaging.application.RemoteHelpOp;
import com.uxplima.uxmessentials.messaging.application.RemoteMessage;

/**
 * Carries the messaging context's traffic to the other backends of a network: a private message to the backend its
 * target is on, that backend's answer, and a help request for the staff everywhere. A port because it crosses a
 * platform boundary (the cross-server bus) and is the seam the multi-backend tests drive with a fake.
 *
 * <p>Sending is fire-and-forget: a bus with no path to peers discards the traffic, so a message waiting for its
 * answer falls back to the offline path when none comes.
 */
public interface MessagingNetwork {

    /** The network of a lone server: nothing leaves it. */
    MessagingNetwork NONE = new MessagingNetwork() {
        @Override
        public void send(RemoteMessage message) {
            // No peers to reach.
        }

        @Override
        public void answer(RemoteAnswer answer) {
            // No peers to reach.
        }

        @Override
        public void raise(RemoteHelpOp helpOp) {
            // No peers to reach.
        }
    };

    /** Send {@code message} toward the backend its target is on. */
    void send(RemoteMessage message);

    /** Tell every other backend what became of a message sent to a player here. */
    void answer(RemoteAnswer answer);

    /** Show {@code helpOp} to the staff on every other backend. */
    void raise(RemoteHelpOp helpOp);
}
