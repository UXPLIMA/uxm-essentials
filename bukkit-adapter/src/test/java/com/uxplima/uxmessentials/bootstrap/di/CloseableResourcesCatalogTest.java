package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CatalogBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.UsageBinding;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;

/**
 * The registration chokepoint must apply the catalog (rename/realias/drop) before it wraps survivors in
 * the locale binding, so an operator's {@code commands.conf} edits change what gets published. MockBukkit
 * boots Paper's Brigadier so the rename rebuild through {@link Commands#literal} is wired before the nodes
 * are built.
 */
class CloseableResourcesCatalogTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void catalogRenamesAndDropsBeforeLocale() {
        CloseableResources resources = new CloseableResources();
        resources.addCommand(new StubRegistration("home", List.of("h")));
        resources.addCommand(new StubRegistration("warp", List.of("w")));
        resources.catalogBinding(new CatalogBinding(Map.of(
                "home", new EffectiveCommand(new CommandId("home"), "ev", List.of("e"), true, true),
                "warp", new EffectiveCommand(new CommandId("warp"), "warp", List.of(), false, true))));

        List<CommandRegistration> out = resources.commands();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).build().getLiteral()).isEqualTo("ev");
        assertThat(out.get(0).aliases()).containsExactly("e");
    }

    @Test
    void noCatalogBindingLeavesCommandsAsDefaults() {
        CloseableResources resources = new CloseableResources();
        resources.addCommand(new StubRegistration("home", List.of("h")));

        List<CommandRegistration> out = resources.commands();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).build().getLiteral()).isEqualTo("home");
        assertThat(out.get(0).aliases()).containsExactly("h");
    }

    @Test
    void catalogAndLocaleCompose() {
        CloseableResources resources = new CloseableResources();
        resources.addCommand(new StubRegistration("home", List.of("h")));
        resources.catalogBinding(new CatalogBinding(
                Map.of("home", new EffectiveCommand(new CommandId("home"), "ev", List.of("e"), true, true))));
        resources.localeBinding(
                new LocaleBinding(new NoOverrideLocaleStore(), Locale.ENGLISH, new SuffixMessages(), new NoOpLog()));

        List<CommandRegistration> out = resources.commands();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).build().getLiteral()).isEqualTo("ev");
    }

    @Test
    void usageIsInjectedBetweenCatalogAndLocale() {
        CloseableResources resources = new CloseableResources();
        resources.addCommand(new BareArgRegistration("gamemode"));
        resources.addCommand(new StubRegistration("home", List.of("h")));
        resources.usageBinding(new UsageBinding(new SuffixMessages()));
        resources.localeBinding(
                new LocaleBinding(new NoOverrideLocaleStore(), Locale.ENGLISH, new SuffixMessages(), new NoOpLog()));

        List<CommandRegistration> out = resources.commands();

        CommandRegistration bare = byLiteral(out, "gamemode");
        // the arg-only command gains a root executor (usage injected) and survives the locale rebuild
        assertThat(bare.build().getCommand()).isNotNull();
        // the command that already had a root executor keeps a root executor too
        assertThat(byLiteral(out, "home").build().getCommand()).isNotNull();
    }

    @Test
    void usageReportsTheCatalogRenamedLiteral() {
        RecordingMessages messages = new RecordingMessages();
        CloseableResources resources = new CloseableResources();
        resources.addCommand(new BareArgRegistration("gamemode"));
        resources.catalogBinding(new CatalogBinding(
                Map.of("gamemode", new EffectiveCommand(new CommandId("gamemode"), "gm", List.of(), true, true))));
        resources.usageBinding(new UsageBinding(messages));

        LiteralCommandNode<CommandSourceStack> node =
                byLiteral(resources.commands(), "gm").build();
        dispatchBare(node, CommandSourceStackMock.from(MockBukkit.getMock().addPlayer("Alice")));

        // usage injected after the catalog rename sees the renamed literal, not the code-side default
        assertThat(messages.placeholders).containsEntry("command", "gm");
    }

    private static CommandRegistration byLiteral(List<CommandRegistration> commands, String literal) {
        return commands.stream()
                .filter(c -> c.build().getLiteral().equals(literal))
                .findFirst()
                .orElseThrow();
    }

    private static void dispatchBare(LiteralCommandNode<CommandSourceStack> node, CommandSourceStack source) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(node.getLiteral(), source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("usage executor did not parse", e);
        }
    }

    private record BareArgRegistration(String id) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal(id)
                    .then(Commands.argument("mode", StringArgumentType.word()).executes(c -> 1))
                    .build();
        }

        @Override
        public String description() {
            return "Set game mode.";
        }

        @Override
        public String commandId() {
            return id;
        }
    }

    private static final class SuffixMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingMessages implements Messages {
        private Map<String, String> placeholders = Map.of();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            this.placeholders = Map.copyOf(placeholders);
            return key.key();
        }
    }

    private static final class NoOpLog implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class NoOverrideLocaleStore implements LocaleStore {
        @Override
        public Optional<Locale> override(PlayerRef player) {
            return Optional.empty();
        }

        @Override
        public void setOverride(PlayerRef player, Locale locale) {}

        @Override
        public void clearOverride(PlayerRef player) {}
    }

    private record StubRegistration(String id, List<String> aliases) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal(id)
                    .executes(c -> 1)
                    .then(Commands.literal("set").executes(c -> 1))
                    .build();
        }

        @Override
        public String description() {
            return "x";
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }

        @Override
        public String commandId() {
            return id;
        }
    }
}
