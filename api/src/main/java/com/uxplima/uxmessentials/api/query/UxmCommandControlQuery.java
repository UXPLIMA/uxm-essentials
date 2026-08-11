package com.uxplima.uxmessentials.api.query;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmCommandCheck;

/**
 * Whether the command gate would stop a command, and which rule would stop it.
 *
 * <p>For anything that shows a player what they can run: a help menu that hides what is blocked, a GUI that greys
 * out a button, a companion plugin deciding whether to offer a shortcut. Asking here means agreeing with the gate
 * instead of reimplementing it, which matters because the rules are per group and per world and the two interact.
 *
 * <p>Both answers need the live player: the rules that apply depend on the world they are standing in and on the
 * permissions they currently hold, and neither is knowable for somebody who is offline. An offline player is
 * therefore an empty answer rather than a guess.
 */
public interface UxmCommandControlQuery {

    /**
     * What the gate would do with {@code command} if this player typed it now, or empty when they are offline.
     *
     * <p>The command may be given with or without its leading slash, and with or without its arguments: only the
     * root is read, exactly as the gate reads it. A namespaced form such as {@code minecraft:gamemode} is answered
     * about the bare command when the module is set to close that bypass, and about the namespaced root otherwise,
     * again matching the gate.
     */
    CompletableFuture<Optional<UxmCommandCheck>> check(UUID playerId, String command);

    /**
     * Whether {@code command} would be stopped for this player, for a call site that only wants the yes or no.
     * False for an offline player, since nothing is being stopped for somebody who is not here.
     */
    CompletableFuture<Boolean> isBlocked(UUID playerId, String command);
}
