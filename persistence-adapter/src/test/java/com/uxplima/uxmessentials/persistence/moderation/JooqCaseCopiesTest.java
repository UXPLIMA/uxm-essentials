package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.CaseCopies;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The copies of mutes and jails made onto other spellings of a name, through the real migrations: each kept once, read
 * by the accounts holding them and by the account they came from, on every server of the database.
 */
@EveryBackend
class JooqCaseCopiesTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID UPPER_STEVE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID LOWER_STEVE = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    private static final Instant ISSUED = Instant.parse("2030-01-01T00:00:00Z");

    private CaseCopies first;
    private CaseCopies second;

    @BeforeEach
    void open(TestDatabase database) {
        first = new JooqCaseCopies(database.dsl());
        second = new JooqCaseCopies(database.dsl());
    }

    @Test
    void aCopyIsKeptOnceAndFoundFromBothEnds() {
        CaseCopies.Copy mute = new CaseCopies.Copy(UPPER_STEVE, CaseCopies.Kind.MUTE, STEVE, ISSUED);
        CaseCopies.Copy jail = new CaseCopies.Copy(LOWER_STEVE, CaseCopies.Kind.JAIL, STEVE, ISSUED);
        first.record(mute);
        first.record(mute);
        first.record(jail);

        assertThat(second.madeFrom(STEVE, CaseCopies.Kind.MUTE)).containsExactly(mute);
        assertThat(second.madeFrom(STEVE, CaseCopies.Kind.JAIL)).containsExactly(jail);
        assertThat(second.madeFrom(UPPER_STEVE, CaseCopies.Kind.MUTE)).isEmpty();
        assertThat(second.heldBy(List.of(UPPER_STEVE, LOWER_STEVE))).containsExactlyInAnyOrder(mute, jail);
        assertThat(second.heldBy(List.of(STEVE))).isEmpty();
    }
}
