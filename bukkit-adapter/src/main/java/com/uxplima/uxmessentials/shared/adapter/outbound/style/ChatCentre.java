package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * The marker an operator writes at the start of a chat line to have it drawn in the middle of the chat:
 * {@code <center>}, or {@code <centre>}, since both spellings are written.
 *
 * <p>It marks the whole line rather than wrapping part of it, because a chat line has one place to start and
 * centring half of it has no meaning. The line after the marker is rendered first and then measured, glyph by glyph,
 * by uxmLib's {@code CenteredTextPadder}, so a bold title, a player's name and a small caps word all count at the
 * width they draw.
 */
@NullMarked
public final class ChatCentre {

    private static final List<String> MARKERS = List.of("<center>", "<centre>");

    private ChatCentre() {}

    /** The rest of {@code line} when it opens with the marker, or empty when it does not. */
    public static Optional<String> afterMarker(String line) {
        Objects.requireNonNull(line, "line");
        String trimmed = line.stripLeading();
        for (String marker : MARKERS) {
            if (trimmed.regionMatches(true, 0, marker, 0, marker.length())) {
                return Optional.of(trimmed.substring(marker.length()));
            }
        }
        return Optional.empty();
    }
}
