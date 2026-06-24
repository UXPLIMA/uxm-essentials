package com.uxplima.uxmessentials.warps.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.WarpEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputInstaller;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpGoToHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryEditing;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryManagerView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryParentSelectorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySelectorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySettingsView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorListener;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpManagerView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpParticleSelectorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundSelectorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpWelcomeMessagesView;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpAccess;
import com.uxplima.uxmessentials.warps.application.WarpNotifier;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage that the warp manager's "create new warp" button and the warp-category administration GUIs
 * drive the real use cases through {@link WarpEditorListener} and {@link WarpCategoryEditing}. It proves:
 *
 * <ul>
 *   <li>clicking the manager's create button arms a name prompt, and the typed name creates a warp at the
 *       operator's position through the same {@link SetWarp} path {@code /warp create} uses, then opens that
 *       warp's editor;
 *   <li>the category manager's create button creates a category from the typed id;
 *   <li>a category-settings click that edits the display name saves the renamed category;
 *   <li>a category-settings delete click removes the category.
 * </ul>
 *
 * The {@code warp.create-name} and {@code warp.category.create-name}/{@code warp.category.display-name} input
 * points are configured as chat here so the typed line round-trips through the shared {@link TextInput} chat
 * backend.
 */
class WarpManagerCategoryTest {

    private static final GuiLayout MANAGER_LAYOUT =
            GuiLayout.paginatedDefault(Material.ENDER_PEARL); // 6 rows, prev=45, next=53

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private RecordingRepository repository;
    private StubWarpCategoryRepository categories;
    private WarpManagerView managerView;
    private WarpCategoryManagerView categoryManagerView;
    private WarpCategorySettingsView categorySettingsView;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);

        Path inputDir = plugin.getDataFolder().toPath().resolve("input");
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("config.conf"), """
                default-mode = anvil
                modes {
                  "warp.create-name" = chat
                  "warp.category.create-name" = chat
                  "warp.category.display-name" = chat
                }
                """);

        Messages messages = new KeyMessages();
        MessageSink sink = (viewer, text) -> {};
        WarpNotifier notifier = new WarpNotifier(messages, sink);
        repository = new RecordingRepository();
        categories = new StubWarpCategoryRepository();
        Scheduler scheduler = new SyncScheduler();
        Permissions permissions = new AllowAllPermissions();

        var anvil = new com.uxplima.uxmlib.gui.anvil.AnvilInput(plugin);
        anvil.install();
        TextInput textInput = TextInputInstaller.install(
                        plugin,
                        plugin.getDataFolder().toPath(),
                        anvil,
                        new GuiText(messages),
                        scheduler,
                        new SilentLogger())
                .textInput();

        WarpEditorView editorView = new WarpEditorView(
                messages, scheduler, repository, WarpEditorLayout.defaultLayout(), new PlayerWarpRepositoryHandle());
        managerView = new WarpManagerView(messages, repository, scheduler, MANAGER_LAYOUT);
        categoryManagerView = new WarpCategoryManagerView(messages, categories, scheduler);
        categorySettingsView = new WarpCategorySettingsView(messages, scheduler);
        var parentSelectorView = new WarpCategoryParentSelectorView(messages, categories, scheduler);
        var categorySelectorView = new WarpCategorySelectorView(messages, categories, scheduler);
        WarpAccess access = new WarpAccess(permissions, Optional.<WarpEconomy>empty());
        UseWarp useWarp = new UseWarp(repository, access, new NoTeleport(), notifier, pos -> true, permissions);
        SetWarp setWarp = new SetWarp(repository, notifier, event -> {}, Clock.systemUTC(), List.of());
        WarpCategoryEditing categoryEditing = new WarpCategoryEditing(
                managerView,
                categoryManagerView,
                categorySettingsView,
                parentSelectorView,
                categories,
                repository,
                editorView,
                textInput,
                messages);

        WarpEditorListener listener = new WarpEditorListener(
                editorView,
                repository,
                textInput,
                messages,
                new WarpSoundSelectorView(messages, scheduler, WarpSoundSelectorView.defaultLayout()),
                new WarpParticleSelectorView(messages, scheduler, WarpParticleSelectorView.defaultLayout()),
                new WarpWelcomeMessagesView(
                        messages, scheduler, repository, editorView, WarpWelcomeMessagesView.defaultLayout()),
                useWarp,
                new PlayerWarpGoToHandle(),
                managerView,
                categorySelectorView,
                categoryEditing,
                setWarp);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void managerCreateButtonCreatesAWarpAtTheOperatorPositionAndOpensItsEditor() {
        managerView.open(player, ref());
        assertThat(player.getOpenInventory()
                        .getTopInventory()
                        .getHolder()
                        .getClass()
                        .getSimpleName())
                .isEqualTo("WarpManagerHolder");

        fireClick(MANAGER_LAYOUT.prevSlot()); // the create-new-warp button
        // The create button closed the manager to arm the name prompt — no inventory is open meanwhile.
        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);

        fireChat("market");

        assertThat(repository.exists(WarpName.of("market")))
                .as("the typed name created a warp through the SetWarp create path")
                .isTrue();
        assertThat(player.getOpenInventory()
                        .getTopInventory()
                        .getHolder()
                        .getClass()
                        .getSimpleName())
                .as("the new warp's editor opens after creation")
                .isEqualTo("WarpEditorHolder");
    }

    @Test
    void managerBackgroundFillerIsAPaneNotTheWarpFallbackIcon() {
        // MANAGER_LAYOUT's fallback icon is ENDER_PEARL — a server warp's default icon. The empty-slot filler must
        // not reuse it, or the whole manager reads as a wall of ender pearls (the "GUI looks broken" report). Slot
        // 53 is a corner that is never a warp (content is slots 0-44) nor a button (48/50/51), so it is pure filler.
        managerView.open(player, ref());
        ItemStack corner = player.getOpenInventory().getTopInventory().getItem(53);
        assertThat(corner).isNotNull();
        assertThat(corner.getType())
                .as("the background filler is a neutral pane, not the ENDER_PEARL warp fallback icon")
                .isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void categoryManagerCreateButtonCreatesACategory() {
        categoryManagerView.open(player, ref());

        fireClick(49); // create-category button

        fireChat("pvp");

        assertThat(categories.find("pvp")).isPresent();
        assertThat(player.getOpenInventory()
                        .getTopInventory()
                        .getHolder()
                        .getClass()
                        .getSimpleName())
                .isEqualTo("WarpCategorySettingsHolder");
    }

    @Test
    void categorySettingsEditsTheDisplayName() {
        categories.save(new WarpCategory("pvp", "pvp", Optional.empty(), List.of(), 0, Optional.empty()));
        categorySettingsView.open(player, ref(), categories.find("pvp").orElseThrow());

        fireClick(10); // the display-name button
        fireChat("PvP Arenas");

        assertThat(categories.find("pvp").orElseThrow().displayName()).isEqualTo("PvP Arenas");
    }

    @Test
    void categorySettingsDeleteRemovesTheCategory() {
        categories.save(new WarpCategory("pvp", "pvp", Optional.empty(), List.of(), 0, Optional.empty()));
        categorySettingsView.open(player, ref(), categories.find("pvp").orElseThrow());

        fireClick(22); // the delete button

        assertThat(categories.find("pvp")).isEmpty();
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private void fireChat(String line) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text(line));
        when(event.getHandlers()).thenReturn(AsyncChatEvent.getHandlerList());
        server.getPluginManager().callEvent(event);
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** Records saved warps so the test can prove the create button created one. */
    private static final class RecordingRepository implements WarpRepository {
        private final Map<String, Warp> warps = new LinkedHashMap<>();

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(warps.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return new ArrayList<>(warps.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return warps.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            warps.put(warp.name().value(), warp);
        }

        @Override
        public void delete(WarpName name) {
            warps.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    private static final class NoTeleport implements WarpTeleporter {
        @Override
        public void teleportTo(PlayerRef who, Warp warp) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class SilentLogger implements Logger {
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
