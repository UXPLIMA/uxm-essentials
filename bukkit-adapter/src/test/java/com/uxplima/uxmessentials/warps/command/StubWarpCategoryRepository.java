package com.uxplima.uxmessentials.warps.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;

/**
 * A small in-memory {@link WarpCategoryRepository} for the warps GUI/command tests: empty by default (so the
 * browse menu falls back to the flat list), with {@link #save} populating it when a test wants to exercise the
 * category grouping path.
 */
final class StubWarpCategoryRepository implements WarpCategoryRepository {

    private final Map<String, WarpCategory> byId = new LinkedHashMap<>();

    @Override
    public Optional<WarpCategory> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<WarpCategory> all() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public void save(WarpCategory category) {
        byId.put(category.id(), category);
    }

    @Override
    public void delete(String id) {
        byId.remove(id);
    }
}
