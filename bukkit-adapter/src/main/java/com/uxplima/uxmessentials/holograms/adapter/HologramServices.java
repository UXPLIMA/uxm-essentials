package com.uxplima.uxmessentials.holograms.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramAppearance;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramRefresh;
import com.uxplima.uxmessentials.holograms.application.SetHologramVisibility;
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
 * @param removeLine {@code /hologram removeline}
 * @param move {@code /hologram movehere}
 * @param appearance {@code /hologram billboard|background|shadow|brightness|scale|linewidth|viewrange}
 * @param refresh {@code /hologram refreshinterval}
 * @param visibility {@code /hologram visibility|visibilitydistance}
 */
@NullMarked
public record HologramServices(
        CreateHologram create,
        DeleteHologram delete,
        ListHolograms list,
        AddHologramLine addLine,
        SetHologramLine setLine,
        RemoveHologramLine removeLine,
        MoveHologram move,
        SetHologramAppearance appearance,
        SetHologramRefresh refresh,
        SetHologramVisibility visibility) {

    public HologramServices {
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(delete, "delete");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(addLine, "addLine");
        Objects.requireNonNull(setLine, "setLine");
        Objects.requireNonNull(removeLine, "removeLine");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(refresh, "refresh");
        Objects.requireNonNull(visibility, "visibility");
    }
}
