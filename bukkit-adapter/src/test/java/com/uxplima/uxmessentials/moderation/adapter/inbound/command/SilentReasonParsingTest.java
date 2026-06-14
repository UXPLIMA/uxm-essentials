package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.moderation.adapter.inbound.command.ModerationCommandSupport.SilentReason;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of the {@code -s} silent-flag parsing every sanction command shares
 * ({@link ModerationCommandSupport#silentReason}). A leading {@code -s} token marks the sanction silent and is
 * stripped from the stored reason; without the token the module's {@code silent-by-default} decides. The token
 * never leaks into the reason the target sees or the audit records.
 */
class SilentReasonParsingTest {

    @Test
    void aLeadingDashSMarksSilentAndStripsTheToken() {
        SilentReason parsed = ModerationCommandSupport.silentReason(Optional.of("-s spamming chat"), false);

        assertThat(parsed.silent()).isTrue();
        assertThat(parsed.reason()).contains("spamming chat");
    }

    @Test
    void aBareDashSIsSilentWithNoReason() {
        SilentReason parsed = ModerationCommandSupport.silentReason(Optional.of("-s"), false);

        assertThat(parsed.silent()).isTrue();
        assertThat(parsed.reason()).isEmpty();
    }

    @Test
    void aPlainReasonKeepsTheConfiguredDefault() {
        SilentReason loud = ModerationCommandSupport.silentReason(Optional.of("griefing"), false);
        assertThat(loud.silent()).isFalse();
        assertThat(loud.reason()).contains("griefing");

        SilentReason quiet = ModerationCommandSupport.silentReason(Optional.of("griefing"), true);
        assertThat(quiet.silent()).isTrue();
        assertThat(quiet.reason()).contains("griefing");
    }

    @Test
    void noReasonFallsBackToTheConfiguredDefault() {
        assertThat(ModerationCommandSupport.silentReason(Optional.empty(), false)
                        .silent())
                .isFalse();
        assertThat(ModerationCommandSupport.silentReason(Optional.empty(), true).silent())
                .isTrue();
    }

    @Test
    void aReasonThatMerelyStartsWithDashSWithoutASpaceIsNotTheFlag() {
        SilentReason parsed = ModerationCommandSupport.silentReason(Optional.of("-spawn-camping"), false);

        assertThat(parsed.silent()).isFalse();
        assertThat(parsed.reason()).contains("-spawn-camping");
    }
}
