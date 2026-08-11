package com.uxplima.uxmessentials.api.view;

/**
 * Which rule settled a command check. Every value already says whether the command runs, so a consumer that only
 * wants the yes or no can read {@link UxmCommandCheck#allowed()} and ignore this entirely.
 */
public enum UxmCommandRule {

    /** The player holds the bypass node, so no list was read at all. Allowed. */
    BYPASS,

    /** The list is a whitelist and the command is on it. Allowed. */
    WHITELISTED,

    /** The list is a whitelist and the command is not on it. Blocked. */
    NOT_WHITELISTED,

    /** The list is a blacklist and the command is on it. Blocked. */
    BLACKLISTED,

    /** The list is a blacklist and the command is not on it. Allowed. */
    NOT_BLACKLISTED
}
