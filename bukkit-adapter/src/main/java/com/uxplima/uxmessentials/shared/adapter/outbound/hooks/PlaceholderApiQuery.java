package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * The worked-example capability that proves the hook pattern end to end: ask whether PlaceholderAPI is usable
 * and expand a string against a viewer. It is a small typed seam, not the production PlaceholderAPI bridge —
 * the operator-facing expansion and message bridge stay in {@code shared.adapter.outbound.papi}; this exists
 * to demonstrate (and let later hook features reuse the harness for) the present/absent contract.
 *
 * <p>This interface and its {@link #ABSENT} default reference none of {@code me.clip.placeholderapi}; the only
 * class that does is the real implementation behind {@link PlaceholderApiHook#whenPresent}.
 */
@NullMarked
public interface PlaceholderApiQuery {

    /** Whether a live PlaceholderAPI is available — false for the absent default. */
    boolean available();

    /** Expand any placeholders in {@code text} for {@code viewer}; the absent default returns it unchanged. */
    String expand(UUID viewer, String text);

    /** The safe no-op used when PlaceholderAPI is absent: never available, returns the text unchanged. */
    PlaceholderApiQuery ABSENT = new PlaceholderApiQuery() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String expand(UUID viewer, String text) {
            Objects.requireNonNull(viewer, "viewer");
            return Objects.requireNonNull(text, "text");
        }
    };
}
