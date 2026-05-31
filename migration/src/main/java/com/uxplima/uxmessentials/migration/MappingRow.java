package com.uxplima.uxmessentials.migration;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * One row in a source's {@code SupportedMappings} table (docs/12-migration §5): a source field, the
 * uxmEssentials target aggregate, the owning context, the mapper that performs the translation, and the
 * identity the conflict policy keys on. The table is the single source of truth for what a source
 * claims to migrate; the drift guard ties each row to a doc row and a fixture (docs/12-migration §8.1).
 *
 * @param sourceField the foreign field as the source names it ({@code "homes.<name>"})
 * @param target the uxmEssentials aggregate it maps to ({@code "Home"})
 * @param context the bounded context that owns the target ({@code "homes"})
 * @param mapper the mapper class name that performs the translation ({@code "UserdataMapper"})
 * @param conflictUnit the identity a collision is resolved per ({@code "(uuid, homeName)"})
 */
@NullMarked
public record MappingRow(String sourceField, String target, String context, String mapper, String conflictUnit) {

    public MappingRow {
        Objects.requireNonNull(sourceField, "sourceField");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(conflictUnit, "conflictUnit");
    }
}
