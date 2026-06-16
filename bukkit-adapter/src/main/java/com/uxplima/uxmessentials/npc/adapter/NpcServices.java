package com.uxplima.uxmessentials.npc.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.AddNpcAction;
import com.uxplima.uxmessentials.npc.application.ClearNpcActions;
import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.application.DeleteNpc;
import com.uxplima.uxmessentials.npc.application.ListNpcActions;
import com.uxplima.uxmessentials.npc.application.ListNpcTypeData;
import com.uxplima.uxmessentials.npc.application.ListNpcs;
import com.uxplima.uxmessentials.npc.application.MoveNpc;
import com.uxplima.uxmessentials.npc.application.RemoveNpcAction;
import com.uxplima.uxmessentials.npc.application.SetNpcClickCommand;
import com.uxplima.uxmessentials.npc.application.SetNpcEntityType;
import com.uxplima.uxmessentials.npc.application.SetNpcEquipment;
import com.uxplima.uxmessentials.npc.application.SetNpcGlowing;
import com.uxplima.uxmessentials.npc.application.SetNpcLookAtPlayer;
import com.uxplima.uxmessentials.npc.application.SetNpcPose;
import com.uxplima.uxmessentials.npc.application.SetNpcScale;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcTypeData;
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
 * @param type {@code /npc type}
 * @param command {@code /npc command}
 * @param look {@code /npc lookatplayer}
 * @param equip {@code /npc equip}
 * @param glow {@code /npc glow}
 * @param pose {@code /npc pose}
 * @param scale {@code /npc scale}
 * @param addAction {@code /npc action add}
 * @param listActions {@code /npc action list}
 * @param removeAction {@code /npc action remove}
 * @param clearActions {@code /npc action clear}
 * @param setData {@code /npc data set} / {@code /npc data clear}
 * @param listData {@code /npc data list}
 */
@NullMarked
public record NpcServices(
        CreateNpc create,
        DeleteNpc delete,
        ListNpcs list,
        MoveNpc move,
        SetNpcSkin skin,
        SetNpcEntityType type,
        SetNpcClickCommand command,
        SetNpcLookAtPlayer look,
        SetNpcEquipment equip,
        SetNpcGlowing glow,
        SetNpcPose pose,
        SetNpcScale scale,
        AddNpcAction addAction,
        ListNpcActions listActions,
        RemoveNpcAction removeAction,
        ClearNpcActions clearActions,
        SetNpcTypeData setData,
        ListNpcTypeData listData) {

    public NpcServices {
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(delete, "delete");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(skin, "skin");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(look, "look");
        Objects.requireNonNull(equip, "equip");
        Objects.requireNonNull(glow, "glow");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(addAction, "addAction");
        Objects.requireNonNull(listActions, "listActions");
        Objects.requireNonNull(removeAction, "removeAction");
        Objects.requireNonNull(clearActions, "clearActions");
        Objects.requireNonNull(setData, "setData");
        Objects.requireNonNull(listData, "listData");
    }
}
