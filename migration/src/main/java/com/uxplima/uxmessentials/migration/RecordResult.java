package com.uxplima.uxmessentials.migration;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * What writing one record did: the {@link RecordOutcome} that is counted, and the notes that explain it on the
 * record's audit line. A note is one {@code key=value} audit field without spaces, such as
 * {@code mail_skipped=messaging_disabled}, so the line stays machine-readable.
 *
 * @param outcome the outcome counted in the summary
 * @param notes extra audit fields, in the order they are written
 */
@NullMarked
public record RecordResult(RecordOutcome outcome, List<String> notes) {

    public RecordResult {
        Objects.requireNonNull(outcome, "outcome");
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
        for (String note : notes) {
            if (note.isBlank() || note.chars().anyMatch(Character::isWhitespace) || note.indexOf('=') < 1) {
                throw new IllegalArgumentException("an audit note is one key=value field without spaces: " + note);
            }
        }
    }

    /** An outcome with nothing more to say. */
    public static RecordResult of(RecordOutcome outcome) {
        return new RecordResult(outcome, List.of());
    }
}
