package com.uxplima.uxmessentials.api.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * One audit line: {@code event=player_mute actor=<uuid> target=<uuid> reason="spam"}, split into its event and the
 * fields after it.
 *
 * <p>The fields keep the order the line wrote them in. A value is exactly as written, so a quoted reason keeps its
 * quotes and its backslash escapes, and the line can be put back together unchanged.
 *
 * @param event the value of {@code event=}, such as {@code player_mute} or {@code economy_admin}
 * @param fields every other {@code key=value} on the line, in the order written
 */
@NullMarked
public record AuditEntry(String event, Map<String, String> fields) {

    public AuditEntry {
        Objects.requireNonNull(event, "event");
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(fields, "fields")));
    }

    /** The value of one field, when the line carries it. */
    public Optional<String> field(String key) {
        return Optional.ofNullable(fields.get(key));
    }
}
