package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.jspecify.annotations.Nullable;

/**
 * One placeholder the plugin answers, described once.
 *
 * <p>A key used to exist only as a branch inside the resolver, which meant the published list was written by hand
 * from memory and fell behind the code. This record is the one place a key is declared: a guard checks the resolver
 * against it in both directions, {@code /uxmess placeholders} lists it in game, and the reference page is built
 * from it.
 *
 * @param key the key as an operator types it between the percent signs, without the {@code uxmessentials_} prefix,
 *     with the open segment written out when the shape is a family ({@code kit_cost_<kit>})
 * @param description what the key renders, in one sentence
 * @param shape whether this is one key or the head of an open family
 * @param scope who the key answers about, which decides how it behaves for an offline player
 * @param owner the feature module that answers it, or empty when the kernel does
 */
public record PlaceholderSpec(
        String key, String description, PlaceholderShape shape, PlaceholderScope scope, Optional<ModuleId> owner) {

    public PlaceholderSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(owner, "owner");
        if (key.isBlank()) {
            throw new IllegalArgumentException("a placeholder needs a key");
        }
        if (!key.equals(key.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("keys are resolved lower-cased, so declare them that way: " + key);
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("key " + key + " needs a description an operator can act on");
        }
        if ((shape == PlaceholderShape.FAMILY) != key.contains("<")) {
            throw new IllegalArgumentException("key " + key + " and shape " + shape + " disagree about its segment");
        }
    }

    /** A key one feature module answers. */
    public static PlaceholderSpec of(String key, String description, PlaceholderScope scope, ModuleId owner) {
        return new PlaceholderSpec(key, description, PlaceholderShape.FIXED, scope, Optional.of(owner));
    }

    /** A family one feature module answers, written with its open segment. */
    public static PlaceholderSpec family(String key, String description, PlaceholderScope scope, ModuleId owner) {
        return new PlaceholderSpec(key, description, PlaceholderShape.FAMILY, scope, Optional.of(owner));
    }

    /** A key the kernel answers rather than any one module: the server metrics. */
    public static PlaceholderSpec shared(String key, String description, PlaceholderScope scope) {
        return new PlaceholderSpec(key, description, PlaceholderShape.FIXED, scope, Optional.empty());
    }

    /** A kernel-owned family. */
    public static PlaceholderSpec sharedFamily(String key, String description, PlaceholderScope scope) {
        return new PlaceholderSpec(key, description, PlaceholderShape.FAMILY, scope, Optional.empty());
    }

    /** The area an operator browses this key under: the owning module's id, or {@code shared}. */
    public String area() {
        return owner.map(ModuleId::value).orElse("shared");
    }

    /**
     * How the key reads in a message, with the prefix an operator actually types. A relational key carries the
     * {@code rel_} prefix PlaceholderAPI routes the two-player form under.
     */
    public String placeholder() {
        return (scope == PlaceholderScope.RELATIONAL ? "%rel_uxmessentials_" : "%uxmessentials_") + key + "%";
    }

    /** Whether this key answers about a pair of players rather than the one it is rendered for. */
    public boolean relational() {
        return scope == PlaceholderScope.RELATIONAL;
    }

    /**
     * The key with every open segment filled in by {@code sample}, so a family can be resolved for real:
     * {@code kit_cost_<kit>} becomes {@code kit_cost_example}. A fixed key is returned unchanged.
     */
    public String sampled(String sample) {
        Objects.requireNonNull(sample, "sample");
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < key.length()) {
            char character = key.charAt(index);
            if (character != '<') {
                out.append(character);
                index++;
                continue;
            }
            int close = key.indexOf('>', index);
            if (close < 0) {
                out.append(key, index, key.length());
                break;
            }
            out.append(numeric(key.substring(index + 1, close)) ? "1" : sample);
            index = close + 1;
        }
        return out.toString();
    }

    /** Whether {@code candidate} is this key, or a member of it when this is a family. */
    public boolean covers(@Nullable String candidate) {
        if (candidate == null) {
            return false;
        }
        if (shape == PlaceholderShape.FIXED) {
            return key.equals(candidate);
        }
        return candidate.startsWith(head());
    }

    /** The fixed part of a family, up to but not including its first open segment. */
    public String head() {
        int open = key.indexOf('<');
        return open < 0 ? key : key.substring(0, open);
    }

    /** Whether an open segment carries a number rather than a name, which changes what a sample looks like. */
    private static boolean numeric(String segment) {
        return segment.equals("n") || segment.equals("rank") || segment.equals("index");
    }
}
