package com.uxplima.uxmessentials.worlds.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Recognizer for our own built-in chunk generators, addressed as {@code uxmEssentials:<id>}. Only
 * the fully namespaced form is a built-in generator the engine resolves; the {@code /worlds create}
 * command is what maps a bare {@code void}/{@code flat} token onto the namespaced ref. Foreign
 * namespaces (e.g. {@code Multiverse:flat}) are external refs and are not recognized here.
 */
public final class BuiltInGenerators {

    public static final String VOID = "void";
    public static final String FLAT = "flat";

    private static final String NAMESPACE = "uxmessentials:";

    private BuiltInGenerators() {}

    /** The id ({@link #VOID}/{@link #FLAT}) iff {@code generatorRef} is our namespaced built-in; else empty. */
    public static Optional<String> idOf(String generatorRef) {
        Objects.requireNonNull(generatorRef, "generatorRef");
        String lower = generatorRef.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(NAMESPACE)) {
            return Optional.empty();
        }
        String id = lower.substring(NAMESPACE.length());
        return (id.equals(VOID) || id.equals(FLAT)) ? Optional.of(id) : Optional.empty();
    }

    /** The canonical namespaced ref for a built-in id, e.g. {@code uxmEssentials:void}. */
    public static GeneratorRef ref(String id) {
        Objects.requireNonNull(id, "id");
        return GeneratorRef.of("uxmEssentials:" + id);
    }
}
