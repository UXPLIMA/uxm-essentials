package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.customcommands.application.port.RequirementCheck;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

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
        return menus.passes(actor, refs, arguments);
    }
}
