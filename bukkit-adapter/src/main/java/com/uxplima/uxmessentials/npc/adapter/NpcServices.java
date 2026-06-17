package com.uxplima.uxmessentials.npc.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.AddNpcAction;
import com.uxplima.uxmessentials.npc.application.CenterNpc;
import com.uxplima.uxmessentials.npc.application.ClearNpcActions;
import com.uxplima.uxmessentials.npc.application.CopyNpc;
import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.application.DeleteNpc;
import com.uxplima.uxmessentials.npc.application.DescribeNpc;
import com.uxplima.uxmessentials.npc.application.FixNpc;
import com.uxplima.uxmessentials.npc.application.ListNpcActions;
import com.uxplima.uxmessentials.npc.application.ListNpcTypeData;
import com.uxplima.uxmessentials.npc.application.ListNpcs;
import com.uxplima.uxmessentials.npc.application.MoveNpc;
import com.uxplima.uxmessentials.npc.application.MoveNpcTo;
import com.uxplima.uxmessentials.npc.application.NearbyNpcs;
import com.uxplima.uxmessentials.npc.application.RemoveNpcAction;
import com.uxplima.uxmessentials.npc.application.SetNpcClickCommand;
import com.uxplima.uxmessentials.npc.application.SetNpcCollidable;
import com.uxplima.uxmessentials.npc.application.SetNpcDisplayName;
import com.uxplima.uxmessentials.npc.application.SetNpcEntityType;
import com.uxplima.uxmessentials.npc.application.SetNpcEquipment;
import com.uxplima.uxmessentials.npc.application.SetNpcGlowing;
import com.uxplima.uxmessentials.npc.application.SetNpcInteractionCooldown;
import com.uxplima.uxmessentials.npc.application.SetNpcLookAtPlayer;
import com.uxplima.uxmessentials.npc.application.SetNpcMirrorSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcPose;
import com.uxplima.uxmessentials.npc.application.SetNpcRange;
import com.uxplima.uxmessentials.npc.application.SetNpcScale;
import com.uxplima.uxmessentials.npc.application.SetNpcShowInTab;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcSkinSlim;
import com.uxplima.uxmessentials.npc.application.SetNpcState;
import com.uxplima.uxmessentials.npc.application.SetNpcTypeData;
import com.uxplima.uxmessentials.npc.application.TeleportToNpc;
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
 * @param nearby {@code /npc nearby}
 * @param info {@code /npc info}
 * @param teleport {@code /npc teleport}
 * @param move {@code /npc movehere}
 * @param copy {@code /npc copy}
 * @param center {@code /npc center}
 * @param fix {@code /npc fix}
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
 * @param moveTo {@code /npc moveto}
 * @param displayName {@code /npc displayname}
 * @param cooldown {@code /npc cooldown}
 * @param mirror {@code /npc mirror}
 * @param collidable {@code /npc collidable}
 * @param showInTab {@code /npc showintab}
 * @param range {@code /npc viewdistance} / {@code /npc turndistance}
 * @param state {@code /npc state}
 * @param skinSlim {@code /npc skinslim}
 */
@NullMarked
public record NpcServices(
        CreateNpc create,
        DeleteNpc delete,
        ListNpcs list,
        NearbyNpcs nearby,
        DescribeNpc info,
        TeleportToNpc teleport,
        MoveNpc move,
        CopyNpc copy,
        CenterNpc center,
        FixNpc fix,
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
        ListNpcTypeData listData,
        MoveNpcTo moveTo,
        SetNpcDisplayName displayName,
        SetNpcInteractionCooldown cooldown,
        SetNpcMirrorSkin mirror,
        SetNpcCollidable collidable,
        SetNpcShowInTab showInTab,
        SetNpcRange range,
        SetNpcState state,
        SetNpcSkinSlim skinSlim) {

    public NpcServices {
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(delete, "delete");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(nearby, "nearby");
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(teleport, "teleport");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(copy, "copy");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(fix, "fix");
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
        Objects.requireNonNull(moveTo, "moveTo");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(collidable, "collidable");
        Objects.requireNonNull(showInTab, "showInTab");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(skinSlim, "skinSlim");
    }
}
