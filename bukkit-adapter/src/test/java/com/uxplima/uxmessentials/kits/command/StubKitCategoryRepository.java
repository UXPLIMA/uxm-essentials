package com.uxplima.uxmessentials.kits.command;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;

final class StubKitCategoryRepository implements KitCategoryRepository {

    @Override
    public Optional<KitCategory> find(String id) {
        return Optional.empty();
    }

    @Override
    public List<KitCategory> all() {
        return List.of();
    }

    @Override
    public void save(KitCategory category) {}

    @Override
    public void delete(String id) {}
}
