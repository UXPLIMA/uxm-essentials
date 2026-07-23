package com.uxplima.uxmessentials.regions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Covers {@link FlagDescriptor} validation, the unset/editable predicates, and immutable choices. */
class FlagDescriptorTest {

    @Test
    void unsetWhenValueIsBlankAndSetOtherwise() {
        assertThat(FlagDescriptor.of("pvp", FlagKind.STATE, "").unset()).isTrue();
        assertThat(FlagDescriptor.of("pvp", FlagKind.STATE, "ALLOW").unset()).isFalse();
    }

    @Test
    void everyKindIsEditableExceptOther() {
        assertThat(FlagDescriptor.of("greeting", FlagKind.STRING, "").editable())
                .isTrue();
        assertThat(FlagDescriptor.of("teleport", FlagKind.OTHER, "x").editable())
                .isFalse();
    }

    @Test
    void carriesChoicesForAnEnumFlag() {
        FlagDescriptor descriptor =
                new FlagDescriptor("game-mode", FlagKind.ENUM, "creative", List.of("survival", "creative"));

        assertThat(descriptor.choices()).containsExactly("survival", "creative");
    }

    @Test
    void copiesChoicesDefensively() {
        List<String> mutable = new java.util.ArrayList<>(List.of("a", "b"));
        FlagDescriptor descriptor = new FlagDescriptor("f", FlagKind.ENUM, "a", mutable);
        mutable.add("c");

        assertThat(descriptor.choices()).containsExactly("a", "b");
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> FlagDescriptor.of(" ", FlagKind.STATE, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
