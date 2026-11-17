package com.uxplima.uxmessentials.migration.convert.map;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An account a source knew by name: its uuid, the name it last played under, and when the source last saw it. The
 * importer records it in the plugin's name index, so a player who has not joined since the migration can be named in
 * {@code /unban}, {@code /pay}, {@code /seen} and every other command that takes a player.
 *
 * <p>Only a name an account can log in under is kept: 1 to 16 letters, digits and underscores, after at most one
 * prefix character a Bedrock bridge such as Floodgate puts in front ({@code .Steve}). A nickname (colours, spaces,
 * more than 16 characters) or a uuid written where a name belongs is not a name, so {@link #of} answers empty for it.
 *
 * @param uuid the account
 * @param name the name it last played under
 * @param lastSeen when the source last saw it; {@link Instant#EPOCH} when the source does not say
 */
@NullMarked
public record ImportedAccount(UUID uuid, String name, Instant lastSeen) {

    private static final Pattern ACCOUNT_NAME = Pattern.compile("[.*+!~#$-]?[A-Za-z0-9_]{1,16}");
    private static final int MAX_LENGTH = 16;

    public ImportedAccount {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lastSeen, "lastSeen");
        if (!isAccountName(name)) {
            throw new IllegalArgumentException("not a name an account plays under: " + name);
        }
    }

    /** The account, or empty when {@code name} is missing or not a name an account plays under. */
    public static Optional<ImportedAccount> of(UUID uuid, @Nullable String name, Instant lastSeen) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(lastSeen, "lastSeen");
        if (name == null) {
            return Optional.empty();
        }
        String stripped = name.strip();
        return isAccountName(stripped) ? Optional.of(new ImportedAccount(uuid, stripped, lastSeen)) : Optional.empty();
    }

    /** Whether {@code name} is one an account can play under, rather than a nickname or a uuid. */
    public static boolean isAccountName(String name) {
        Objects.requireNonNull(name, "name");
        return name.length() <= MAX_LENGTH && ACCOUNT_NAME.matcher(name).matches();
    }

    /** The row the name index keeps for this account. */
    public PlayerName row() {
        return new PlayerName(uuid, name, lastSeen.toEpochMilli());
    }
}
