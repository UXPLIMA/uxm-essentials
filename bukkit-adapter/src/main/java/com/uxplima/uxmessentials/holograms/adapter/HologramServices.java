package com.uxplima.uxmessentials.holograms.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.AddHologramPage;
import com.uxplima.uxmessentials.holograms.application.CenterHologram;
import com.uxplima.uxmessentials.holograms.application.CopyHologram;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.DescribeHologram;
import com.uxplima.uxmessentials.holograms.application.InsertHologramLine;
import com.uxplima.uxmessentials.holograms.application.LinkHologramToNpc;
import com.uxplima.uxmessentials.holograms.application.ListHologramPages;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.ManageHologramViewer;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.NearbyHolograms;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramPage;
import com.uxplima.uxmessentials.holograms.application.RotateHologram;
import com.uxplima.uxmessentials.holograms.application.SetHologramAppearance;
import com.uxplima.uxmessentials.holograms.application.SetHologramClickCommand;
import com.uxplima.uxmessentials.holograms.application.SetHologramLeaderboard;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramModel;
import com.uxplima.uxmessentials.holograms.application.SetHologramRefresh;
import com.uxplima.uxmessentials.holograms.application.SetHologramVisibility;
import com.uxplima.uxmessentials.holograms.application.TeleportToHologram;
import com.uxplima.uxmessentials.holograms.application.UnlinkHologramFromNpc;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed holograms use cases the single {@code /hologram} command shares, built once per module
 * start by {@code HologramsWiring} from the kernel ports, the jOOQ repository, and the uxmLib-backed
 * renderer. Held so every subcommand reads the same use cases; the holograms context keeps no other
 * adapter-side runtime state beyond the renderer (which the {@code Wired} bundle drains on stop).
 *
 * @param create {@code /hologram create}
 * @param delete {@code /hologram delete}
 * @param list {@code /hologram list}
 * @param addLine {@code /hologram addline}
 * @param setLine {@code /hologram setline}
 * @param insertLine {@code /hologram insertline}
 * @param removeLine {@code /hologram removeline}
 * @param move {@code /hologram movehere}
 * @param center {@code /hologram center}
 * @param teleport {@code /hologram teleport}
 * @param copy {@code /hologram copy}
 * @param rotate {@code /hologram rotate}
 * @param info {@code /hologram info}
 * @param nearby {@code /hologram nearby}
 * @param appearance {@code /hologram billboard|background|shadow|brightness|scale|linewidth|viewrange}
 * @param refresh {@code /hologram refreshinterval}
 * @param visibility {@code /hologram visibility|visibilitydistance}
 * @param viewers {@code /hologram show|hide}
 * @param model {@code /hologram item|block}
 * @param linkNpc {@code /hologram linknpc}
 * @param unlinkNpc {@code /hologram unlinknpc}
 * @param addPage {@code /hologram page <name> add}
 * @param removePage {@code /hologram page <name> remove}
 * @param listPages {@code /hologram page <name> list}
 */
@NullMarked
public record HologramServices(
        CreateHologram create,
        DeleteHologram delete,
        ListHolograms list,
        AddHologramLine addLine,
        SetHologramLine setLine,
        InsertHologramLine insertLine,
        RemoveHologramLine removeLine,
        MoveHologram move,
        CenterHologram center,
        TeleportToHologram teleport,
        CopyHologram copy,
        RotateHologram rotate,
        DescribeHologram info,
        NearbyHolograms nearby,
        SetHologramAppearance appearance,
        SetHologramRefresh refresh,
        SetHologramVisibility visibility,
        ManageHologramViewer viewers,
        SetHologramModel model,
        SetHologramClickCommand clickCommand,
        SetHologramLeaderboard leaderboard,
        LinkHologramToNpc linkNpc,
        UnlinkHologramFromNpc unlinkNpc,
        AddHologramPage addPage,
        RemoveHologramPage removePage,
        ListHologramPages listPages) {

    public HologramServices {
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(delete, "delete");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(addLine, "addLine");
        Objects.requireNonNull(setLine, "setLine");
        Objects.requireNonNull(insertLine, "insertLine");
        Objects.requireNonNull(removeLine, "removeLine");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(teleport, "teleport");
        Objects.requireNonNull(copy, "copy");
        Objects.requireNonNull(rotate, "rotate");
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(nearby, "nearby");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(refresh, "refresh");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(viewers, "viewers");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(clickCommand, "clickCommand");
        Objects.requireNonNull(leaderboard, "leaderboard");
        Objects.requireNonNull(linkNpc, "linkNpc");
        Objects.requireNonNull(unlinkNpc, "unlinkNpc");
        Objects.requireNonNull(addPage, "addPage");
        Objects.requireNonNull(removePage, "removePage");
        Objects.requireNonNull(listPages, "listPages");
    }
}
