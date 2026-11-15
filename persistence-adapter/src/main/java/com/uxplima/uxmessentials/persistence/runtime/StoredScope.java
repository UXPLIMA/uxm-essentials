package com.uxplima.uxmessentials.persistence.runtime;

import com.uxplima.uxmessentials.shared.domain.ServerScope;
import org.jspecify.annotations.Nullable;

/** The nullable {@code scope} column of holograms, NPCs and main spawns: NULL means every server. */
public final class StoredScope {

    private StoredScope() {}

    /**
     * The scope a column holds, or {@code null} for every server. A value that is not a scope (a hand edit) also
     * reads as every server, so one bad cell never keeps the rest of the rows from loading.
     */
    public static @Nullable ServerScope read(@Nullable String column) {
        if (column == null || !ServerScope.isScope(column.strip())) {
            return null;
        }
        return new ServerScope(column.strip());
    }

    /** The column value for {@code scope}: its stored form, or NULL for every server. */
    public static @Nullable String write(@Nullable ServerScope scope) {
        return scope == null ? null : scope.value();
    }
}
