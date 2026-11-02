package com.uxplima.uxmessentials.commandcontrol.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.command.CommandLocales;

/**
 * Command words for the readers of one language ({@code tab-completion.languages}). A word listed under a language is
 * left out of the command list a player who reads another language is sent, so a Turkish player finds {@code /ayarlar}
 * and an English one {@code /settings}. Typing a word still runs it for anyone the command lists allow, and a word in
 * no list, such as {@code /ping}, is everybody's.
 *
 * <p>A word can also be kept out of everybody's list ({@code tab-completion.hidden}) and still run when typed: the
 * plain-letter spelling of {@code /yardım}, {@code yardim}, stays for a keyboard without the language's letters
 * without showing twice.
 *
 * <p>A word is matched bare and under any namespace, so {@code uxmessentials:settings} goes where {@code settings}
 * goes.
 */
public final class LanguageWords {

    private final Map<String, Set<String>> tagsByWord;
    private final Set<String> hidden;

    private LanguageWords(Map<String, Set<String>> tagsByWord, Set<String> hidden) {
        this.tagsByWord = tagsByWord;
        this.hidden = hidden;
    }

    /**
     * Read {@code wordsByTag}, a list of words per language tag such as {@code tr} or {@code pt-BR}. A tag that names
     * no language is skipped, and a word listed under two languages is shown to the readers of both.
     */
    public static LanguageWords of(Map<String, List<String>> wordsByTag) {
        return of(wordsByTag, List.of());
    }

    /** {@link #of(Map)}, with {@code hiddenWords} kept out of every reader's list. */
    public static LanguageWords of(Map<String, List<String>> wordsByTag, List<String> hiddenWords) {
        Objects.requireNonNull(wordsByTag, "wordsByTag");
        Objects.requireNonNull(hiddenWords, "hiddenWords");
        Set<String> hidden = new HashSet<>();
        for (String word : hiddenWords) {
            if (word != null && !word.isBlank()) {
                hidden.add(word(word));
            }
        }
        Map<String, Set<String>> tags = new HashMap<>();
        wordsByTag.forEach((tag, words) -> CommandLocales.normalize(tag).ifPresent(normalized -> {
            for (String word : words) {
                if (word != null && !word.isBlank()) {
                    tags.computeIfAbsent(word(word), ignored -> new HashSet<>()).add(normalized);
                }
            }
        }));
        Map<String, Set<String>> frozen = new HashMap<>();
        tags.forEach((word, held) -> frozen.put(word, Set.copyOf(held)));
        return new LanguageWords(Map.copyOf(frozen), Set.copyOf(hidden));
    }

    /** True when no word belongs to a language and none is hidden, so the list needs no trimming. */
    public boolean isEmpty() {
        return tagsByWord.isEmpty() && hidden.isEmpty();
    }

    /** Whether {@code label} is hidden, or belongs to languages that a player reading {@code reader} does not read. */
    public boolean hiddenFor(String label, Locale reader) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(reader, "reader");
        String word = word(label);
        int colon = word.indexOf(':');
        String bare = colon < 0 ? word : word.substring(colon + 1);
        if (hidden.contains(bare)) {
            return true;
        }
        Set<String> tags = tagsByWord.get(bare);
        return tags != null && tags.stream().noneMatch(tag -> CommandLocales.matches(tag, reader));
    }

    /** Lowercase, trimmed and without a leading slash, as the lists and the sent labels compare. */
    private static String word(String raw) {
        String trimmed = raw.strip().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}
