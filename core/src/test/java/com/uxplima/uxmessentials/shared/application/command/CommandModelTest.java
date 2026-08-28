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
    void acceptsANamespacedIdForACommandThatIsNotOurOwn() {
        // An operator's own definition out of commands/custom/ is keyed custom:<file name>, and that file name takes
        // the wider shape a file name may have: a leading digit, a hyphen, an underscore.
        assertThat(new CommandId("custom:welcome").value()).isEqualTo("custom:welcome");
        assertThat(new CommandId("custom:daily-reward_2").value()).isEqualTo("custom:daily-reward_2");
        assertThatThrownBy(() -> new CommandId("custom:")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandId("custom:Welcome")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandId("custom:a:b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void effectiveCommandRejectsBlankName() {
        assertThatThrownBy(() -> new EffectiveCommand(new CommandId("home"), " ", List.of(), true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definitionCopiesItsAliasList() {
        CommandDefinition def = new CommandDefinition(new CommandId("home"), "home", List.of("h"));
        assertThat(def.defaultAliases()).containsExactly("h");
    }
}
