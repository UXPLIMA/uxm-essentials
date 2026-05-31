package com.uxplima.uxmessentials.migration;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The migration adapter's operator-facing {@link MessageKey} block (docs/12-migration §3, docs/13-i18n).
 * These back the {@code /uxmess import} command's output — the source list, the started/finished
 * acknowledgements, and the unknown-source error. They live in the {@code :migration} module rather than
 * the core catalogue because the importer is a command-gated adapter, not one of the twelve feature
 * contexts; the module ships its own {@code migration.*} locale block and resolves these through the
 * shared {@code Messages} port exactly as a feature context resolves its own keys.
 *
 * <p>Constant name and catalog key map 1:1 ({@code MIGRATION_STARTED} ↔ {@code migration.started}); all
 * keys sit under the {@code migration} namespace this module owns.
 */
public enum MigrationMessageKey implements MessageKey {

    /** Acknowledges the dispatch of a real import to the bounded executor. */
    MIGRATION_STARTED("migration.started"),

    /** Reports the final summary tally once a run drains. */
    MIGRATION_FINISHED("migration.finished"),

    /** Reports the final summary tally of a dry run. */
    MIGRATION_DRY_RUN_FINISHED("migration.dry-run-finished"),

    /** Names the built sources when an operator runs the bare command with no source. */
    MIGRATION_NO_SOURCE("migration.no-source"),

    /** Rejects an unrecognised source id, listing the built sources. */
    MIGRATION_UNKNOWN_SOURCE("migration.unknown-source"),

    /** A planned-but-not-built source id was requested. */
    MIGRATION_PLANNED_SOURCE("migration.planned-source"),

    /** The header of the {@code --list} output. */
    MIGRATION_SOURCE_LIST("migration.source-list"),

    /** One row of the {@code --list} output: a built source and its surface. */
    MIGRATION_SOURCE_ROW("migration.source-row"),

    /** The migration module is disabled, so the importer is dormant. */
    MIGRATION_MODULE_DISABLED("migration.module-disabled");

    private final String key;

    MigrationMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
