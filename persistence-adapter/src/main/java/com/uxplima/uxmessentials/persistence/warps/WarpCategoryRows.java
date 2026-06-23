package com.uxplima.uxmessentials.persistence.warps;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.WarpCategoriesRecord;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between a {@code warp_categories} row and the domain {@link WarpCategory}. The
 * optional display material and parent category are nullable columns mapping to an empty {@link Optional}; the
 * display-lore list is stored as a JSON array in one column (an empty/blank column reads back as an empty list).
 * This class is the single place that translation lives.
 */
final class WarpCategoryRows {

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final java.lang.reflect.Type LORE_TYPE =
            new com.google.gson.reflect.TypeToken<List<String>>() {}.getType();

    private WarpCategoryRows() {}

    /** Rebuild a {@link WarpCategory} from a queried row. */
    static WarpCategory toCategory(Record row) {
        return new WarpCategory(
                row.get(com.uxplima.uxmessentials.persistence.jooq.tables.WarpCategories.WARP_CATEGORIES.ID),
                row.get(com.uxplima.uxmessentials.persistence.jooq.tables.WarpCategories.WARP_CATEGORIES.DISPLAY_NAME),
                Optional.ofNullable(row.get(
                        com.uxplima.uxmessentials.persistence.jooq.tables.WarpCategories.WARP_CATEGORIES
                                .DISPLAY_MATERIAL)),
                deserializeLore(row.get(
                        com.uxplima.uxmessentials.persistence.jooq.tables.WarpCategories.WARP_CATEGORIES.DISPLAY_LORE)),
                row.get(com.uxplima.uxmessentials.persistence.jooq.tables.WarpCategories.WARP_CATEGORIES.SLOT),
                Optional.ofNullable(row.get(
                        com.uxplima.uxmessentials.persistence.jooq.tables.WarpCategories.WARP_CATEGORIES
                                .PARENT_CATEGORY_ID)));
    }

    /** Populate a {@link WarpCategoriesRecord} from a domain {@link WarpCategory} for an upsert. */
    static void apply(WarpCategoriesRecord record, WarpCategory category) {
        record.setId(category.id())
                .setDisplayName(category.displayName())
                .setDisplayMaterial(category.displayMaterial().orElse(null))
                .setDisplayLore(serializeLore(category.displayLore()))
                .setSlot(category.slot())
                .setParentCategoryId(category.parentCategoryId().orElse(null));
    }

    private static List<String> deserializeLore(@Nullable String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = GSON.fromJson(stored, LORE_TYPE);
            return list != null ? list : List.of();
        } catch (com.google.gson.JsonSyntaxException invalid) {
            // A malformed lore column reads back as no lore rather than failing the whole load.
            return List.of();
        }
    }

    private static @Nullable String serializeLore(List<String> lore) {
        return lore.isEmpty() ? null : GSON.toJson(lore);
    }
}
