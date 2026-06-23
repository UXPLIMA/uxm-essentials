package com.uxplima.uxmessentials.warps.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.warps.domain.WarpCategory;

/**
 * The store of warp categories. Mirrors {@code KitCategoryRepository}: a small, server-wide set the browse
 * menu groups warps under and the category manager edits. Filtering by parent for the drill-in is done in
 * memory by the views, so the port stays a flat find / list / save / delete.
 */
public interface WarpCategoryRepository {

    /** Find a category by its ID. */
    Optional<WarpCategory> find(String id);

    /** Get all categories. */
    List<WarpCategory> all();

    /** Save a category definition. */
    void save(WarpCategory category);

    /** Delete a category by its ID. */
    void delete(String id);
}
