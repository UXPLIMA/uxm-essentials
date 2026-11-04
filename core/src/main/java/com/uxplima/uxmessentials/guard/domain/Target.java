package com.uxplima.uxmessentials.guard.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Who a rule applies to.
 *
 * <p>Inside one region, at the deciding action node, role and non-member rules are the most specific, then
 * permission and condition rules, then {@code everyone}. Role, non-member and everyone rules are fixed for a
 * player and are compiled ahead; permission and condition rules are answered when the check runs.
 */
public sealed interface Target {

    /** The specificity class, lower is more specific. */
    int rank();

    /** True when the answer depends on the player's permissions or a condition, not only on their role. */
    default boolean dynamic() {
        return false;
    }

    /** The text form used in commands and storage. */
    String text();

    /** Anyone, including nature for the {@code world.*} actions. */
    record Everyone() implements Target {
        @Override
        public int rank() {
            return 2;
        }

        @Override
        public String text() {
            return "everyone";
        }
    }

    /** A player with no role in the region. */
    record NonMember() implements Target {
        @Override
        public int rank() {
            return 0;
        }

        @Override
        public String text() {
            return "nonmember";
        }
    }

    /** Holders of a region role. */
    record Role(RoleName role) implements Target {
        public Role {
            Objects.requireNonNull(role, "role");
        }

        @Override
        public int rank() {
            return 0;
        }

        @Override
        public String text() {
            return "role:" + role.value();
        }
    }

    /** Players holding a permission node. */
    record Permission(String node) implements Target {
        public Permission {
            Objects.requireNonNull(node, "node");
            if (node.isBlank() || node.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("not a permission node: " + node);
            }
        }

        @Override
        public int rank() {
            return 1;
        }

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String text() {
            return "perm:" + node;
        }
    }

    /** Players meeting a condition expression of the uxmLib condition engine. */
    record Condition(String expression) implements Target {
        public Condition {
            Objects.requireNonNull(expression, "expression");
            if (expression.isBlank()) {
                throw new IllegalArgumentException("condition must not be blank");
            }
        }

        @Override
        public int rank() {
            return 1;
        }

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String text() {
            return "cond:" + expression;
        }
    }

    /** Parse the text form; a bare role name is read as {@code role:<name>}. */
    static Optional<Target> parse(String raw) {
        String text = Objects.requireNonNull(raw, "raw").strip();
        String lower = text.toLowerCase(Locale.ROOT);
        try {
            if (lower.equals("everyone") || lower.equals("herkes") || lower.equals("all")) {
                return Optional.of(new Everyone());
            }
            if (lower.equals("nonmember") || lower.equals("nonmembers") || lower.equals("yabanci")) {
                return Optional.of(new NonMember());
            }
            if (lower.startsWith("role:") || lower.startsWith("rol:")) {
                return Optional.of(new Role(RoleName.of(text.substring(text.indexOf(':') + 1))));
            }
            if (lower.startsWith("perm:")) {
                return Optional.of(new Permission(text.substring(5)));
            }
            if (lower.startsWith("cond:")) {
                return Optional.of(new Condition(text.substring(5)));
            }
            return Optional.of(new Role(RoleName.of(text)));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
