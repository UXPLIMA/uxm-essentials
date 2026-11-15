package com.uxplima.uxmessentials.persistence.vanish;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the vanish context's persistence adapter, so the bukkit-adapter wires {@link VanishRecords} from the
 * {@link Persistence} handle it already holds without naming a jOOQ type. No cache sits in front: a row is read once
 * per login and written only when a player's vanish changes, and every backend on the database writes it.
 */
@NullMarked
public final class VanishRecordStores {

    private VanishRecordStores() {}

    /** A jOOQ {@link VanishRecords} over the shared persistence DSL. */
    public static VanishRecords jooq(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqVanishRecords(persistence.dsl());
    }
}
