package com.uxplima.uxmessentials.persistence.warps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.WarpCategories.WARP_CATEGORIES;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.WarpCategoriesRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link WarpCategoryRepository} over the generated {@code WARP_CATEGORIES} table. Categories
 * are server-wide and keyed by id alone, so a lookup is a single-row {@code SELECT} on the id primary key, the
 * list reads every row in id order, and a {@code save} upserts on that key. The {@code display_lore} list is
 * persisted as a JSON array in one column (the same shape the welcome-message list uses on the warps table).
 * Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqWarpCategoryRepository extends JooqRepository implements WarpCategoryRepository {

    public JooqWarpCategoryRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<WarpCategory> find(String id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.selectFrom(WARP_CATEGORIES)
                .where(WARP_CATEGORIES.ID.eq(id))
                .fetchOptional()
                .map(WarpCategoryRows::toCategory));
    }

    @Override
    public List<WarpCategory> all() {
        return read(dsl -> dsl.selectFrom(WARP_CATEGORIES)
                .orderBy(WARP_CATEGORIES.ID.asc())
                .fetch()
                .map(WarpCategoryRows::toCategory));
    }

    @Override
    public void save(WarpCategory category) {
        Objects.requireNonNull(category, "category");
        write(dsl -> {
            upsert(dsl, category);
            return null;
        });
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id");
        write(dsl ->
                dsl.deleteFrom(WARP_CATEGORIES).where(WARP_CATEGORIES.ID.eq(id)).execute());
    }

    private static void upsert(DSLContext dsl, WarpCategory category) {
        WarpCategoriesRecord record = dsl.newRecord(WARP_CATEGORIES);
        WarpCategoryRows.apply(record, category);
        dsl.insertInto(WARP_CATEGORIES)
                .set(record)
                .onConflict(WARP_CATEGORIES.ID)
                .doUpdate()
                .set(WARP_CATEGORIES.DISPLAY_NAME, record.getDisplayName())
                .set(WARP_CATEGORIES.DISPLAY_MATERIAL, record.getDisplayMaterial())
                .set(WARP_CATEGORIES.DISPLAY_LORE, record.getDisplayLore())
                .set(WARP_CATEGORIES.SLOT, record.getSlot())
                .set(WARP_CATEGORIES.PARENT_CATEGORY_ID, record.getParentCategoryId())
                .execute();
    }
}
