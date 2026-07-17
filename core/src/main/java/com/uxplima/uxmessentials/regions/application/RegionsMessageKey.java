package com.uxplima.uxmessentials.regions.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The regions context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code REGIONS_GUI_TITLE} ↔ {@code regions.gui.title}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context.
 *
 * <p>Phase 1 covers the {@code /regions} list surface: the refusals ({@code no-worldguard}, {@code unknown-world},
 * {@code no-regions}), the paginated list panel (title, per-region line, prev/next), and the placeholder detail
 * notice a region click raises until the Phase 2/3 editors fill it in.
 */
public enum RegionsMessageKey implements MessageKey {

    // Refusals shown at the /regions command boundary.
    REGIONS_NO_WORLDGUARD("regions.no-worldguard"),
    REGIONS_UNKNOWN_WORLD("regions.unknown-world"),
    REGIONS_NO_REGIONS("regions.no-regions"),

    // The region list GUI: the panel title, a per-region icon name (its id) and lore (priority + member count),
    // the empty-state title, and the previous/next page buttons.
    REGIONS_GUI_TITLE("regions.gui.title"),
    REGIONS_GUI_EMPTY("regions.gui.empty"),
    REGIONS_GUI_REGION("regions.gui.region"),
    REGIONS_GUI_REGION_INFO("regions.gui.region-info"),
    REGIONS_GUI_PREV("regions.gui.prev"),
    REGIONS_GUI_NEXT("regions.gui.next"),

    // The placeholder detail notice a region click raises until the flag/member editors land in later phases.
    REGIONS_DETAIL_SOON("regions.detail-soon");

    private final String key;

    RegionsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
