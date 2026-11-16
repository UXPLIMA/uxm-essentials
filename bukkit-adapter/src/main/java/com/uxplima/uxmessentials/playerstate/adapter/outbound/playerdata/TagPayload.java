package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.util.Arrays;
import java.util.Objects;

/**
 * The payload bytes of one NBT tag exactly as they sit in the file, without the type id and name in front of them.
 * Two payloads are equal when their bytes are, which is how a save tells that a slot still holds what the window was
 * opened with: anything that rewrote the slot in between, even with the same item, reads as a change.
 */
public final class TagPayload {

    private final byte[] bytes;

    public TagPayload(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }

    /** The bytes, as a copy. */
    public byte[] bytes() {
        return bytes.clone();
    }

    int length() {
        return bytes.length;
    }

    byte[] shared() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TagPayload payload && Arrays.equals(bytes, payload.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "TagPayload[" + bytes.length + " bytes]";
    }
}
