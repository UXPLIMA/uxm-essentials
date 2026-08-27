package com.uxplima.uxmessentials.customcommands.application.port;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Evaluates the requirement refs a definition declares, in the menu engine's requirement vocabulary. The adapter
 * asks the engine's condition registry, so a custom command gates on exactly what a menu item gates on.
 *
 * <p>Evaluation is fail-closed: a requirement naming something no binding provides reads as unmet, because letting
 * an unknown gate pass would turn an operator typo into an open command.
 */
public interface RequirementCheck {

    /** Whether {@code actor} meets every requirement, with {@code arguments} available to the refs. */
    boolean passes(PlayerRef actor, List<String> requirements, Map<String, String> arguments);
}
