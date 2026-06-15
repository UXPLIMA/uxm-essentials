package com.uxplima.uxmessentials.npc.domain;

/**
 * The kinds of effect an {@link NpcAction} produces when its {@link ClickTrigger} matches a click. Each type
 * interprets the action's raw string {@code value} its own way — a command line, a MiniMessage source, a sound
 * key, or a target server name — and the adapter's runner dispatches accordingly. The domain only names the
 * types and carries the value; how each one runs against Bukkit is an adapter concern.
 */
public enum NpcActionType {

    /** Run the value as a command from the server console. */
    RUN_CONSOLE,

    /** Run the value as a command performed by the clicking player. */
    RUN_PLAYER,

    /** Send the value to the player as a chat message. */
    MESSAGE,

    /** Show the value to the player on their action bar. */
    ACTIONBAR,

    /** Show the value to the player as a title; {@code title|subtitle} splits the two lines. */
    TITLE,

    /** Play the value as a sound to the player; {@code KEY[:volume[:pitch]]}. */
    SOUND,

    /** Send the player to the value-named server through the proxy's BungeeCord connect channel. */
    CONNECT
}
