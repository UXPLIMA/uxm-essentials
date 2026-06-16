package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.holograms.domain.Visibility.Mode;
import org.junit.jupiter.api.Test;

class VisibilityTest {

    @Test
    void everyoneIsTheDefaultWithNoNodeAndNoDistanceLimit() {
        Visibility everyone = Visibility.everyone();

        assertThat(everyone.mode()).isEqualTo(Mode.ALL);
        assertThat(everyone.permission()).isNull();
        assertThat(everyone.distance()).isEqualTo(Visibility.UNLIMITED);
        assertThat(everyone.isPermissionGated()).isFalse();
        assertThat(everyone.hasDistanceLimit()).isFalse();
    }

    @Test
    void restrictedToCarriesTheNodeAndIsPermissionGated() {
        Visibility gated = Visibility.restrictedTo("uxmessentials.hologram.see.vip");

        assertThat(gated.mode()).isEqualTo(Mode.PERMISSION);
        assertThat(gated.permission()).isEqualTo("uxmessentials.hologram.see.vip");
        assertThat(gated.isPermissionGated()).isTrue();
    }

    @Test
    void permissionModeRequiresANonBlankNode() {
        assertThatThrownBy(() -> new Visibility(Mode.PERMISSION, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Visibility(Mode.PERMISSION, "  ", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allModeRejectsANode() {
        assertThatThrownBy(() -> new Visibility(Mode.ALL, "some.node", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distanceMustNotBeNegative() {
        assertThatThrownBy(() -> new Visibility(Mode.ALL, null, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toPermissionAndToEveryonePreserveTheDistance() {
        Visibility gated = Visibility.everyone().withDistance(48).toPermission("node.x");
        assertThat(gated.distance()).isEqualTo(48);
        assertThat(gated.permission()).isEqualTo("node.x");

        Visibility opened = gated.toEveryone();
        assertThat(opened.mode()).isEqualTo(Mode.ALL);
        assertThat(opened.permission()).isNull();
        assertThat(opened.distance()).isEqualTo(48);
    }

    @Test
    void withDistanceMarksAFiniteRadius() {
        Visibility limited = Visibility.everyone().withDistance(32);

        assertThat(limited.hasDistanceLimit()).isTrue();
        assertThat(limited.distance()).isEqualTo(32);
    }

    @Test
    void clampDistanceHoldsTheRange() {
        assertThat(Visibility.clampDistance(-5)).isEqualTo(Visibility.UNLIMITED);
        assertThat(Visibility.clampDistance(10_000)).isEqualTo(Visibility.MAX_DISTANCE);
        assertThat(Visibility.clampDistance(64)).isEqualTo(64);
    }

    @Test
    void manualIsHiddenByDefaultWithNoNode() {
        Visibility manual = Visibility.manual();

        assertThat(manual.mode()).isEqualTo(Mode.MANUAL);
        assertThat(manual.permission()).isNull();
        assertThat(manual.distance()).isEqualTo(Visibility.UNLIMITED);
        assertThat(manual.isManual()).isTrue();
        assertThat(manual.isPermissionGated()).isFalse();
    }

    @Test
    void manualModeRejectsANode() {
        assertThatThrownBy(() -> new Visibility(Mode.MANUAL, "some.node", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toManualKeepsTheDistanceAndDropsAnyNode() {
        Visibility manual = Visibility.restrictedTo("node.x").withDistance(48).toManual();

        assertThat(manual.mode()).isEqualTo(Mode.MANUAL);
        assertThat(manual.permission()).isNull();
        assertThat(manual.distance()).isEqualTo(48);
        assertThat(manual.isManual()).isTrue();
    }

    @Test
    void allAndPermissionAreNotManual() {
        assertThat(Visibility.everyone().isManual()).isFalse();
        assertThat(Visibility.restrictedTo("node.x").isManual()).isFalse();
    }
}
