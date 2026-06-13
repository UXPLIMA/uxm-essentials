package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import java.util.Objects;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import org.jspecify.annotations.NullMarked;

/**
 * One parsed-and-mapped EssentialsX kit, expressed as the domain {@link KitDefinition} the {@code kits}
 * context owns. The kit's item stacks are carried as the kit context's opaque {@code KitItem} payloads
 * (the EssentialsX item descriptor strings); the bukkit-side writer round-trips them through Bukkit's
 * item codec exactly as a live {@code /kit create} would, so this mapper stays free of any
 * {@code ItemStack} type (docs/12-migration §5.1).
 *
 * @param definition the mapped kit definition
 */
@NullMarked
public record ImportedKit(KitDefinition definition) {

    public ImportedKit {
        Objects.requireNonNull(definition, "definition");
    }
}
