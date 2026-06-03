package com.uxplima.uxmessentials.persistence.homes;

import static com.uxplima.uxmessentials.persistence.jooq.tables.HomeMain.HOME_MAIN;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.MainHomePreference;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link MainHomePreference} over the {@code home_main} table. A player's chosen main home
 * is a first-class {@code (owner, name)} row — not a JSON blob — so it is queryable and survives relog and
 * world rollbacks. A player with no row has never run {@code /setmainhome} and plain {@code /home} falls
 * back to the first-created home; that fallback lives in the resolving use case, not here.
 *
 * <p>{@link #setMainHome} upserts on the owner key, so re-choosing overwrites the same row. Every statement
 * is typed jOOQ DSL; no SQL is ever string-concatenated. This is uncached: the row is read off the
 * {@code /home} hot path and is a single keyed lookup on a primary-key index, already cheap.
 */
@NullMarked
public final class JooqMainHomePreference extends JooqRepository implements MainHomePreference {

    public JooqMainHomePreference(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<HomeName> mainHomeOf(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.select(HOME_MAIN.NAME)
                .from(HOME_MAIN)
                .where(HOME_MAIN.OWNER.eq(owner.uuid().toString()))
                .fetchOptional(HOME_MAIN.NAME)
                .map(HomeName::of));
    }

    @Override
    public void setMainHome(PlayerRef owner, HomeName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        write(dsl -> {
            dsl.insertInto(HOME_MAIN)
                    .set(HOME_MAIN.OWNER, owner.uuid().toString())
                    .set(HOME_MAIN.NAME, name.value())
                    .onConflict(HOME_MAIN.OWNER)
                    .doUpdate()
                    .set(HOME_MAIN.NAME, name.value())
                    .execute();
            return null;
        });
    }

    @Override
    public void clear(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        write(dsl -> {
            dsl.deleteFrom(HOME_MAIN)
                    .where(HOME_MAIN.OWNER.eq(owner.uuid().toString()))
                    .execute();
            return null;
        });
    }
}
