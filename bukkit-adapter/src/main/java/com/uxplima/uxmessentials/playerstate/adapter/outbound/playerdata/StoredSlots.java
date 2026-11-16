package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A container's slots as one read of the file found them: the stored payload of every filled slot and the file's data
 * version. A save hands this back so it can refuse when any slot it changes, or the version, no longer matches.
 */
public final class StoredSlots {

    /** The version a file without a {@code DataVersion} tag reads as, which no running server has. */
    public static final int NO_VERSION = -1;

    private final int dataVersion;
    private final Map<Integer, TagPayload> slots;

    StoredSlots(int dataVersion, Map<Integer, TagPayload> slots) {
        this.dataVersion = dataVersion;
        this.slots = Map.copyOf(Objects.requireNonNull(slots, "slots"));
    }

    /** The {@code DataVersion} the file was written with, or {@link #NO_VERSION}. */
    public int dataVersion() {
        return dataVersion;
    }

    /** The stored payload of {@code slot}, or {@code null} when the slot is empty. */
    public @Nullable TagPayload slot(int slot) {
        return slots.get(slot);
    }

    /** Every filled slot and its payload. */
    public Map<Integer, TagPayload> filled() {
        return slots;
    }
}
