package com.uxplima.uxmessentials.npc.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import org.jspecify.annotations.Nullable;

/**
 * An NPC was given the servers that show it, {@code /npc scope}. Carries the new scope so a subscriber moves anything
 * riding the NPC (a hologram linked to it) off a server the NPC left, or back on one it returned to.
 *
 * @param name the name of the NPC
 * @param scope the servers that now show it, or {@code null} for every server
 */
public record NpcRescoped(NpcName name, @Nullable ServerScope scope) implements NpcEvent {

    public NpcRescoped {
        Objects.requireNonNull(name, "name");
    }
}
