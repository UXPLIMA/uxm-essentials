package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.NameCase;
import org.junit.jupiter.api.Test;

/** The allowed operators: a name in any case with authenticated logins, the exact spelling on offline mode. */
class OperatorListTest {

    private static final UUID LISTED_ID = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @Test
    void aListThatIsOffPermitsEverybody() {
        OperatorList list = OperatorList.of(false, List.of());

        assertThat(list.permits(UUID.randomUUID(), "Anybody", NameCase.SENSITIVE))
                .isTrue();
    }

    @Test
    void withAuthenticatedLoginsANameIsMatchedWithoutRegardToCase() {
        OperatorList list = OperatorList.of(true, List.of("Owner"));

        assertThat(list.permits(UUID.randomUUID(), "owner", NameCase.INSENSITIVE))
                .isTrue();
        assertThat(list.permits(UUID.randomUUID(), "Stranger", NameCase.INSENSITIVE))
                .isFalse();
    }

    @Test
    void offlineModeANameIsMatchedOnlyAsSpelled() {
        OperatorList list = OperatorList.of(true, List.of("Admin"));

        assertThat(list.permits(UUID.randomUUID(), "Admin", NameCase.SENSITIVE)).isTrue();
        // Another spelling logs in as another account on offline mode.
        assertThat(list.permits(UUID.randomUUID(), "aDMIN", NameCase.SENSITIVE)).isFalse();
        assertThat(list.permits(null, "admin", NameCase.SENSITIVE)).isFalse();
    }

    @Test
    void aUuidKeepsARenamedPlayerListed() {
        OperatorList list = OperatorList.of(true, List.of(LISTED_ID.toString()));

        assertThat(list.permits(LISTED_ID, "NewName", NameCase.SENSITIVE)).isTrue();
        assertThat(list.permits(null, "NewName", NameCase.INSENSITIVE)).isFalse();
    }

    @Test
    void anEmptyListInForcePermitsNobody() {
        OperatorList list = OperatorList.of(true, List.of(" "));

        assertThat(list.permits(LISTED_ID, "Owner", NameCase.INSENSITIVE)).isFalse();
    }
}
