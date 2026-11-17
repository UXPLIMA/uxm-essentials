package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;
import com.uxplima.uxmessentials.shared.domain.message.TypedText;
import org.jspecify.annotations.NullMarked;

/**
 * Whether the placeholders being filled on this thread go into one of our own MiniMessage lines. PlaceholderAPI
 * fills every placeholder of a text in one synchronous call, so a value our expansion answers while
 * {@link #during} runs, directly or inside another expansion's placeholder, ends up in that line. There it is shown
 * as the words it holds: a display name, a renamed item or a nickname a player chose cannot become a click or a
 * colour. Outside a pass (another plugin's text, a requirement that compares values) the expansion answers values
 * as they are, so a plugin that does not parse MiniMessage sees no backslashes.
 */
@NullMarked
final class MarkupPass {

    /** How many passes this thread is inside; a counter, so a pass started inside another ends with its own. */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private MarkupPass() {}

    /** {@code fill} run as a pass, which ends however {@code fill} does. */
    static <T> T during(Supplier<T> fill) {
        Objects.requireNonNull(fill, "fill");
        int[] depth = DEPTH.get();
        depth[0]++;
        try {
            return fill.get();
        } finally {
            depth[0]--;
            if (depth[0] == 0) {
                DEPTH.remove();
            }
        }
    }

    /** Whether this thread is inside a pass. */
    static boolean active() {
        return DEPTH.get()[0] > 0;
    }

    /**
     * {@code value}, which our expansion answers for {@code key}, as it goes into the text being filled: as words in a
     * pass unless the key is the operator's own markup, as it is otherwise.
     */
    static String shown(String key, String value) {
        if (!active() || PlaceholderCatalog.markup(key)) {
            return value;
        }
        return TypedText.asWords(TypedText.withoutLegacyCodes(value));
    }
}
