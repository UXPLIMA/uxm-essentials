package com.uxplima.uxmessentials.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScopesTest {

    @Test
    void aListIsReadInAnyCaseAndSpacing() {
        assertThat(Scopes.parse(" READ , write ")).containsExactlyInAnyOrder("read", "write");
    }

    @Test
    void somethingThatIsNotAScopeIsDroppedRatherThanInvented() {
        assertThat(Scopes.parse("read,everything")).containsExactly("read");
    }

    @Test
    void aListWithNoScopeInItIsRefusedRatherThanIssuingATokenThatCanDoNothing() {
        assertThatThrownBy(() -> Scopes.parse("admin")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thereAreThreeOfThem() {
        assertThat(Scopes.ALL).containsExactlyInAnyOrder("read", "write", "events");
    }
}
