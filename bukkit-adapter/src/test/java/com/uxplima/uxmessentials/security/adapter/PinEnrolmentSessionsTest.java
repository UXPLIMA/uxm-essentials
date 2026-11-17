package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * A create-a-PIN session is known by its token, so an answer for a session that ended (the player left, or a verify
 * freeze replaced it) is never taken for the session that holds them now.
 */
class PinEnrolmentSessionsTest {

    private static final UUID ADA = new UUID(0L, 7L);

    private final PinEnrolmentSessions sessions = new PinEnrolmentSessions();

    @Test
    void anAnswerForAnEndedSessionDoesNotCountForTheNextOne() {
        sessions.begin(ADA);
        long first = sessions.token(ADA).orElseThrow();
        sessions.rememberFirst(ADA, "4821");

        sessions.clear(ADA);
        assertThat(sessions.isPending(ADA, first)).isFalse();
        assertThat(sessions.token(ADA)).isEmpty();

        sessions.begin(ADA);
        assertThat(sessions.isPending(ADA, first)).isFalse();
        assertThat(sessions.isPending(ADA, sessions.token(ADA).orElseThrow())).isTrue();
    }

    @Test
    void aRestartKeepsTheSession() {
        sessions.begin(ADA);
        long token = sessions.token(ADA).orElseThrow();
        sessions.rememberFirst(ADA, "4821");

        sessions.restart(ADA);

        assertThat(sessions.isPending(ADA, token)).isTrue();
        assertThat(sessions.firstEntry(ADA)).isEmpty();
    }
}
