package com.uxplima.uxmessentials.persistence.moderation;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
import org.jspecify.annotations.Nullable;

/**
 * The stored form of a {@link DrawnReason}: the {@code reason_key} and {@code reason_args} columns of the sanction
 * tables. The arguments are {@code name=value} pairs joined by {@code &}, each side percent-encoded as UTF-8, so a
 * warning's own reason may hold any character, and in name order, so one reason always stores the same text.
 *
 * <p>A row whose key names no reason line, or whose arguments do not decode, reads as no drawn reason: the sanction
 * then shows its {@code reason} text, which is always written beside them.
 */
final class ReasonLines {

    private ReasonLines() {}

    static @Nullable String key(Optional<DrawnReason> drawn) {
        return drawn.map(DrawnReason::key).orElse(null);
    }

    static @Nullable String args(Optional<DrawnReason> drawn) {
        return drawn.map(reason -> new TreeMap<>(reason.args())
                        .entrySet().stream()
                                .map(arg -> encode(arg.getKey()) + "=" + encode(arg.getValue()))
                                .collect(Collectors.joining("&")))
                .orElse(null);
    }

    static Optional<DrawnReason> read(@Nullable String key, @Nullable String args) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return DrawnReason.stored(key, decode(args == null ? "" : args));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static Map<String, String> decode(String args) {
        Map<String, String> decoded = new LinkedHashMap<>();
        if (args.isEmpty()) {
            return decoded;
        }
        for (String pair : args.split("&", -1)) {
            int split = pair.indexOf('=');
            if (split < 0) {
                throw new IllegalArgumentException("an argument without a value: " + pair);
            }
            decoded.put(
                    URLDecoder.decode(pair.substring(0, split), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8));
        }
        return decoded;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
