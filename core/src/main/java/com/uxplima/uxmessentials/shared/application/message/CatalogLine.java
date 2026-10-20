package com.uxplima.uxmessentials.shared.application.message;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A line an operator wrote that names a catalogue entry instead of carrying its own words: {@code @key}, then any
 * number of {@code name=value} arguments the entry fills its {@code {name}} tokens from. A value with a space in it
 * is quoted with {@code "} or {@code '}, and a backslash keeps a quote inside one.
 *
 * <p>The words live in {@code messages_<lang>.conf}, so one line reads in every viewer's own language, and the
 * arguments are what let one entry serve several lines: {@code @custom.online-players server=skyblock} and
 * {@code server=bedwars} share a template rather than each needing one per language.
 *
 * <p>Only the whole line counts. A line that opens with an at sign and then carries prose ({@code @everyone meet at
 * spawn}) is not a reference, because a reference has nothing after its key but arguments; that keeps every line an
 * operator wrote before keys existed meaning what it meant.
 *
 * @param key the catalogue key, as written after the at sign
 * @param arguments the values for the entry's {@code {name}} tokens, in the order written
 */
public record CatalogLine(String key, Map<String, String> arguments) {

    private static final char MARK = '@';
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    public CatalogLine {
        Objects.requireNonNull(key, "key");
        arguments = Map.copyOf(arguments);
    }

    /** The reference {@code line} spells, or empty when the line carries its own words. */
    public static Optional<CatalogLine> parse(String line) {
        Objects.requireNonNull(line, "line");
        String text = line.strip();
        if (text.length() < 2 || text.charAt(0) != MARK) {
            return Optional.empty();
        }
        int keyEnd = nextSpace(text, 1);
        String key = text.substring(1, keyEnd);
        if (!KEY.matcher(key).matches()) {
            return Optional.empty();
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        int index = keyEnd;
        while (true) {
            index = skipSpaces(text, index);
            if (index >= text.length()) {
                return Optional.of(new CatalogLine(key, arguments));
            }
            int equals = text.indexOf('=', index);
            if (equals < 0 || !NAME.matcher(text.substring(index, equals)).matches()) {
                return Optional.empty();
            }
            String name = text.substring(index, equals);
            int valueStart = equals + 1;
            if (valueStart >= text.length() || Character.isWhitespace(text.charAt(valueStart))) {
                return Optional.empty();
            }
            char first = text.charAt(valueStart);
            if (first == '"' || first == '\'') {
                StringBuilder value = new StringBuilder();
                int end = quoted(text, valueStart + 1, first, value);
                if (end < 0) {
                    return Optional.empty();
                }
                arguments.put(name, value.toString());
                index = end;
            } else {
                int end = nextSpace(text, valueStart);
                arguments.put(name, text.substring(valueStart, end));
                index = end;
            }
        }
    }

    /** Read a quoted value into {@code into}; the index just past the closing quote, or -1 when it never closes. */
    private static int quoted(String text, int from, char quote, StringBuilder into) {
        for (int index = from; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\\' && index + 1 < text.length()) {
                into.append(text.charAt(++index));
            } else if (current == quote) {
                return index + 1;
            } else {
                into.append(current);
            }
        }
        return -1;
    }

    private static int nextSpace(String text, int from) {
        int index = from;
        while (index < text.length() && !Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int skipSpaces(String text, int from) {
        int index = from;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }
}
