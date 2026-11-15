package com.uxplima.uxmessentials.shared.network;

/**
 * A frame names a message type this version does not know. In a cluster being upgraded one backend at a time this
 * is the ordinary case of a newer peer's frame, not a fault, so a receiver drops it quietly instead of warning. It
 * stays an {@link IllegalArgumentException} so every caller that treats a bad frame as malformed keeps working.
 */
public final class UnknownMessageTypeException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final byte wireTag;

    /** A frame whose type tag is {@code wireTag}. */
    public UnknownMessageTypeException(byte wireTag) {
        super("unknown network message wire tag: " + wireTag);
        this.wireTag = wireTag;
    }

    /** The tag the frame carried. */
    public byte wireTag() {
        return wireTag;
    }
}
