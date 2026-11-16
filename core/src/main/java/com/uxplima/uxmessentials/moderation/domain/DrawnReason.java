package com.uxplima.uxmessentials.moderation.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A sanction reason the server writes from a catalog line rather than one a staff member typed: the warn ladder's
 * "reached 3 warnings, the last for: spam". It is kept as the line and its arguments, so each reader reads it in their
 * own language and an operator's later edit of the line reaches sanctions already given. The sanction keeps the text
 * drawn for the punished player beside it, for whatever cannot draw a line: the audit log, a release before this one,
 * and a stored line this release no longer knows.
 *
 * <p>Only the lines in {@link #LINES} can be a reason. A stored key outside them reads as no drawn reason, so the
 * kept text is shown instead of a raw key.
 *
 * @param line the catalog line the reason is drawn from
 * @param args the line's arguments, by name
 */
public record DrawnReason(ModerationMessageKey line, Map<String, String> args) {

    /** The catalog lines a sanction reason can be drawn from. */
    private static final Set<ModerationMessageKey> LINES = EnumSet.of(
            ModerationMessageKey.MOD_WARN_ESCALATION_REASON, ModerationMessageKey.MOD_WARN_ESCALATION_REASON_BARE);

    public DrawnReason {
        Objects.requireNonNull(line, "line");
        if (!LINES.contains(line)) {
            throw new IllegalArgumentException("not a reason line: " + line.key());
        }
        args = Map.copyOf(args);
    }

    /**
     * The reason a warn ladder step's sanction is given: the warning count that tripped it and the reason of the
     * warning that reached it, or the count alone when that warning gave no reason.
     */
    public static DrawnReason warnStep(int count, Optional<String> warnReason) {
        Objects.requireNonNull(warnReason, "warnReason");
        String total = Integer.toString(count);
        return warnReason
                .filter(text -> !text.isBlank())
                .map(text -> new DrawnReason(
                        ModerationMessageKey.MOD_WARN_ESCALATION_REASON, Map.of("count", total, "reason", text)))
                .orElseGet(() ->
                        new DrawnReason(ModerationMessageKey.MOD_WARN_ESCALATION_REASON_BARE, Map.of("count", total)));
    }

    /** The drawn reason stored under {@code key} with {@code args}; empty when the key names no reason line. */
    public static Optional<DrawnReason> stored(String key, Map<String, String> args) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(args, "args");
        return LINES.stream()
                .filter(line -> line.key().equals(key))
                .findFirst()
                .map(line -> new DrawnReason(line, args));
    }

    /** The catalog key the reason is stored under. */
    public String key() {
        return line.key();
    }
}
