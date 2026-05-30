package com.uxplima.uxmessentials.shared.application.module;

import java.util.Objects;

/**
 * Names one Flyway migration location a module owns, such as {@code "db/migration/homes"}.
 *
 * <p>Migrations run only when the module is enabled; a disabled module's tables are simply absent.
 * Re-enabling a module runs its migrations forward idempotently from the surviving data.
 *
 * @param location the classpath-relative Flyway migration directory
 */
public record MigrationSet(String location) {

    public MigrationSet {
        Objects.requireNonNull(location, "location");
        if (location.isBlank()) {
            throw new IllegalArgumentException("migration location must not be blank");
        }
    }
}
