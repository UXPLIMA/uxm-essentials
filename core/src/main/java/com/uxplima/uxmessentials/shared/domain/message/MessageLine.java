package com.uxplima.uxmessentials.shared.domain.message;

import java.util.Map;
import java.util.Objects;

/**
 * A catalogue line chosen together with the values it is filled with, for a caller that decides which line to send
 * in one place and sends it in another.
 *
 * @param key the catalogue line
 * @param args the placeholder values it is filled with
 */
public record MessageLine(MessageKey key, Map<String, String> args) {

    public MessageLine {
        Objects.requireNonNull(key, "key");
        args = Map.copyOf(Objects.requireNonNull(args, "args"));
    }
}
