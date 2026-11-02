package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OperatorListTest {

    private static final UUID LISTED_ID = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @Test
    void aListThatIsOffPermitsEverybody() {
        OperatorList list = OperatorList.of(false, List.of());

        assertThat(list.permits(UUID.randomUUID(), "Anybody")).isTrue();
    }

    @Test
    void aNameIsMatchedWithoutRegardToCase() {
        OperatorList list = OperatorList.of(true, List.of("Owner"));

        assertThat(list.permits(UUID.randomUUID(), "owner")).isTrue();
        assertThat(list.permits(UUID.randomUUID(), "Stranger")).isFalse();
    }

    @Test
    void aUuidKeepsARenamedPlayerListed() {
        OperatorList list = OperatorList.of(true, List.of(LISTED_ID.toString()));

        assertThat(list.permits(LISTED_ID, "NewName")).isTrue();
        assertThat(list.permits(null, "NewName")).isFalse();
    }

    @Test
    void anEmptyListInForcePermitsNobody() {
        OperatorList list = OperatorList.of(true, List.of(" "));

        assertThat(list.permits(LISTED_ID, "Owner")).isFalse();
    }
}
