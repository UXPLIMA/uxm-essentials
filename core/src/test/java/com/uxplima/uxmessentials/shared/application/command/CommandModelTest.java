package com.uxplima.uxmessentials.shared.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommandModelTest {

    @Test
    void commandIdRejectsBlankAndUppercase() {
        assertThatThrownBy(() -> new CommandId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandId("Home")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new CommandId("home").value()).isEqualTo("home");
    }

    @Test
    void effectiveCommandRejectsBlankName() {
        assertThatThrownBy(() -> new EffectiveCommand(new CommandId("home"), " ", List.of(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definitionCopiesItsAliasList() {
        CommandDefinition def = new CommandDefinition(new CommandId("home"), "home", List.of("h"));
        assertThat(def.defaultAliases()).containsExactly("h");
    }
}
