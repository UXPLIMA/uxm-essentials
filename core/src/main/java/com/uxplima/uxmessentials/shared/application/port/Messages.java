package com.uxplima.uxmessentials.shared.application.port;

import java.util.Map;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * Outbound port that resolves a {@link MessageKey} to a final rendered string in the viewer's locale.
 *
 * <p>This is a synchronous pure function: it resolves the viewer's locale through the
 * override → client → {@code en} fallback chain, looks up the template in the catalog, and does
 * literal {@code {name}} → value substitution. The return is a plain MiniMessage source string, no
 * Adventure types cross this boundary, which is what keeps {@code :core} free of {@code net.kyori}.
 * Tag parsing into a {@code Component} happens once downstream in the {@link MessageSink}.
 *
 * <p>The {@code viewer} is the locale dimension and is mandatory: there is no method into which a
 * caller can pass a {@code Locale} that then gets silently discarded.
 */
public interface Messages {

    /**
     * Resolve {@code key} for {@code viewer} with {@code placeholders} substituted, in the viewer's
     * locale. Placeholder values are literal {@code {name}} replacements, not MiniMessage tags. An
     * unmatched {@code {name}} is left intact as a dev aid.
     */
    String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders);

    /**
     * {@link #resolve} for arguments the operator wrote next to the key, as in an {@code @key name=value} line on a
     * hologram or in a menu. The adapter keeps the placeholders in a value a player may have typed from being filled
     * for the reader; an operator's own arguments are the operator's words, so theirs are filled like the rest of the
     * line. Never pass a value a player could have typed.
     */
    default String resolveAsWritten(PlayerRef viewer, MessageKey key, Map<String, String> arguments) {
        return resolve(viewer, key, arguments);
    }
}
