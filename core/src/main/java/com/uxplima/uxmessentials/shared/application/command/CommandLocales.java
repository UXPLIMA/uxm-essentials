package com.uxplima.uxmessentials.shared.application.command;

import java.util.Locale;
import java.util.Optional;

/** Locale-tag normalization and matching shared by command-catalog resolution and client visibility. */
public final class CommandLocales {

    private CommandLocales() {}

    /** Normalize {@code tr_TR}/{@code tr-TR} into a stable lowercase BCP-47 key, or empty for an invalid tag. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Locale locale = Locale.forLanguageTag(raw.strip().replace('_', '-'));
        if (locale.getLanguage().isEmpty() || locale.equals(Locale.ROOT)) {
            return Optional.empty();
        }
        return Optional.of(locale.toLanguageTag().toLowerCase(Locale.ROOT));
    }

    /** True for an exact locale tag, or a language-wide key such as {@code tr} matching {@code tr-TR}. */
    public static boolean matches(String configuredTag, Locale locale) {
        Optional<String> normalized = normalize(locale.toLanguageTag());
        if (normalized.isEmpty()) {
            return false;
        }
        String exact = normalized.get();
        return configuredTag.equals(exact)
                || configuredTag.equals(locale.getLanguage().toLowerCase(Locale.ROOT));
    }
}
