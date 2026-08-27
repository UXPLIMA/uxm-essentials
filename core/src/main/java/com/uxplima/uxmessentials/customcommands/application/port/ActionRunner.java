package com.uxplima.uxmessentials.customcommands.application.port;

import java.util.Map;

import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Runs an action chain for one actor. The adapter hands each step to the menu engine's action vocabulary, expanding
 * the {@code %arg_<name>%} tokens from {@code arguments} first, and schedules a step that carries an offset instead
 * of running it inline.
 *
 * <p>A step the actor cannot perform (an item given to a console, a sound played to nobody) is skipped with one log
 * line rather than failing the rest of the chain, so a half-console-safe command still does what it can.
 */
public interface ActionRunner {

    /** Run every step of {@code chain} for {@code actor}, resolving argument tokens from {@code arguments}. */
    void run(PlayerRef actor, ActionChain chain, Map<String, String> arguments);
}
