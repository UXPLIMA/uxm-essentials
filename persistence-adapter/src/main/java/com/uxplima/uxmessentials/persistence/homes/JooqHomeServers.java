package com.uxplima.uxmessentials.persistence.homes;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeServers;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/** The jOOQ-backed {@link HomeServers}: one keyed {@code UPDATE} over every owner's homes. */
@NullMarked
public final class JooqHomeServers extends JooqRepository implements HomeServers {

    public JooqHomeServers(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public int rename(String from, String to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return write(dsl -> dsl.update(HOMES)
                .set(HOMES.SERVER_ID, to)
                .where(HOMES.SERVER_ID.eq(from))
                .execute());
    }
}
