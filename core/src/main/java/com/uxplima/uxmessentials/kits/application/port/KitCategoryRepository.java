package com.uxplima.uxmessentials.kits.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.domain.KitCategory;

/**
 * The store of kit categories.
 */
public interface KitCategoryRepository {

    /** Find a category by its ID. */
    Optional<KitCategory> find(String id);

    /** Get all categories. */
    List<KitCategory> all();

    /** Save a category definition. */
    void save(KitCategory category);

    /** Delete a category by its ID. */
    void delete(String id);
}
