package com.uxplima.uxmessentials.tablist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

class TablistFormatTest {

    private static TablistContent content() {
        return new TablistContent(List.of("<gold>Welcome"), List.of(), Duration.ofSeconds(1L), Set.of());
    }

    @Test
    void carriesItsParts() {
        TablistFormat format = new TablistFormat(
                "staff",
                DisplayCondition.always(),
                10,
                content(),
                Optional.of("<red>[Staff] {player}"),
                OptionalInt.of(100));

        assertThat(format.name()).isEqualTo("staff");
        assertThat(format.condition()).isEqualTo(DisplayCondition.always());
        assertThat(format.priority()).isEqualTo(10);
        assertThat(format.nameFormat()).contains("<red>[Staff] {player}");
        assertThat(format.sortOrder()).hasValue(100);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> new TablistFormat(
                        " ", DisplayCondition.always(), 0, content(), Optional.empty(), OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TablistFormat(
                        "", DisplayCondition.always(), 0, content(), Optional.empty(), OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
