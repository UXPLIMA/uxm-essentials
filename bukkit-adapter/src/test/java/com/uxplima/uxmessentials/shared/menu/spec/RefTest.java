package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.junit.jupiter.api.Test;

class RefTest {

    @Test
    void parsesSimpleNamespacedRefWithNoArg() {
        Ref r = Ref.parse("warp:teleport");
        assertThat(r.id()).isEqualTo("warp:teleport");
        assertThat(r.value()).isEmpty();
    }

    @Test
    void parsesGenericRefWithArgAfterFirstColon() {
        Ref r = Ref.parse("sound:UI_BUTTON_CLICK");
        assertThat(r.id()).isEqualTo("sound");
        assertThat(r.value()).isEqualTo("UI_BUTTON_CLICK");
    }

    @Test
    void parsesBareIdAsNoArg() {
        assertThat(Ref.parse("close").id()).isEqualTo("close");
        assertThat(Ref.parse("close").value()).isEmpty();
    }

    @Test
    void rejectsBlankRaw() {
        assertThatThrownBy(() -> Ref.parse(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delegatingConstructorDefaultsToNoModifiers() {
        Ref parsed = Ref.parse("sound:click");
        assertThat(parsed.delayTicks()).isZero();
        assertThat(parsed.chance()).isEqualTo(100.0);
        assertThat(parsed.deny()).isEmpty();

        Ref built = Ref.of("give", Map.of());
        assertThat(built.delayTicks()).isZero();
        assertThat(built.chance()).isEqualTo(100.0);
        assertThat(built.deny()).isEmpty();
    }

    @Test
    void withModifiersCarriesDelayChanceAndDeny() {
        Ref deny = Ref.parse("message:no");
        Ref ref = Ref.parse("sound:LEVEL_UP").withModifiers(20, 25.0, deny);

        assertThat(ref.id()).isEqualTo("sound");
        assertThat(ref.value()).isEqualTo("LEVEL_UP");
        assertThat(ref.delayTicks()).isEqualTo(20);
        assertThat(ref.chance()).isEqualTo(25.0);
        assertThat(ref.deny()).contains(deny);
    }

    @Test
    void withModifiersClampsOutOfRangeValues() {
        Ref clamped = Ref.parse("give").withModifiers(-5, 250.0, null);
        assertThat(clamped.delayTicks()).as("a negative delay becomes now").isZero();
        assertThat(clamped.chance()).as("a chance above 100 caps at 100").isEqualTo(100.0);
        assertThat(clamped.deny()).isEmpty();

        assertThat(Ref.parse("give").withModifiers(0, -5.0, null).chance())
                .as("a negative chance floors at 0")
                .isZero();
    }

    @Test
    void deniedAtDecidesTheChanceBoundaryFromAnInjectedRoll() {
        // Full chance never rolls a miss, whatever the draw.
        assertThat(Ref.parse("give").deniedAt(0.0)).isFalse();
        assertThat(Ref.parse("give").deniedAt(99.9)).isFalse();

        // Zero chance is always a miss — a [0,100) draw is always at or above 0.
        assertThat(Ref.parse("give").withModifiers(0, 0.0, null).deniedAt(0.0)).isTrue();

        // A 25% ref proceeds on a draw in [0,25) and misses at or above it.
        Ref quarter = Ref.parse("give").withModifiers(0, 25.0, null);
        assertThat(quarter.deniedAt(24.999)).isFalse();
        assertThat(quarter.deniedAt(25.0)).isTrue();
        assertThat(quarter.deniedAt(80.0)).isTrue();
    }
}
