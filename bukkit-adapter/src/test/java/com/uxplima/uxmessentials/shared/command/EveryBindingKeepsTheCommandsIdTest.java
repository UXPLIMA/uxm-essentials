package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.UsageBinding;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A binding that wraps a command keeps the command's id, its code-side name and its code-side aliases.
 *
 * <p>The locale binding wraps last, and it answered the id with its built literal. /2fa, whose id is
 * {@code twofactor}, and every command an operator renamed lost their id there, so /help looked up the wrong
 * description and showed the code's English to a Turkish reader.
 */
class EveryBindingKeepsTheCommandsIdTest {

    @Test
    @DisplayName("the locale, usage and gui bindings answer with the wrapped command's id, name and aliases")
    void theBindingsKeepTheId() {
        CommandRegistration command = new TwoFactor();
        Messages messages = (viewer, key, placeholders) -> key.key();
        LocaleBinding locale = new LocaleBinding(new NoOverrides(), Locale.ENGLISH, messages, new QuietLog());
        UsageBinding usage = new UsageBinding(messages);

        GuiRootBinding guiRoot = new GuiRootBinding(java.util.Map.of());

        for (CommandRegistration wrapped : List.of(locale.wrap(command), usage.wrap(command), guiRoot.wrap(command))) {
            assertThat(wrapped.commandId()).isEqualTo("twofactor");
            assertThat(wrapped.defaultName()).isEqualTo("2fa");
            assertThat(wrapped.defaultAliases()).containsExactly("twofactor");
        }
    }

    /** Registers as /2fa under the letter-first id twofactor, as the real command does. */
    private static final class TwoFactor implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal("2fa").executes(c -> 1).build();
        }

        @Override
        public String description() {
            return "Manage your second factor.";
        }

        @Override
        public String commandId() {
            return "twofactor";
        }

        @Override
        public String defaultName() {
            return "2fa";
        }

        @Override
        public List<String> aliases() {
            return List.of("twofactor");
        }
    }

    private static final class NoOverrides implements LocaleStore {
        @Override
        public Optional<Locale> override(PlayerRef player) {
            return Optional.empty();
        }

        @Override
        public void setOverride(PlayerRef player, Locale locale) {}

        @Override
        public void clearOverride(PlayerRef player) {}
    }

    private static final class QuietLog implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
