package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.customcommands.application.port.RequirementCheck;
import com.uxplima.uxmessentials.shared.adapter.outbound.LivePlayers;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.spec.Ref;

/**
 * Evaluates a definition's {@code requirements} through the menu engine's condition registry, so a token means the
 * same thing in a command file as it does on a menu item. A command that requires nothing never touches the engine.
 */
public final class MenuRequirementCheck implements RequirementCheck {

    private final Menus menus;

    public MenuRequirementCheck(Menus menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    @Override
    public boolean passes(PlayerRef actor, List<String> requirements, Map<String, String> arguments) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(arguments, "arguments");
        if (requirements.isEmpty()) {
            return true;
        }
        List<Ref> refs = new ArrayList<>();
        for (String token : requirements) {
            refs.add(Ref.parse(token));
        }
        // The engine gates on a live player. An actor who has left cannot satisfy a requirement, and a command
        // whose requirements cannot be read must not run, so their absence reads as a refusal rather than a pass.
        return LivePlayers.of(actor)
                .map(live -> menus.passes(live, refs, arguments))
                .orElse(false);
    }
}
