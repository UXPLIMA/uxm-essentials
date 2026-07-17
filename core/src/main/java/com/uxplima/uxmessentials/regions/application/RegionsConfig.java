package com.uxplima.uxmessentials.regions.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/regions/config.conf}: the enable gate and the page size the region
 * list GUI paginates by. Resolved once from the module's scoped {@link ConfigStore} when the module starts and, per
 * the atomic-reload rule, swapped whole on reload, so a command dispatched mid-reload sees one coherent snapshot.
 *
 * <p>Every knob carries the default the bundled config ships, so an operator who deletes a line falls back to the
 * shipped value rather than to zero.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param listPageSize how many regions fill one page of the {@code /regions} list ({@code list.page-size},
 *     default {@code 45}; clamped to a single chest page)
 */
public record RegionsConfig(boolean enabled, int listPageSize) {

    /** The default region-list page size: the full five content rows of a six-row chest. */
    private static final int DEFAULT_LIST_PAGE_SIZE = 45;

    /** The largest content region a single six-row chest list can hold (five rows of nine). */
    private static final int MAX_LIST_PAGE_SIZE = 45;

    public RegionsConfig {
        if (listPageSize < 1 || listPageSize > MAX_LIST_PAGE_SIZE) {
            throw new IllegalArgumentException("list.page-size must be 1.." + MAX_LIST_PAGE_SIZE + ": " + listPageSize);
        }
    }

    /** Resolve the regions config from the module's scoped {@link ConfigStore} ({@code modules.regions}). */
    public static RegionsConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        int pageSize = config.getInt("list.page-size", DEFAULT_LIST_PAGE_SIZE);
        return new RegionsConfig(
                config.getBoolean("enabled", true), Math.min(Math.max(pageSize, 1), MAX_LIST_PAGE_SIZE));
    }
}
