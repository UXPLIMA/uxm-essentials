package com.uxplima.uxmessentials.migration.convert.map;

import java.util.Objects;

import com.uxplima.uxmessentials.warps.domain.Warp;
import org.jspecify.annotations.NullMarked;

/**
 * One parsed-and-mapped server warp, expressed as the domain {@link Warp} aggregate the warps context
 * owns. The importer writes it through the same application service {@code /setwarp} uses, so an imported
 * warp can never reach a state a normal command could not.
 *
 * @param warp the mapped warp aggregate
 */
@NullMarked
public record ImportedWarp(Warp warp) {

    public ImportedWarp {
        Objects.requireNonNull(warp, "warp");
    }
}
