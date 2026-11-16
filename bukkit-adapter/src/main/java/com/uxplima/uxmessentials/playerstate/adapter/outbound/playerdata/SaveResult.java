package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.util.Objects;
import java.util.Set;

/**
 * How a save ended, and which of the slots it was asked to change had been changed by someone else in the meantime.
 * {@link SaveOutcome#SAVED} with conflicts means the other slots were written and these were left as they were.
 *
 * @param outcome how the save ended
 * @param conflicts the slots that no longer held what the edit started from
 */
public record SaveResult(SaveOutcome outcome, Set<Integer> conflicts) {

    public SaveResult {
        Objects.requireNonNull(outcome, "outcome");
        conflicts = Set.copyOf(conflicts);
    }

    /** An outcome with no conflicting slots. */
    public static SaveResult of(SaveOutcome outcome) {
        return new SaveResult(outcome, Set.of());
    }
}
