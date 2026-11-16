package com.uxplima.uxmessentials.moderation.application.port;

import java.util.Set;

/**
 * The names the server knows for a typed command word. A port because the command list belongs to the server
 * (other plugins and the game register into it); tests bind a fake.
 */
public interface CommandNames {

    /** Knows no command, so only the typed word itself is ever compared. */
    CommandNames NONE = word -> Set.of();

    /**
     * Every name of the command {@code word} runs: its own name, the label it was registered under and the aliases
     * it lists, lower case and without a {@code plugin:} namespace. Empty when no command answers to {@code word}.
     * Called on the thread of the player who typed the command, once per typed command, so it must not do I/O. On
     * Paper a lookup is not free: it walks the command tree and allocates, which is why the policy keeps what it can.
     *
     * @param word the command word as typed, lower case, with or without a namespace
     */
    Set<String> namesOf(String word);
}
