package com.uxplima.uxmessentials.worlds.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Computes the next raw value when a player clicks a property button in the GUI world editor. Pure:
 * given the property descriptor, the current raw value, the action, and the candidate world names,
 * it returns the next raw string the property's {@code decode} accepts (or {@code ""} for an empty
 * string property). The branch is chosen by property shape: a property with suggestions cycles those
 * suggestions; a suggestion-free numeric property steps by 1 or 10; a suggestion-free non-numeric
 * (world-name) property cycles the supplied world names.
 */
public final class WorldPropertyCycle {

    private WorldPropertyCycle() {}

    public static String next(
            WorldProperty<?> property, String currentRaw, CycleAction action, List<String> worldNames) {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(worldNames, "worldNames");
        String current = (currentRaw == null || currentRaw.isBlank()) ? "" : currentRaw.strip();
        if (action == CycleAction.CLEAR) {
            return clearValue(property);
        }
        if (!property.suggestions().isEmpty()) {
            return cycleSuggestions(property.suggestions(), current, action);
        }
        if (isNumeric(property)) {
            return stepNumber(property, current, action);
        }
        return cycleWorldNames(worldNames, current, action);
    }

    private static String clearValue(WorldProperty<?> property) {
        if (property.suggestions().isEmpty() && !isNumeric(property)) {
            return "";
        }
        return encodeDefault(property);
    }

    private static <T> String encodeDefault(WorldProperty<T> property) {
        return property.encode(property.defaultValue());
    }

    private static boolean isNumeric(WorldProperty<?> property) {
        return property.decode("1").isPresent() && property.decode("x").isEmpty();
    }

    private static String cycleSuggestions(List<String> suggestions, String current, CycleAction action) {
        int index = suggestions.indexOf(current);
        int from = index < 0 ? 0 : index;
        int delta = forward(action) ? 1 : -1;
        int size = suggestions.size();
        return suggestions.get(((from + delta) % size + size) % size);
    }

    private static String stepNumber(WorldProperty<?> property, String current, CycleAction action) {
        BigDecimal base = current.isEmpty() ? BigDecimal.ZERO : new BigDecimal(current);
        BigDecimal step = big(action) ? BigDecimal.TEN : BigDecimal.ONE;
        BigDecimal moved = forward(action) ? base.add(step) : base.subtract(step);
        BigDecimal clamped = moved.max(BigDecimal.ZERO).stripTrailingZeros();
        String encoded = clamped.toPlainString();
        return property.decode(encoded).isPresent() ? encoded : current;
    }

    private static String cycleWorldNames(List<String> worldNames, String current, CycleAction action) {
        if (worldNames.isEmpty()) {
            return current;
        }
        int index = worldNames.indexOf(current);
        int delta = forward(action) ? 1 : -1;
        int size = worldNames.size();
        if (index < 0) {
            return forward(action) ? worldNames.get(0) : worldNames.get(size - 1);
        }
        return worldNames.get(((index + delta) % size + size) % size);
    }

    private static boolean forward(CycleAction action) {
        return action == CycleAction.FORWARD || action == CycleAction.FORWARD_BIG;
    }

    private static boolean big(CycleAction action) {
        return action == CycleAction.FORWARD_BIG || action == CycleAction.BACKWARD_BIG;
    }
}
