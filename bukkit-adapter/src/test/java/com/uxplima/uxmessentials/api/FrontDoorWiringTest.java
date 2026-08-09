package com.uxplima.uxmessentials.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuClick;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuIconProvider;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuView;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.shared.adapter.inbound.api.UxmEssentialsApiImpl;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.QueryContexts;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * What the front door answers once it is wired to the real registry.
 *
 * <p>The module question is the one worth pinning: a consumer decides whether to hook economy or homes by asking it,
 * and it has to reflect the operator's <em>current</em> configuration rather than whatever was true when the plugin
 * enabled. The API therefore reads the configuration through a supplier, and the last case here is what proves that:
 * flipping the config after wiring changes the answer.
 */
class FrontDoorWiringTest {

    @Test
    void anEnabledModuleAnswersTrueAndADisabledOneFalse() {
        ToggleConfig config = new ToggleConfig(true);
        UxmEssentialsApi api = api(config, "homes");

        assertThat(api.isModuleEnabled("homes")).isTrue();

        config.enabled = false;
        assertThat(api.isModuleEnabled("homes"))
                .as("the answer follows the live config, not a snapshot taken at wiring time")
                .isFalse();
    }

    @Test
    void anUnknownModuleIdIsFalseRatherThanAnException() {
        assertThat(api(new ToggleConfig(true), "homes").isModuleEnabled("nosuchmodule"))
                .as("a consumer feature-detecting a module we do not ship gets an answer, not a stack trace")
                .isFalse();
    }

    @Test
    void anIdThatCouldNeverBeAModuleIsAlsoJustFalse() {
        // Module ids are lowercase by construction, so "Homes" can never name one. A consumer that guessed the
        // casing gets false rather than the IllegalArgumentException a ModuleId round-trip would have thrown.
        assertThat(api(new ToggleConfig(true), "homes").isModuleEnabled("Homes"))
                .isFalse();
    }

    @Test
    void aContextThatNeverWiredAnswersEmptyRatherThanNull() {
        UxmEssentialsApi api = api(new ToggleConfig(true), "homes");

        assertThat(api.homes())
                .as("a disabled module registers no surface, and absent is the answer a consumer can act on")
                .isEmpty();
        assertThat(api.warps()).isEmpty();
        assertThat(api.playerWarps()).isEmpty();
        assertThat(api.economy()).isEmpty();
        assertThat(api.kits()).isEmpty();
        assertThat(api.vaults()).isEmpty();
        assertThat(api.moderation()).isEmpty();
        assertThat(api.presence()).isEmpty();
        assertThat(api.vanish()).isEmpty();
        assertThat(api.playtime()).isEmpty();
        assertThat(api.playerState()).isEmpty();
        assertThat(api.worlds()).isEmpty();
        assertThat(api.teleport()).isEmpty();
        assertThat(api.vote()).isEmpty();
        assertThat(api.messaging()).isEmpty();
    }

    @Test
    void aContextThatNeverWiredOffersNoWritesEither() {
        UxmActions actions = api(new ToggleConfig(true), "homes").actions(plugin("MyPlugin"));

        assertThat(actions.economy())
                .as("a disabled module registers no write surface, so a consumer takes its own path instead")
                .isEmpty();
    }

    @Test
    void aSurfaceRegisteredAfterTheFrontDoorWasBuiltIsStillVisible() {
        // The front door is published to other plugins before any context has wired, so the registry it holds is
        // filled afterwards. A snapshot taken at construction time would answer empty forever.
        QueryContexts queries = QueryContexts.empty();
        UxmEssentialsApi api = new UxmEssentialsApiImpl(
                "1.2.3", new ListModuleRegistry(), () -> new ToggleConfig(true), new UnusedMenus(), queries);
        assertThat(api.homes()).isEmpty();

        UxmHomesQuery surface = new UnusedHomes();
        queries.register(UxmHomesQuery.class, surface);

        assertThat(api.homes()).containsSame(surface);
    }

    @Test
    void aContextCannotRegisterItsSurfaceTwice() {
        QueryContexts queries = QueryContexts.empty().register(UxmHomesQuery.class, new UnusedHomes());

        assertThatThrownBy(() -> queries.register(UxmHomesQuery.class, new UnusedHomes()))
                .as("two registrations mean two wirings of one context, which is a bootstrap bug worth failing loudly")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void versionIsWhateverTheWiringWasGiven() {
        assertThat(api(new ToggleConfig(true), "homes").version()).isEqualTo("1.2.3");
    }

    /** A plugin handle that knows only its name, which is all the front door reads off it. */
    private static Plugin plugin(String name) {
        Plugin plugin = org.mockito.Mockito.mock(Plugin.class);
        org.mockito.Mockito.when(plugin.getName()).thenReturn(name);
        return plugin;
    }

    private static UxmEssentialsApi api(ToggleConfig config, String moduleId) {
        ModuleRegistry registry = new ListModuleRegistry().register(new ToggleModule(ModuleId.of(moduleId)));
        return new UxmEssentialsApiImpl("1.2.3", registry, () -> config, new UnusedMenus());
    }

    /** A module whose gate simply reads the flag on the config handed to it, so a test can flip it mid-run. */
    private record ToggleModule(ModuleId id) implements FeatureModule {
        @Override
        public String configRoot() {
            return id.configRoot();
        }

        @Override
        public boolean enabled(ConfigStore config) {
            return config.getBoolean(configRoot() + ".enabled", false);
        }

        @Override
        public List<CommandSpec> commands() {
            return List.of();
        }

        @Override
        public List<ListenerFactory> listeners() {
            return List.of();
        }

        @Override
        public List<MigrationSet> migrations() {
            return List.of();
        }

        @Override
        public void start(ModuleContext ctx) {}

        @Override
        public void stop() {}
    }

    /** Answers every boolean with the one flag under test, so flipping it flips the module gate. */
    private static final class ToggleConfig implements ConfigStore {
        private boolean enabled;

        private ToggleConfig(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return enabled;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    /** The front door hands the menu surface straight through; this test never calls it. */
    private static final class UnusedMenus implements MenuApi {
        @Override
        public void registerAction(String id, Consumer<MenuClick> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerRequirement(String id, BiPredicate<MenuView, Map<String, String>> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerPlaceholder(String id, Function<MenuView, String> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerListSource(String id, Function<MenuView, List<?>> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerIconProvider(MenuIconProvider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack buildItem(String material, String name, List<String> lore, Player viewer) {
            throw new UnsupportedOperationException();
        }
    }

    /** Stands in for the homes surface where only its identity matters. */
    private static final class UnusedHomes implements UxmHomesQuery {
        @Override
        public CompletableFuture<List<UxmHome>> list(UUID playerId) {
            throw new AssertionError("this test never asks the surface anything");
        }

        @Override
        public CompletableFuture<Optional<UxmHome>> get(UUID playerId, int slot) {
            throw new AssertionError("this test never asks the surface anything");
        }

        @Override
        public CompletableFuture<Integer> count(UUID playerId) {
            throw new AssertionError("this test never asks the surface anything");
        }

        @Override
        public CompletableFuture<Optional<Integer>> limit(UUID playerId) {
            throw new AssertionError("this test never asks the surface anything");
        }
    }
}
