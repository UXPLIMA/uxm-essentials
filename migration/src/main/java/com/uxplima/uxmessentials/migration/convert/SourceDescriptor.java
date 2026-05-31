package com.uxplima.uxmessentials.migration.convert;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * Human-readable description of a source and the surface it migrates, surfaced by {@code /uxmess
 * import --list}. The {@code surfaces} are the operator-facing names of the contexts a source seeds
 * ({@code "homes"}, {@code "balance"}, {@code "warps"}, …), drawn from the source's
 * {@code SupportedMappings} table so the listing never drifts from what the importer actually does.
 *
 * @param sourceId the source this describes
 * @param displayName the operator-facing name ({@code "EssentialsX"})
 * @param layout a short note on the on-disk layout the source expects
 * @param surfaces the contexts this source migrates into, for the {@code --list} output
 */
@NullMarked
public record SourceDescriptor(SourceId sourceId, String displayName, String layout, List<String> surfaces) {

    public SourceDescriptor {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(layout, "layout");
        surfaces = List.copyOf(Objects.requireNonNull(surfaces, "surfaces"));
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}
