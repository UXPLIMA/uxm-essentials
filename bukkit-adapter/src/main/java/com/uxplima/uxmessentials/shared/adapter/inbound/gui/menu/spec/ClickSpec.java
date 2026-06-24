package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The actions and conditions bound to an item's click gestures, keyed by {@link ClickKind}. A gesture's
 * effective action list is its own list followed by whatever is bound to {@link ClickKind#ANY}, so authors can
 * share behaviour across every gesture without repeating it.
 */
public record ClickSpec(Map<ClickKind, List<Ref>> actions, Map<ClickKind, List<Ref>> conditions) {

    public ClickSpec {
        actions = copyImmutable(Objects.requireNonNull(actions, "actions"));
        conditions = copyImmutable(Objects.requireNonNull(conditions, "conditions"));
    }

    /**
     * The actions that should fire for {@code kind}: the gesture's own list first, then the shared {@code ANY}
     * list. The result is a fresh immutable list so callers can't mutate the underlying spec.
     */
    public List<Ref> actionsFor(ClickKind kind) {
        Objects.requireNonNull(kind, "kind");
        List<Ref> merged = new ArrayList<>(actions.getOrDefault(kind, List.of()));
        merged.addAll(actions.getOrDefault(ClickKind.ANY, List.of()));
        return List.copyOf(merged);
    }

    private static Map<ClickKind, List<Ref>> copyImmutable(Map<ClickKind, List<Ref>> source) {
        Map<ClickKind, List<Ref>> copy = new java.util.EnumMap<>(ClickKind.class);
        source.forEach((kind, refs) ->
                copy.put(Objects.requireNonNull(kind, "kind"), List.copyOf(Objects.requireNonNull(refs, "refs"))));
        return Map.copyOf(copy);
    }
}
