package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/**
 * The {@code /modstats} count runs on SQLite in the other tests; here it is rendered for the two server databases, so a
 * construct one of them spells differently (the null-safe actor match, the distinct count) is checked without one.
 */
class SanctionCountsDialectTest {

    private static final Instant SINCE = Instant.parse("2030-01-01T00:00:00Z");
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private static String render(SQLDialect dialect, Optional<UUID> actor) {
        DSLContext dsl = DSL.using(dialect);
        return dsl.renderInlined(JooqSanctionHistory.countsQuery(dsl, SINCE, actor));
    }

    @Test
    void mysqlMatchesTheActorNullSafelyAndCountsDistinctInstants() {
        String sql = render(SQLDialect.MYSQL, Optional.empty());

        assertThat(sql)
                .contains("`player_ban`.`actor` <=> `counted`.`actor`")
                .contains("count(distinct `counted`.`ts`)")
                .contains("`counted`.`ts` >= " + SINCE.toEpochMilli())
                .contains("group by `counted`.`actor`, `counted`.`actor_name`, `counted`.`action`")
                .doesNotContain("is not distinct from");
    }

    @Test
    void postgresMatchesTheActorNullSafelyAndCountsDistinctInstants() {
        String sql = render(SQLDialect.POSTGRES, Optional.empty());

        assertThat(sql)
                .contains("\"player_ban\".\"actor\" is not distinct from \"counted\".\"actor\"")
                .contains("count(distinct \"counted\".\"ts\")")
                .contains("\"counted\".\"ts\" >= " + SINCE.toEpochMilli())
                .contains("group by \"counted\".\"actor\", \"counted\".\"actor_name\", \"counted\".\"action\"");
    }

    @Test
    void thePerStaffViewAddsTheActorFilterOnBoth() {
        assertThat(render(SQLDialect.MYSQL, Optional.of(STAFF))).contains("`counted`.`actor` = '" + STAFF + "'");
        assertThat(render(SQLDialect.POSTGRES, Optional.of(STAFF))).contains("\"counted\".\"actor\" = '" + STAFF + "'");
    }
}
