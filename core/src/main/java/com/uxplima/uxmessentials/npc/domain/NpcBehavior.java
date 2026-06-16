package com.uxplima.uxmessentials.npc.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The "how an NPC acts" half of the {@link Npc} aggregate: the single click command, whether the NPC rotates to
 * face nearby viewers, and the ordered list of typed actions a click runs. Grouping these behavioural fields into
 * one immutable value object keeps the {@link Npc} aggregate small while leaving the public surface unchanged —
 * {@code Npc} delegates every behavioural transition and accessor here. A behavior is a value object: each
 * {@code with*} produces a new instance rather than mutating.
 *
 * <p>{@code clickCommand} is the raw command text run when a player clicks the NPC, or {@code null} for none —
 * running it is an adapter concern, so the domain only carries the binding. {@code lookAtPlayer} controls whether
 * the fake player turns to face each nearby viewer; it defaults to {@code true} so a freshly created NPC tracks
 * players out of the box. {@code actions} is the ordered list of {@link NpcAction}s a click runs, the richer
 * mechanism alongside the single {@code clickCommand} (which still runs first). The list is copied defensively on
 * construction so the snapshot is immutable.
 */
public record NpcBehavior(@Nullable String clickCommand, boolean lookAtPlayer, List<NpcAction> actions) {

    public NpcBehavior {
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }

    /** The default behavior for a freshly created NPC: no command, looking at players, no actions. */
    static NpcBehavior defaults() {
        return new NpcBehavior(null, true, List.of());
    }

    NpcBehavior withClickCommand(@Nullable String newCommand) {
        return new NpcBehavior(newCommand, lookAtPlayer, actions);
    }

    NpcBehavior withLookAtPlayer(boolean newLookAtPlayer) {
        return new NpcBehavior(clickCommand, newLookAtPlayer, actions);
    }

    NpcBehavior withActionAdded(NpcAction action) {
        Objects.requireNonNull(action, "action");
        List<NpcAction> updated = new ArrayList<>(actions);
        updated.add(action);
        return new NpcBehavior(clickCommand, lookAtPlayer, updated);
    }

    NpcBehavior withActionRemovedAt(int index) {
        if (index < 0 || index >= actions.size()) {
            throw new IndexOutOfBoundsException("action index out of range: " + index);
        }
        List<NpcAction> updated = new ArrayList<>(actions);
        updated.remove(index);
        return new NpcBehavior(clickCommand, lookAtPlayer, updated);
    }

    NpcBehavior withActionsCleared() {
        return new NpcBehavior(clickCommand, lookAtPlayer, List.of());
    }

    boolean hasClickCommand() {
        return clickCommand != null && !clickCommand.isBlank();
    }

    boolean hasActions() {
        return !actions.isEmpty();
    }
}
