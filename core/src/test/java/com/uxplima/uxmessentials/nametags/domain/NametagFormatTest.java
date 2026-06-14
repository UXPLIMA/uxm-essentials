package com.uxplima.uxmessentials.nametags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

class NametagFormatTest {

    @Test
    void carriesItsParts() {
        NametagFormat format = new NametagFormat(
                "staff",
                DisplayCondition.always(),
                10,
                List.of("<red>[Staff]", "<gray>{player}"),
                NametagAppearance.defaults(),
                NametagVisibility.alwaysVisible());

        assertThat(format.name()).isEqualTo("staff");
        assertThat(format.condition()).isEqualTo(DisplayCondition.always());
        assertThat(format.priority()).isEqualTo(10);
        assertThat(format.lines()).containsExactly("<red>[Staff]", "<gray>{player}");
        assertThat(format.appearance()).isEqualTo(NametagAppearance.defaults());
        assertThat(format.visibility()).isEqualTo(NametagVisibility.alwaysVisible());
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> new NametagFormat(
                        " ",
                        DisplayCondition.always(),
                        0,
                        List.of("<gold>line"),
                        NametagAppearance.defaults(),
                        NametagVisibility.alwaysVisible()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NametagFormat(
                        "",
                        DisplayCondition.always(),
                        0,
                        List.of("<gold>line"),
                        NametagAppearance.defaults(),
                        NametagVisibility.alwaysVisible()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsEmptyLinesMeaningNoNametag() {
        NametagFormat format = new NametagFormat(
                "hidden",
                DisplayCondition.always(),
                0,
                List.of(),
                NametagAppearance.defaults(),
                NametagVisibility.alwaysVisible());

        assertThat(format.lines()).isEmpty();
    }

    @Test
    void defensivelyCopiesItsLines() {
        List<String> mutable = new ArrayList<>(List.of("<gold>line"));
        NametagFormat format = new NametagFormat(
                "default",
                DisplayCondition.always(),
                0,
                mutable,
                NametagAppearance.defaults(),
                NametagVisibility.alwaysVisible());

        mutable.add("<red>sneaky");

        assertThat(format.lines()).containsExactly("<gold>line");
    }
}
