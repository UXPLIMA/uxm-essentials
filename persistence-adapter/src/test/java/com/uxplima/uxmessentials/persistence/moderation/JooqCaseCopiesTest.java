package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.CaseCopies;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The mutes and jails copied onto other spellings of a name, through the real migrations: each stored together with
 * its record or not at all, and found by the accounts holding them and by the account they came from, on every server
 * of the database.
 */
@EveryBackend
class JooqCaseCopiesTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final PlayerRef UPPER_STEVE =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000b2"), "STEVE");
    private static final PlayerRef LOWER_STEVE =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000b3"), "steve");
    private static final Instant ISSUED = Instant.parse("2030-01-01T00:00:00Z");
    private static final MuteState MUTE = MuteState.permanent(Issuer.of(ALEX), Optional.of("slurs"), ISSUED);

    private CaseCopies first;
    private CaseCopies second;
    private ModerationRepository moderation;

    @BeforeEach
    void open(TestDatabase database) {
        first = new JooqCaseCopies(database.dsl());
        second = new JooqCaseCopies(database.dsl());
        moderation = new JooqModerationRepository(database.dsl());
    }

    @Test
    void aCopyIsStoredWithItsRecordAndFoundFromBothEnds() {
        CaseCopies.Copy mute = new CaseCopies.Copy(UPPER_STEVE.uuid(), CaseCopies.Kind.MUTE, STEVE, ISSUED);
        JailState.Active cell =
                (JailState.Active) JailState.permanent("cell", Issuer.of(ALEX), Optional.of("grief"), ISSUED);
        CaseCopies.Copy jail = new CaseCopies.Copy(LOWER_STEVE.uuid(), CaseCopies.Kind.JAIL, STEVE, ISSUED);

        assertThat(first.copyMute(UPPER_STEVE, MuteState.none(), MUTE, ISSUED, mute))
                .isTrue();
        assertThat(first.copyJail(LOWER_STEVE, cell, ISSUED, jail)).isTrue();

        assertThat(moderation.loadMute(UPPER_STEVE)).isEqualTo(MUTE);
        assertThat(moderation.loadJail(LOWER_STEVE)).isEqualTo(cell);
        assertThat(second.madeFrom(STEVE, CaseCopies.Kind.MUTE)).containsExactly(mute);
        assertThat(second.madeFrom(STEVE, CaseCopies.Kind.JAIL)).containsExactly(jail);
        assertThat(second.madeFrom(UPPER_STEVE.uuid(), CaseCopies.Kind.MUTE)).isEmpty();
        assertThat(second.heldBy(List.of(UPPER_STEVE.uuid(), LOWER_STEVE.uuid())))
                .containsExactlyInAnyOrder(mute, jail);
        assertThat(second.heldBy(List.of(STEVE))).isEmpty();
    }

    @Test
    void aCopyThatMeetsAMuteStoredMeanwhileKeepsNeitherIt() {
        CaseCopies.Copy mute = new CaseCopies.Copy(UPPER_STEVE.uuid(), CaseCopies.Kind.MUTE, STEVE, ISSUED);
        moderation.ensureUserExists(UPPER_STEVE, ISSUED);
        MuteState staffs = MuteState.permanent(Issuer.of(ALEX), Optional.of("spam"), ISSUED.plusSeconds(60));
        moderation.saveMute(UPPER_STEVE, staffs);

        assertThat(first.copyMute(UPPER_STEVE, MuteState.none(), MUTE, ISSUED, mute))
                .isFalse();

        assertThat(moderation.loadMute(UPPER_STEVE)).isEqualTo(staffs);
        assertThat(second.heldBy(List.of(UPPER_STEVE.uuid()))).isEmpty();
    }

    @Test
    void aCopyThatCannotBeStoredTakesItsRecordWithIt() {
        CaseCopies.Copy mute = new CaseCopies.Copy(UPPER_STEVE.uuid(), CaseCopies.Kind.MUTE, STEVE, ISSUED);

        // A lifted mute has no row to store, so the write fails after the record went in.
        assertThatThrownBy(() -> first.copyMute(UPPER_STEVE, MuteState.none(), MuteState.none(), ISSUED, mute))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(second.heldBy(List.of(UPPER_STEVE.uuid()))).isEmpty();
    }
}
