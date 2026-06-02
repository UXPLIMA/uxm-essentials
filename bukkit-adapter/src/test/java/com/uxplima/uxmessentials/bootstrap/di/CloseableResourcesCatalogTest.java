package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CatalogBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The registration chokepoint must apply the catalog (rename/realias/drop) before it wraps survivors in
 * the locale binding, so an operator's {@code commands/*.conf} edits change what gets published. MockBukkit
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
                "home", new EffectiveCommand(new CommandId("home"), "ev", List.of("e"), true),
                "warp", new EffectiveCommand(new CommandId("warp"), "warp", List.of(), false))));

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
                Map.of("home", new EffectiveCommand(new CommandId("home"), "ev", List.of("e"), true))));
        resources.localeBinding(new LocaleBinding(new NoOverrideLocaleStore(), Locale.ENGLISH));

        List<CommandRegistration> out = resources.commands();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).build().getLiteral()).isEqualTo("ev");
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
