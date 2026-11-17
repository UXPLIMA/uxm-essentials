package com.uxplima.uxmessentials.api.view;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a player name resolved to: one account, nobody, or several accounts it could mean.
 *
 * <p>The third answer exists for offline-mode servers, where {@code Steve} and {@code steve} are two accounts. A name
 * spelled exactly as one of them is that one. A name that matches several only in case is none of them: picking one
 * would hand a consumer an account its user may not have meant, so the candidates come back instead and the user
 * chooses.
 */
public sealed interface UxmPlayerMatch permits UxmPlayerMatch.Found, UxmPlayerMatch.Unknown, UxmPlayerMatch.Ambiguous {

    /** The account the name resolved to; empty when it named nobody or several accounts. */
    Optional<UxmPlayer> player();

    /** Every account the name could mean: the one found, the ambiguous ones, or none. */
    List<UxmPlayer> candidates();

    /**
     * The name resolved to one account.
     *
     * @param account the account
     */
    record Found(UxmPlayer account) implements UxmPlayerMatch {

        public Found {
            Objects.requireNonNull(account, "account");
        }

        @Override
        public Optional<UxmPlayer> player() {
            return Optional.of(account);
        }

        @Override
        public List<UxmPlayer> candidates() {
            return List.of(account);
        }
    }

    /** No account the server knows has played under the name. */
    record Unknown() implements UxmPlayerMatch {

        @Override
        public Optional<UxmPlayer> player() {
            return Optional.empty();
        }

        @Override
        public List<UxmPlayer> candidates() {
            return List.of();
        }
    }

    /**
     * The name matches two or more accounts only in case, and none is spelled exactly as asked.
     *
     * @param name the name as it was asked for
     * @param candidates every account it could mean
     */
    record Ambiguous(String name, List<UxmPlayer> candidates) implements UxmPlayerMatch {

        public Ambiguous {
            Objects.requireNonNull(name, "name");
            candidates = List.copyOf(candidates);
            if (candidates.size() < 2) {
                throw new IllegalArgumentException("an ambiguous name needs two accounts or more: " + candidates);
            }
        }

        @Override
        public Optional<UxmPlayer> player() {
            return Optional.empty();
        }
    }
}
