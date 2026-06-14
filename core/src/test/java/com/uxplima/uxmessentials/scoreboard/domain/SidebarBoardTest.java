package com.uxplima.uxmessentials.scoreboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

class SidebarBoardTest {

    private static DisplayContent content() {
        return new DisplayContent(
                Optional.of("<gold>Server"), List.of("<white>line"), true, Duration.ofSeconds(1L), Set.of());
    }

    @Test
    void carriesItsParts() {
        SidebarBoard board = new SidebarBoard("staff", content(), DisplayCondition.always(), 10);

        assertThat(board.name()).isEqualTo("staff");
        assertThat(board.condition()).isEqualTo(DisplayCondition.always());
        assertThat(board.priority()).isEqualTo(10);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> new SidebarBoard(" ", content(), DisplayCondition.always(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SidebarBoard("", content(), DisplayCondition.always(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
