package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmLocation;

/**
 * Creating, moving, dressing and removing NPCs.
 *
 * <p>Every verb names the player it acts as, and runs the same use case {@code /npc} runs for them: the per-player
 * creation limit applies to a create, and the actor is recorded as the NPC's owner. Pass the UUID of whoever asked
 * for it; a plugin acting on its own behalf should pass the UUID of the player whose action triggered it, so the
 * limit lands on somebody rather than on nobody.
 *
 * <p>What is here is the shape of an NPC, not every render detail. Equipment, pose, scale and the rest stay behind
 * {@code /npc} because they are a long tail of knobs whose meaning is the renderer's, and publishing them would
 * freeze that renderer's vocabulary into a compatibility promise.
 */
public interface UxmNpcActions {

    /**
     * Create an NPC standing at {@code where}, wearing no skin.
     *
     * <p>Refused with {@link UxmFailure#ALREADY_EXISTS} for a name already taken and {@link UxmFailure#REFUSED}
     * when the actor is at their NPC limit, which is the same pair {@code /npc create} answers with.
     */
    CompletableFuture<UxmOutcome> create(UUID actorId, String name, UxmLocation where);

    /** Remove the NPC and despawn it for everybody who can see it. */
    CompletableFuture<UxmOutcome> delete(UUID actorId, String name);

    /** Move the NPC to {@code where}, keeping everything else about it. */
    CompletableFuture<UxmOutcome> move(UUID actorId, String name, UxmLocation where);

    /**
     * Dress the NPC in the skin the account {@code skinOwner} wears, looked up the same way {@code /npc skin} looks
     * it up, so it works on an offline-mode server too. A name no account answers to is
     * {@link UxmFailure#NOT_FOUND}, and only a fake-player NPC can wear a skin.
     */
    CompletableFuture<UxmOutcome> setSkin(UUID actorId, String name, String skinOwner);

    /** Take the skin back off, leaving the default. */
    CompletableFuture<UxmOutcome> clearSkin(UUID actorId, String name);

    /** Show {@code displayName} above the NPC instead of its id. MiniMessage is parsed at render time. */
    CompletableFuture<UxmOutcome> setDisplayName(UUID actorId, String name, String displayName);

    /** Show nothing above the NPC at all, which is different from showing its id again. */
    CompletableFuture<UxmOutcome> hideDisplayName(UUID actorId, String name);

    /** Show the NPC's id above it again, which is the default. */
    CompletableFuture<UxmOutcome> clearDisplayName(UUID actorId, String name);

    /** Run {@code command} when a player clicks the NPC. Give it without a leading slash. */
    CompletableFuture<UxmOutcome> setClickCommand(UUID actorId, String name, String command);

    /** Stop a click running a command. Any typed click actions bound to the NPC still run. */
    CompletableFuture<UxmOutcome> clearClickCommand(UUID actorId, String name);
}
