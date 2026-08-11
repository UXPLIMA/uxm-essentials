package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmLocation;

/**
 * Creating, moving, editing and removing holograms.
 *
 * <p>Every verb names the player it acts as and runs the same use case {@code /hologram} runs, so a consumer that
 * puts a hologram up gets one indistinguishable from a hand-made one: it renders the same way, it is stored in the
 * same table, and an operator can edit or delete it with the ordinary commands afterwards.
 *
 * <p>Line numbers count from one, the way the commands number them and the way the hologram reads on screen.
 *
 * <p>Text is stored as given, MiniMessage and placeholders included; both are resolved per viewer at render time. A
 * hologram carrying a placeholder needs a refresh interval to keep up with it, which is set with
 * {@code /hologram refresh} rather than from here.
 */
public interface UxmHologramsActions {

    /**
     * Create a text hologram at {@code where} showing one line.
     *
     * <p>One line rather than a list, because a hologram must always have at least one and starting with exactly
     * that makes the rule impossible to trip over. Add the rest with {@link #addLine}.
     */
    CompletableFuture<UxmOutcome> create(UUID actorId, String name, UxmLocation where, String firstLine);

    /** Remove the hologram and stop rendering it for everybody. */
    CompletableFuture<UxmOutcome> delete(UUID actorId, String name);

    /** Move the hologram to {@code where}, keeping its text and everything else. */
    CompletableFuture<UxmOutcome> move(UUID actorId, String name, UxmLocation where);

    /** Add a line to the bottom of the hologram. */
    CompletableFuture<UxmOutcome> addLine(UUID actorId, String name, String text);

    /** Replace line {@code line}, counting from one. Out of range is {@link UxmFailure#NOT_FOUND}. */
    CompletableFuture<UxmOutcome> setLine(UUID actorId, String name, int line, String text);

    /**
     * Remove line {@code line}, counting from one. Removing the last remaining line is refused with
     * {@link UxmFailure#REFUSED} rather than leaving an invisible hologram behind: delete it instead.
     */
    CompletableFuture<UxmOutcome> removeLine(UUID actorId, String name, int line);

    /** Run {@code command} when a player clicks the hologram. Give it without a leading slash. */
    CompletableFuture<UxmOutcome> setClickCommand(UUID actorId, String name, String command);

    /** Stop a click running a command. Any typed click actions bound to the hologram still run. */
    CompletableFuture<UxmOutcome> clearClickCommand(UUID actorId, String name);
}
