package com.uxplima.uxmessentials.shared.adapter.outbound.update;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NullMarked;

/**
 * Extracts the release tag string from an update-source JSON body. GitHub's
 * {@code /releases/latest} returns a {@code "tag_name"} field; a generic release endpoint may instead expose a
 * {@code "version"} field, so both are accepted with {@code tag_name} preferred.
 *
 * <p>This is a deliberately small, dependency-free string scan rather than a full JSON parse: the only datum the
 * checker needs is one short version string, the body is operator-trusted (their own configured endpoint), and the
 * fraud-webhook path already establishes the no-JSON-library convention. A body missing both fields, or one whose
 * field value is not a quoted string, yields {@link Optional#empty()} so the caller logs a warning rather than
 * comparing garbage. The downstream {@code Version.parse} still validates the extracted string is a real SemVer.
 */
@NullMarked
final class ReleaseTag {

    private static final Pattern TAG_NAME = quotedField("tag_name");
    private static final Pattern VERSION = quotedField("version");

    private ReleaseTag() {}

    /** The release tag from {@code json}: the {@code tag_name} value, else the {@code version} value, else empty. */
    static Optional<String> from(String json) {
        Objects.requireNonNull(json, "json");
        return firstMatch(TAG_NAME, json).or(() -> firstMatch(VERSION, json));
    }

    private static Optional<String> firstMatch(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = matcher.group(1).strip();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static Pattern quotedField(String field) {
        // "field" : "value" — tolerant of whitespace around the colon; the value is any run without a quote.
        return Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
    }
}
