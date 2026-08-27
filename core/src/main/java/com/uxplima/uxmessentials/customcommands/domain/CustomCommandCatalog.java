package com.uxplima.uxmessentials.customcommands.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the definitions read off disk into the set that actually registers, in declaration order.
 *
 * <p>The rule is the one the shared command catalog already applies to built-in commands, so an operator meets one
 * behaviour rather than two: the first claim of a word keeps it. A duplicate id or a primary name somebody already
 * took drops the whole command; a merely colliding alias drops that alias and keeps the command. Every drop is
 * reported, because a command silently missing an alias is the kind of thing nobody thinks to look for.
 */
public final class CustomCommandCatalog {

    private CustomCommandCatalog() {}

    /** Resolve {@code commands} in order, dropping collisions and reporting each one. */
    public static Loaded of(List<CustomCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        List<CustomCommand> accepted = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> claimedWords = new HashSet<>();
        Set<String> claimedIds = new HashSet<>();
        for (CustomCommand command : commands) {
            if (!claimedIds.add(command.id().value())) {
                warnings.add("dropping a second definition of '" + command.id() + "'");
                continue;
            }
            String nameKey = key(command.literal().name());
            if (!claimedWords.add(nameKey)) {
                warnings.add("dropping '" + command.id() + "': the command word '"
                        + command.literal().name() + "' is already taken");
                continue;
            }
            accepted.add(command.withLiteral(trimAliases(command, claimedWords, warnings)));
        }
        return new Loaded(accepted, warnings);
    }

    private static CommandLiteral trimAliases(CustomCommand command, Set<String> claimedWords, List<String> warnings) {
        List<String> aliases = new ArrayList<>();
        for (String alias : command.literal().aliases()) {
            if (claimedWords.add(key(alias))) {
                aliases.add(alias);
            } else {
                warnings.add("dropping alias '" + alias + "' of '" + command.id() + "': already taken");
            }
        }
        Map<String, List<String>> localized = new LinkedHashMap<>();
        command.literal().localizedAliases().forEach((locale, words) -> {
            List<String> kept = new ArrayList<>();
            for (String word : words) {
                if (claimedWords.add(key(word))) {
                    kept.add(word);
                } else {
                    warnings.add(
                            "dropping " + locale + " alias '" + word + "' of '" + command.id() + "': already taken");
                }
            }
            if (!kept.isEmpty()) {
                localized.put(locale, kept);
            }
        });
        return new CommandLiteral(command.literal().name(), aliases, localized);
    }

    private static String key(String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    /** The resolved set plus the warnings the resolution produced, both immutable. */
    public record Loaded(List<CustomCommand> commands, List<String> warnings) {

        public Loaded {
            commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }

        /** The empty catalog, which a fresh install with no command files resolves to. */
        public static Loaded empty() {
            return new Loaded(List.of(), List.of());
        }

        /** The command with that id, or empty when none loaded under it. */
        public Optional<CustomCommand> byId(String id) {
            Objects.requireNonNull(id, "id");
            return commands.stream()
                    .filter(command -> command.id().value().equals(id))
                    .findFirst();
        }

        /** The loaded ids, in declaration order. */
        public List<String> ids() {
            return commands.stream().map(command -> command.id().value()).toList();
        }

        /** Every word the loaded set claims, for a tab-completion source that must not repeat itself. */
        public List<String> words() {
            Set<String> words = new LinkedHashSet<>();
            commands.forEach(command -> words.addAll(command.literal().allWords()));
            return List.copyOf(words);
        }
    }
}
