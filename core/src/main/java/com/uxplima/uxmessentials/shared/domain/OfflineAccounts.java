package com.uxplima.uxmessentials.shared.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The accounts an offline-mode server makes from names, and the uuid literals staff type for an account by id.
 *
 * <p>Without authentication the server derives a player's uuid from the exact spelling of their name, the same way
 * every offline-mode server and proxy does, so the account a name will log in as is known before it ever joins. A
 * Bedrock player's account comes from Floodgate instead, and a prefixed name derives nothing.
 */
public final class OfflineAccounts {

    /** A name a Java account can log in under. */
    private static final Pattern JAVA_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** A uuid written with its dashes, or as the 32 hex digits Mojang's API prints. */
    private static final Pattern UUID_LITERAL = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{32}");

    private OfflineAccounts() {}

    /** The uuid an offline-mode server gives {@code name}, or empty when no Java account can have that name. */
    public static Optional<UUID> uuidOf(String name) {
        Objects.requireNonNull(name, "name");
        if (!JAVA_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }

    /** {@code typed} read as a uuid, with or without dashes, or empty when it is anything else. */
    public static Optional<UUID> uuidLiteral(String typed) {
        Objects.requireNonNull(typed, "typed");
        String text = typed.strip();
        if (!UUID_LITERAL.matcher(text).matches()) {
            return Optional.empty();
        }
        String dashed = text.length() == 32
                ? text.substring(0, 8) + "-" + text.substring(8, 12) + "-" + text.substring(12, 16) + "-"
                        + text.substring(16, 20) + "-" + text.substring(20)
                : text;
        return Optional.of(UUID.fromString(dashed));
    }
}
