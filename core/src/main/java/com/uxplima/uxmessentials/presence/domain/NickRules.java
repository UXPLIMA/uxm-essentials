package com.uxplima.uxmessentials.presence.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The shape a nick may take: up to sixteen letters, digits and underscores, or the player's own account name in any
 * case, which keeps a Bedrock player's {@code .Name} open to them. Whether another player holds it is the store's to
 * say.
 */
public final class NickRules {

    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_]+");

    private NickRules() {}

    /** Whether {@code nick}, already stripped, is a nick the account named {@code accountName} may wear. */
    public static boolean allowed(String nick, String accountName) {
        Objects.requireNonNull(nick, "nick");
        Objects.requireNonNull(accountName, "accountName");
        if (nick.isEmpty() || nick.length() > MAX_LENGTH) {
            return false;
        }
        return ALLOWED.matcher(nick).matches()
                || nick.toLowerCase(Locale.ROOT).equals(accountName.toLowerCase(Locale.ROOT));
    }
}
