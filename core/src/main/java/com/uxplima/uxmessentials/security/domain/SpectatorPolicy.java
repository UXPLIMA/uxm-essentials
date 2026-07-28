package com.uxplima.uxmessentials.security.domain;

import java.util.Locale;

/**
 * What to do with a player who has to verify while they are in spectator mode. A spectator cannot click any
 * inventory the server opens for them, so a spectator shown the keypad can see every button and press none of
 * them: they are frozen with no way to prove anything and no way out but a disconnect, which changes nothing.
 *
 * <p>The fix is to move them into a mode that can click, hold their original mode for the length of the freeze,
 * and put it back the moment they verify. {@link #NONE} opts out for servers that never let a spectator reach the
 * join flow, and it is the only value that leaves the hole open.
 */
public enum SpectatorPolicy {

    /** Hold them in adventure mode while they verify: they can click the keypad but cannot edit the world. */
    ADVENTURE,

    /** Hold them in survival mode while they verify, for servers whose spectators are staff mid-session. */
    SURVIVAL,

    /** Leave a spectator in spectator mode. The keypad will not respond to them; only choose this knowingly. */
    NONE;

    /** The policy named by {@code raw}, falling back to {@link #ADVENTURE} so a typo never re-opens the hole. */
    public static SpectatorPolicy parse(String raw) {
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "survival" -> SURVIVAL;
            case "none", "off", "keep", "spectator" -> NONE;
            default -> ADVENTURE;
        };
    }
}
