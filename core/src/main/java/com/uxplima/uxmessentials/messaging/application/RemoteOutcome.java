package com.uxplima.uxmessentials.messaging.application;

/** What the recipient's backend did with a private message sent from another backend. */
public enum RemoteOutcome {
    /** The recipient got the message. */
    DELIVERED,
    /** The recipient ignores the sender: nothing was shown, and the sender is told it was sent all the same. */
    IGNORED,
    /** The recipient turned private messages off. */
    TOGGLED_OFF,
    /** A reply, and the recipient turned reply routing off. */
    REPLIES_OFF,
    /** The recipient is no longer on the backend the message was sent to. */
    OFFLINE
}
