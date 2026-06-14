package com.uxplima.uxmessentials.tablist.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * One named tablist format: the header/footer {@link TablistContent} to render, the {@link DisplayCondition} deciding
 * which viewers it applies to, a {@code priority} that breaks the field of matching formats, and the two per-player
 * tab-list overrides a format may carry — the viewer's own list-<em>name</em> template ({@code nameFormat}, how they
 * appear to everyone in the tab list) and their list <em>sort order</em> ({@code sortOrder}). A server may author
 * several formats (a staff format, a build-world format, a default) and {@link TablistFormatConfig#select} picks the
 * highest-priority one whose condition matches each viewer.
 *
 * <p>This mirrors the scoreboard's {@code SidebarBoard}: the {@code content} owns the structural header/footer concerns
 * (its {@link TablistContent#worldBlacklist() world blacklist} still suppresses the tablist within the selected format),
 * while {@code nameFormat} and {@code sortOrder} are the tablist-only additions. Both are optional: an absent
 * {@code nameFormat} leaves the viewer's vanilla list name untouched (and resets it on a switch away from a format that
 * set one), and an absent {@code sortOrder} leaves their vanilla sort order untouched.
 *
 * <p>The {@code nameFormat}, when present, is raw operator content rendered per viewer through the placeholder pipeline
 * and MiniMessage by the adapter; it may embed the {@code {player}} token (the viewer's name) and PlaceholderAPI
 * placeholders. The {@code sortOrder}, when present, is the {@code Player.setPlayerListOrder(int)} value — a positive
 * integer where a higher value sorts the player higher in the tab list (see the renderer for the confirmed semantics).
 *
 * @param name the format name, non-blank (the config map key; used only for operator-facing identification and tie-break)
 * @param condition the per-viewer gate; {@link DisplayCondition#always()} for an unconditional format
 * @param priority the selection rank; higher wins, ties broken by name (see {@link TablistFormatConfig#select})
 * @param content the operator-authored header/footer rendered when this format is selected
 * @param nameFormat the per-viewer list-name template, or empty to leave the vanilla list name untouched
 * @param sortOrder the per-viewer {@code setPlayerListOrder} value, or empty to leave the vanilla sort order untouched
 */
public record TablistFormat(
        String name,
        DisplayCondition condition,
        int priority,
        TablistContent content,
        Optional<String> nameFormat,
        OptionalInt sortOrder) {

    public TablistFormat {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(nameFormat, "nameFormat");
        Objects.requireNonNull(sortOrder, "sortOrder");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a tablist format name must not be blank");
        }
    }
}
