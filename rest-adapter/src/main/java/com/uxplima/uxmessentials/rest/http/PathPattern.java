package com.uxplima.uxmessentials.rest.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A route's path, with the parts that vary written as {@code {name}}.
 *
 * <p>Matched segment by segment rather than by regular expression, so {@code /players/{uuid}/homes} cannot
 * accidentally match {@code /players/a/b/homes} and a name with a slash in it cannot smuggle its way into a
 * parameter. A pattern with no braces is a literal path and matches only itself.
 */
public final class PathPattern {

    private final String source;
    private final List<String> segments;

    private PathPattern(String source, List<String> segments) {
        this.source = source;
        this.segments = List.copyOf(segments);
    }

    /** Compile {@code pattern}, which must start with a slash. */
    public static PathPattern of(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        if (!pattern.startsWith("/")) {
            throw new IllegalArgumentException("a route path must start with a slash: " + pattern);
        }
        return new PathPattern(pattern, split(pattern));
    }

    /** The pattern as it was written, which is what the route table prints. */
    public String source() {
        return source;
    }

    /** The parameters {@code path} fills in, or empty when it is a different path altogether. */
    public Optional<Map<String, String>> match(String path) {
        List<String> parts = split(path);
        if (parts.size() != segments.size()) {
            return Optional.empty();
        }
        Map<String, String> parameters = new HashMap<>();
        for (int at = 0; at < segments.size(); at++) {
            String segment = segments.get(at);
            String part = parts.get(at);
            if (isParameter(segment)) {
                if (part.isEmpty()) {
                    return Optional.empty();
                }
                parameters.put(segment.substring(1, segment.length() - 1), part);
            } else if (!segment.equals(part)) {
                return Optional.empty();
            }
        }
        return Optional.of(Map.copyOf(parameters));
    }

    private static boolean isParameter(String segment) {
        return segment.length() > 2 && segment.charAt(0) == '{' && segment.charAt(segment.length() - 1) == '}';
    }

    /** Split on slashes, ignoring the leading one and a trailing one, so {@code /warps/} is {@code /warps}. */
    private static List<String> split(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/", -1)) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    @Override
    public String toString() {
        return source;
    }
}
