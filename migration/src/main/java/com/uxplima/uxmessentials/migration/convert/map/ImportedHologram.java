package com.uxplima.uxmessentials.migration.convert.map;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import org.jspecify.annotations.NullMarked;

/**
 * One parsed-and-mapped hologram, expressed as the domain {@link Hologram} aggregate the holograms context
 * owns. The importer writes it through the same repository {@code /hologram create} upserts through, so an
 * imported hologram can never reach a state a normal command could not. The mapped hologram is a TEXT
 * hologram at the source's location with the source's text lines and uxmEssentials' default styling.
 *
 * @param hologram the mapped hologram aggregate
 */
@NullMarked
public record ImportedHologram(Hologram hologram) {

    public ImportedHologram {
        Objects.requireNonNull(hologram, "hologram");
    }
}
