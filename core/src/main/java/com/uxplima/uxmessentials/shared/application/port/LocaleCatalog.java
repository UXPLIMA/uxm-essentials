package com.uxplima.uxmessentials.shared.application.port;

import java.util.Locale;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * Outbound port that resolves a {@link MessageKey} to its template in a locale, with the {@code en}
 * fallback chain already applied.
 *
 * <p>It abstracts "give me the template for this key in this locale" so the {@link Messages}
 * implementation does not hard-code HOCON loading. The implementation owns catalog loading and the
 * atomic reload swap; the per-key fallback chain (a missing key in {@code tr} falls back to {@code en}
 * for that key only, then to the key's own name so a message is never blank) lives behind
 * {@link #template}.
 */
public interface LocaleCatalog {

    /** Template for {@code key} in {@code locale}; falls back to {@code en}, then to {@code key.key()}. */
    String template(Locale locale, MessageKey key);

    /** Locales this catalog has loaded; always contains {@link Locale#ENGLISH}. */
    Set<Locale> loadedLocales();
}
