package com.uxplima.uxmessentials.shared.application.permission;

/**
 * Who holds a node when nobody has been granted or denied it.
 *
 * <p>The four values are the ones a Bukkit permission can carry, kept here as a platform-neutral enum so the
 * catalogue stays pure application code. The adapter that registers the catalogue with the server maps each value
 * onto the server's own type.
 */
public enum PermissionDefault {

    /** Nobody, until it is granted. The default for anything that changes another player or the world. */
    FALSE,

    /** Everybody. The default for the self-service verbs a normal player is expected to have. */
    TRUE,

    /** Server operators only. The default for administrative and moderation surfaces. */
    OP,

    /** Everybody except operators, which is how an exemption reads when operators are already covered. */
    NOT_OP
}
