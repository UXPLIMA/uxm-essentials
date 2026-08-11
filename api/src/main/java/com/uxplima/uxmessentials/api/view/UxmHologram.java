package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * One hologram as the plugin stores it: its name, where it floats, what it is made of, and the text it shows.
 *
 * <p>The lines are the raw stored text, before placeholders are filled in and before MiniMessage is parsed. That is
 * deliberate: a placeholder resolves per viewer, so there is no single rendered answer to publish, and a consumer
 * mirroring a hologram elsewhere wants the source rather than one player's view of it.
 *
 * <p>{@code content} carries the single item, block, head texture or entity type for the kinds that have one, and
 * is empty for a text hologram, whose text is in {@link #lines()}.
 *
 * @param name the hologram's id, which is what {@code /hologram} commands take and what the API takes here
 * @param location where it floats
 * @param type what it is made of
 * @param lines the stored text lines, top to bottom, empty for a hologram that shows no text
 * @param content the item, block, head texture or entity type for a non-text hologram, otherwise empty
 * @param linkedNpc the NPC it follows instead of anchoring to its own location, or empty
 * @param clickCommand the command a click runs, or empty when a click runs none
 * @param actions how many typed click actions are bound to it, which run after {@code clickCommand}
 * @param pages how many pages it carries, one for an ordinary hologram
 * @param refreshIntervalTicks how often it re-renders, zero for one that renders once and stays
 * @param createdAt when it was created
 */
@NullMarked
public record UxmHologram(
        String name,
        UxmLocation location,
        UxmHologramType type,
        List<String> lines,
        Optional<String> content,
        Optional<String> linkedNpc,
        Optional<String> clickCommand,
        int actions,
        int pages,
        int refreshIntervalTicks,
        Instant createdAt) {

    public UxmHologram {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(type, "type");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(linkedNpc, "linkedNpc");
        Objects.requireNonNull(clickCommand, "clickCommand");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Whether it re-renders on a cadence rather than rendering once, which is what a placeholder line needs. */
    public boolean refreshes() {
        return refreshIntervalTicks > 0;
    }
}
