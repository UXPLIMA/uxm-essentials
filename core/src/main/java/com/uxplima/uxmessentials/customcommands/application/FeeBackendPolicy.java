package com.uxplima.uxmessentials.customcommands.application;

/**
 * What a priced custom command does while the currency backend behind its cost does not answer. Both choices cost
 * the operator something, so neither is hidden: allowing hands out whatever the price guarded, denying turns every
 * priced command into a refusal until the economy returns.
 */
public enum FeeBackendPolicy {

    /** Run the command without charging, so a missing economy never locks players out of their commands. */
    ALLOW,

    /** Refuse the command and run nothing, so a price meant to guard a reward is never skipped. */
    DENY
}
