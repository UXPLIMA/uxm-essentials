package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * The pure look-at math: the yaw/pitch a fake player must adopt to face a viewer. Minecraft yaw is clockwise
 * from negative Z (south is 0, west is 90, north is 180/-180, east is -90); pitch is positive looking down.
 */
class LookAnglesTest {

    private static final float EYE = 1.62f;
    private static final float TOLERANCE = 0.5f;

    @Test
    void facesSouthForAViewerOnPositiveZ() {
        LookAngles look = LookAngles.facing(0, EYE, 0, 0, EYE, 5, 0f);

        assertThat(look.yaw()).isCloseTo(0f, within(TOLERANCE));
        assertThat(look.pitch()).isCloseTo(0f, within(TOLERANCE));
    }

    @Test
    void facesWestForAViewerOnNegativeX() {
        LookAngles look = LookAngles.facing(0, EYE, 0, -5, EYE, 0, 0f);

        assertThat(look.yaw()).isCloseTo(90f, within(TOLERANCE));
    }

    @Test
    void facesEastForAViewerOnPositiveX() {
        LookAngles look = LookAngles.facing(0, EYE, 0, 5, EYE, 0, 0f);

        assertThat(look.yaw()).isCloseTo(-90f, within(TOLERANCE));
    }

    @Test
    void pitchesUpForAViewerAbove() {
        // Viewer one block out on +Z and well above eye height: the NPC tilts its head up (negative pitch).
        LookAngles look = LookAngles.facing(0, EYE, 0, 0, EYE + 5, 1, 0f);

        assertThat(look.pitch()).isLessThan(0f);
    }

    @Test
    void pitchesDownForAViewerBelow() {
        LookAngles look = LookAngles.facing(0, EYE, 0, 0, EYE - 5, 1, 0f);

        assertThat(look.pitch()).isGreaterThan(0f);
    }

    @Test
    void keepsTheCurrentYawWhenTheTargetIsDirectlyAbove() {
        LookAngles look = LookAngles.facing(0, EYE, 0, 0, EYE + 5, 0, 42f);

        assertThat(look.yaw()).isEqualTo(42f);
        assertThat(look.pitch()).isCloseTo(-90f, within(TOLERANCE));
    }
}
