package com.uxplima.uxmessentials.tablist.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The exact cells and per-group overflow counts produced by one layout planning pass. */
public record VirtualTabGrid<T>(List<VirtualTabCell<T>> cells, Map<String, Integer> overflowByGroup) {

    public VirtualTabGrid {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(overflowByGroup, "overflowByGroup");
        cells = List.copyOf(cells);
        overflowByGroup = Collections.unmodifiableMap(new LinkedHashMap<>(overflowByGroup));
    }

    public int slotCount() {
        return cells.size();
    }

    public VirtualTabCell<T> cell(int slot) {
        if (slot <= 0 || slot > cells.size()) {
            throw new IllegalArgumentException("virtual tab slot " + slot + " is outside 1-" + cells.size());
        }
        return cells.get(slot - 1);
    }
}
