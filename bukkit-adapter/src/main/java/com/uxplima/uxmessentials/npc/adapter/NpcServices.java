package com.uxplima.uxmessentials.npc.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.application.DeleteNpc;
import com.uxplima.uxmessentials.npc.application.ListNpcs;
import com.uxplima.uxmessentials.npc.application.MoveNpc;
import com.uxplima.uxmessentials.npc.application.SetNpcClickCommand;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed npc use cases the single {@code /npc} command shares, built once per module start by
 * {@code NpcWiring} from the kernel ports, the jOOQ repository, and the packet-backed renderer. Held so every
 * subcommand reads the same use cases; the npc context keeps no other adapter-side runtime state beyond the
 * renderer (which the {@code Wired} bundle drains on stop).
 *
 * @param create {@code /npc create}
 * @param delete {@code /npc delete}
 * @param list {@code /npc list}
 * @param move {@code /npc movehere}
 * @param skin {@code /npc skin}
 * @param command {@code /npc command}
 */
@NullMarked
public record NpcServices(
        CreateNpc create, DeleteNpc delete, ListNpcs list, MoveNpc move, SetNpcSkin skin, SetNpcClickCommand command) {

    public NpcServices {
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(delete, "delete");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(skin, "skin");
        Objects.requireNonNull(command, "command");
    }
}
