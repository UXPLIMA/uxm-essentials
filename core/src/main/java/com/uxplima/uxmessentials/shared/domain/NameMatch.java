package com.uxplima.uxmessentials.shared.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * What a typed player name resolved to: one player, nobody, or several accounts the name could mean.
 *
 * <p>The third answer exists for offline-mode servers, where {@code Steve} and {@code steve} are two accounts. When
 * neither is spelled exactly as typed, picking one would act on a player the sender may not have meant, so the
 * caller says which spellings exist instead and lets the sender type the one they want.
 */
public sealed interface NameMatch permits NameMatch.Found, NameMatch.Unknown, NameMatch.Ambiguous {

    /** The player the name resolved to; empty when it named nobody or several accounts. */
    Optional<PlayerRef> player();

    /**
     * The line that tells the sender why no single player was found: the shared ambiguity line naming every
     * spelling when the name could mean several accounts, otherwise {@code unknown} with {@code args}.
     */
    MessageLine miss(MessageKey unknown, Map<String, String> args);

    /** A match of the single player in {@code player}, or of nobody. */
    static NameMatch of(Optional<PlayerRef> player) {
        Objects.requireNonNull(player, "player");
        return player.<NameMatch>map(Found::new).orElse(Unknown.INSTANCE);
    }

    /** A match of {@code player}. */
    static NameMatch found(PlayerRef player) {
        return new Found(player);
    }

    /** A name nobody here has used. */
    static NameMatch unknown() {
        return Unknown.INSTANCE;
    }

    /** {@code typed} could mean any of {@code variants}, two or more accounts whose names differ only in case. */
    static NameMatch ambiguous(String typed, Collection<PlayerRef> variants) {
        return new Ambiguous(typed, List.copyOf(variants));
    }

    /** The name resolved to one player. */
    record Found(PlayerRef ref) implements NameMatch {

        public Found {
            Objects.requireNonNull(ref, "ref");
        }

        @Override
        public Optional<PlayerRef> player() {
            return Optional.of(ref);
        }

        @Override
        public MessageLine miss(MessageKey unknown, Map<String, String> args) {
            return new MessageLine(unknown, args);
        }
    }

    /** The name resolved to nobody. */
    record Unknown() implements NameMatch {

        private static final Unknown INSTANCE = new Unknown();

        @Override
        public Optional<PlayerRef> player() {
            return Optional.empty();
        }

        @Override
        public MessageLine miss(MessageKey unknown, Map<String, String> args) {
            return new MessageLine(unknown, args);
        }
    }

    /**
     * The name could mean several accounts, none of them spelled exactly as typed.
     *
     * @param typed the name as the sender typed it
     * @param variants every account it could mean
     */
    record Ambiguous(String typed, List<PlayerRef> variants) implements NameMatch {

        public Ambiguous {
            Objects.requireNonNull(typed, "typed");
            variants = List.copyOf(variants);
            if (variants.size() < 2) {
                throw new IllegalArgumentException("an ambiguous name needs two accounts or more: " + variants);
            }
        }

        @Override
        public Optional<PlayerRef> player() {
            return Optional.empty();
        }

        @Override
        public MessageLine miss(MessageKey unknown, Map<String, String> args) {
            return new MessageLine(
                    SharedMessageKey.COMMAND_AMBIGUOUS_PLAYER, Map.of("player", typed, "matches", spellings()));
        }

        /** Every spelling once, in a fixed order, so the line reads the same each time. */
        public String spellings() {
            TreeSet<String> names = new TreeSet<>(Comparator.<String>naturalOrder());
            for (PlayerRef variant : variants) {
                names.add(variant.name());
            }
            return String.join(", ", names);
        }
    }
}
