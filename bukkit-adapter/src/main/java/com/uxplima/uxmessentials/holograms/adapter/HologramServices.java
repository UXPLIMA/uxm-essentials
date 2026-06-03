package com.uxplima.uxmessentials.holograms.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
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
 */
@NullMarked
public record HologramServices(
        CreateHologram create,
        DeleteHologram delete,
        ListHolograms list,
        AddHologramLine addLine,
        SetHologramLine setLine,
        RemoveHologramLine removeLine,
        MoveHologram move) {

    public HologramServices {
        Objects.requireNonNull(create, "create");
        Objects.requireNonNull(delete, "delete");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(addLine, "addLine");
        Objects.requireNonNull(setLine, "setLine");
        Objects.requireNonNull(removeLine, "removeLine");
        Objects.requireNonNull(move, "move");
    }
}
