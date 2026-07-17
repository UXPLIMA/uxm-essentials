package com.uxplima.uxmessentials.regions.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/regions/config.conf}: the enable gate, the page size the region list
 * GUI paginates by, and the flag names the flag editor exposes. Resolved once from the module's scoped
 * {@link ConfigStore} when the module starts and, per the atomic-reload rule, swapped whole on reload, so a command
 * dispatched mid-reload sees one coherent snapshot.
 *
 * <p>Every knob carries the default the bundled config ships, so an operator who deletes a line falls back to the
 * shipped value rather than to zero.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param listPageSize how many regions fill one page of the {@code /regions} list ({@code list.page-size},
 *     default {@code 45}; clamped to a single chest page)
 * @param editableFlags the state-flag names the flag editor lists, in display order ({@code flags.editable}); each
 *     is a WorldGuard state flag cycled {@code UNSET → ALLOW → DENY}, lower-cased and de-duplicated
 */
public record RegionsConfig(boolean enabled, int listPageSize, List<String> editableFlags) {

    /** The default region-list page size: the full five content rows of a six-row chest. */
    private static final int DEFAULT_LIST_PAGE_SIZE = 45;

    /** The largest content region a single six-row chest list can hold (five rows of nine). */
    private static final int MAX_LIST_PAGE_SIZE = 45;

    /**
     * The state flags the editor ships enabled: the everyday protection flags an operator reaches for most, each a
     * WorldGuard {@code StateFlag} the editor can cycle {@code ALLOW}/{@code DENY}/unset. Free-text flags (greeting,
     * farewell) are not state flags and so are not part of the cycle editor.
     */
    private static final List<String> DEFAULT_EDITABLE_FLAGS = List.of(
            "pvp",
            "build",
            "mob-spawning",
            "mob-damage",
            "creeper-explosion",
            "tnt",
            "entry",
            "exit",
            "use",
            "chest-access",
            "ride",
            "interact");

    public RegionsConfig {
        if (listPageSize < 1 || listPageSize > MAX_LIST_PAGE_SIZE) {
            throw new IllegalArgumentException("list.page-size must be 1.." + MAX_LIST_PAGE_SIZE + ": " + listPageSize);
        }
        editableFlags = List.copyOf(Objects.requireNonNull(editableFlags, "editableFlags"));
    }

    /** Resolve the regions config from the module's scoped {@link ConfigStore} ({@code modules.regions}). */
    public static RegionsConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        int pageSize = config.getInt("list.page-size", DEFAULT_LIST_PAGE_SIZE);
        return new RegionsConfig(
                config.getBoolean("enabled", true),
                Math.min(Math.max(pageSize, 1), MAX_LIST_PAGE_SIZE),
                normalizeFlags(config.getStringList("flags.editable", DEFAULT_EDITABLE_FLAGS)));
    }

    /** Lower-case, trim, drop blanks, and de-duplicate the configured flag names while keeping declaration order. */
    private static List<String> normalizeFlags(List<String> raw) {
        return raw.stream()
                .map(name -> name.strip().toLowerCase(java.util.Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }
}
