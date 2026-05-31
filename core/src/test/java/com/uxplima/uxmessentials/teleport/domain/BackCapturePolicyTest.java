package com.uxplima.uxmessentials.teleport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The pure {@code /back} capture decision: an ignored cause (ender pearl, chorus fruit) does not record a
 * fresh return point, every other cause does, and the shipped default ignores exactly pearl and chorus.
 */
class BackCapturePolicyTest {

    @Test
    void defaultsIgnoreEnderPearlAndChorusFruit() {
        BackCapturePolicy policy = BackCapturePolicy.defaults();

        assertThat(policy.capturesOn(TeleportCauseCategory.ENDER_PEARL)).isFalse();
        assertThat(policy.capturesOn(TeleportCauseCategory.CHORUS_FRUIT)).isFalse();
        assertThat(policy.capturesOn(TeleportCauseCategory.COMMAND)).isTrue();
        assertThat(policy.capturesOn(TeleportCauseCategory.PORTAL)).isTrue();
        assertThat(policy.capturesOn(TeleportCauseCategory.OTHER)).isTrue();
    }

    @Test
    void captureAllRecordsEveryCause() {
        BackCapturePolicy policy = BackCapturePolicy.captureAll();

        for (TeleportCauseCategory cause : TeleportCauseCategory.values()) {
            assertThat(policy.capturesOn(cause)).isTrue();
        }
    }

    @Test
    void anOperatorListBlacklistsExactlyTheNamedCauses() {
        BackCapturePolicy policy =
                new BackCapturePolicy(EnumSet.of(TeleportCauseCategory.COMMAND, TeleportCauseCategory.PORTAL));

        assertThat(policy.capturesOn(TeleportCauseCategory.COMMAND)).isFalse();
        assertThat(policy.capturesOn(TeleportCauseCategory.PORTAL)).isFalse();
        assertThat(policy.capturesOn(TeleportCauseCategory.ENDER_PEARL)).isTrue();
    }

    @Test
    void theIgnoredSetIsDefensivelyCopied() {
        EnumSet<TeleportCauseCategory> mutable = EnumSet.of(TeleportCauseCategory.ENDER_PEARL);
        BackCapturePolicy policy = new BackCapturePolicy(mutable);

        mutable.add(TeleportCauseCategory.COMMAND);

        assertThat(policy.ignoredCauses()).isEqualTo(Set.of(TeleportCauseCategory.ENDER_PEARL));
    }
}
