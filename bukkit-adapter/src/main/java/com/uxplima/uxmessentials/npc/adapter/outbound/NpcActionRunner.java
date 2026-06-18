package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.List;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.action.ClickAction;

/**
 * The seam between the click-interaction listener and the engine that runs an NPC's ordered action chain. Pulling
 * it behind this port keeps the listener's resolve-and-dispatch flow unit-testable against a recording fake, with
 * the Bukkit/Adventure effects (console/player command dispatch, message/action-bar/title components, sounds,
 * cross-server connect) isolated in the single implementation.
 */
public interface NpcActionRunner {

    /**
     * Run, in order, every action in {@code actions} whose trigger matches the click described by {@code attack}
     * (true = a left-click/attack, false = a right-click/interact) for {@code viewer}. Each action is fail-soft:
     * one bad action is logged and skipped, never aborting the rest of the chain or throwing to the caller. Must
     * be called on {@code viewer}'s region thread, where the Bukkit effects are safe.
     */
    void run(Player viewer, List<ClickAction> actions, boolean attack);
}
