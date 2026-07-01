package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A parsed reference to engine behaviour — an action, condition, placeholder, or list source the registries
 * later resolve by {@link #id()}. A reference carries optional string arguments so a spec can write a single
 * compact token instead of a HOCON block.
 */
public record Ref(String id, Map<String, String> args) {

    public Ref {
        Objects.requireNonNull(id, "id");
        args = Map.copyOf(Objects.requireNonNull(args, "args"));
    }

    public static Ref parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("blank ref");
        }
        int colon = trimmed.indexOf(':');
        // A namespaced feature ref (e.g. warp:teleport) keeps the whole token as the id; a generic ref
        // (sound:..., command:...) splits one arg off. We treat a known generic prefix as arg-bearing.
        if (colon < 0) {
            return new Ref(trimmed, Map.of());
        }
        String head = trimmed.substring(0, colon);
        String tail = trimmed.substring(colon + 1);
        if (head.contains(":") || isGeneric(head)) {
            return new Ref(head, Map.of("value", tail));
        }
        return new Ref(trimmed, Map.of()); // namespaced feature ref, args empty
    }

    public static Ref of(String id, Map<String, String> args) {
        return new Ref(id, args);
    }

    public String value() {
        return args.getOrDefault("value", "");
    }

    private static boolean isGeneric(String head) {
        return Set.of("sound", "command", "console", "message", "perm", "open", "expr", "refresh-slot")
                .contains(head);
    }
}
