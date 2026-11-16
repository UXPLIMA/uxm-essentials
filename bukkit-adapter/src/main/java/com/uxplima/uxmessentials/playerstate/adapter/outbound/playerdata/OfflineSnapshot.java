package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * An offline container as a window was opened on it: the items it showed, one per slot, and the file state they came
 * from. A save of the window is checked against that file state, which is what keeps two edits from overwriting each
 * other. The item type is a parameter so the rules built on this run without a server.
 *
 * @param <I> the item type
 */
public final class OfflineSnapshot<I> {

    private final @Nullable SaveOutcome problem;
    private final @Nullable I[] items;
    private final @Nullable StoredSlots stored;
    private final boolean editable;

    private OfflineSnapshot(
            @Nullable SaveOutcome problem, @Nullable I[] items, @Nullable StoredSlots stored, boolean editable) {
        this.problem = problem;
        this.items = items;
        this.stored = stored;
        this.editable = editable;
    }

    /** A container that was read: {@code editable} when every item was understood and the data is current. */
    public static <I> OfflineSnapshot<I> found(@Nullable I[] items, StoredSlots stored, boolean editable) {
        return new OfflineSnapshot<>(
                null,
                Objects.requireNonNull(items, "items").clone(),
                Objects.requireNonNull(stored, "stored"),
                editable);
    }

    /** A container that could not be read, for {@code reason}; {@code empty} is a zero-length item array. */
    public static <I> OfflineSnapshot<I> unreadable(SaveOutcome reason, @Nullable I[] empty) {
        return new OfflineSnapshot<>(Objects.requireNonNull(reason, "reason"), Arrays.copyOf(empty, 0), null, false);
    }

    /** Why the container could not be read, or empty when it was. */
    public Optional<SaveOutcome> problem() {
        return Optional.ofNullable(problem);
    }

    /** The items, one per slot, as a copy; {@code null} is an empty slot. */
    public @Nullable I[] items() {
        return items.clone();
    }

    /** Whether a window on this may change the file: the items were all understood and written by this version. */
    public boolean editable() {
        return editable;
    }

    /** The file state the items were read from. */
    public StoredSlots stored() {
        return Objects.requireNonNull(stored, "a snapshot that was not read has nothing to save against");
    }
}
