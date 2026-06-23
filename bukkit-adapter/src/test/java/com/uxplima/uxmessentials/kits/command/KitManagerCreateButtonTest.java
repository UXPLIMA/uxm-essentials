package com.uxplima.uxmessentials.kits.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitSettingsView;
import com.uxplima.uxmessentials.kits.adapter.inbound.listener.ChatPromptListener;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitNotifier;
import com.uxplima.uxmessentials.kits.application.KitReset;
import com.uxplima.uxmessentials.kits.application.ListKits;
import com.uxplima.uxmessentials.kits.application.ShowKit;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage that the kit manager's "create new kit" button starts kit creation directly — the kit
 * name prompt, then the new kit's settings window — instead of opening the old create-kit/create-category
 * chooser (category creation already lives in the category manager, so the chooser was redundant). Clicking
 * the create-button slot of an open {@link KitManagerView} closes the manager (the prompt path closes it) and
 * arms the chat prompt; the next chat line names the kit, which is created in the repository and its settings
 * window opens.
 */
class KitManagerCreateButtonTest {

    // The manager layout default: 6 rows, prev/create button at slot 49, close at 53.
    private static final GuiLayout MANAGER_LAYOUT =
            new GuiLayout(6, Material.GRAY_STAINED_GLASS_PANE, Material.ARROW, 49, 53, List.of());
    private static final GuiLayout SETTINGS_LAYOUT = new GuiLayout(
            3,
            Material.GRAY_STAINED_GLASS_PANE,
            Material.ARROW,
            26,
            22,
            List.of(0, 2, 4, 6, 8, 10, 12, 14, 16, 22, 18, 20, 24));
    private static final GuiLayout BROWSE_LAYOUT = GuiLayout.paginatedDefault(Material.CHEST);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private RecordingRepository repository;
    private KitManagerView managerView;
    private ChatPromptListener prompts;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);

        Messages messages = new KeyMessages();
        MessageSink sink = (viewer, text) -> {};
        KitNotifier notifier = new KitNotifier(messages, sink);
        repository = new RecordingRepository();
        Scheduler scheduler = new SyncScheduler();

        managerView = new KitManagerView(messages, repository, scheduler, MANAGER_LAYOUT);
        KitSettingsView settingsView = new KitSettingsView(messages, scheduler, SETTINGS_LAYOUT);
        prompts = new ChatPromptListener(messages);
        KitEditor kitEditor = new KitEditor(repository, notifier);
        KitEditorView editorView = new KitEditorView(messages, kitEditor, scheduler);
        KitServices services = services(messages, notifier, scheduler, kitEditor, editorView);

        KitEditorListener listener = new KitEditorListener(
                editorView, services, repository, new StubCategoryRepository(), settingsView, prompts, messages);
        server.getPluginManager().registerEvents(listener, plugin);
        server.getPluginManager().registerEvents(prompts, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createButtonClosesTheManagerForTheNamePromptInsteadOfOpeningAChooser() {
        managerView.open(player, ref(player));
        Inventory manager = player.getOpenInventory().getTopInventory();
        assertThat(manager.getHolder().getClass().getSimpleName()).isEqualTo("KitManagerHolder");

        fireClick(49); // the create-new-kit button

        // Going straight to kit creation means the create button closes the manager to ask for a kit name —
        // it must NOT open any further inventory (the removed create-kit/create-category chooser would have).
        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
    }

    @Test
    void namingTheKitCreatesItAndOpensItsSettings() {
        managerView.open(player, ref(player));
        fireClick(49);

        // Drive the kit-name prompt callback directly (MockBukkit fires chat off-thread), the same callback the
        // chat listener invokes: it creates the kit and opens its settings window.
        armedPrompt().accept("freshkit");

        assertThat(repository.exists(KitId.of("freshkit"))).isTrue();
        assertThat(player.getOpenInventory()
                        .getTopInventory()
                        .getHolder()
                        .getClass()
                        .getSimpleName())
                .isEqualTo("KitSettingsHolder");
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /**
     * The chat-prompt callback the create button armed for this player, read from the listener's pending-prompt
     * map. Invoking it on the test thread runs the same name-to-kit flow the chat listener triggers, without
     * MockBukkit's off-thread chat firing the inventory open asynchronously.
     */
    @SuppressWarnings("unchecked") // the listener stores UUID -> Consumer<String>; the map type is known here
    private java.util.function.Consumer<String> armedPrompt() {
        try {
            java.lang.reflect.Field field = ChatPromptListener.class.getDeclaredField("activePrompts");
            field.setAccessible(true);
            Map<UUID, java.util.function.Consumer<String>> active =
                    (Map<UUID, java.util.function.Consumer<String>>) field.get(prompts);
            java.util.function.Consumer<String> callback = active.get(player.getUniqueId());
            assertThat(callback)
                    .as("a kit-name prompt is armed after clicking create")
                    .isNotNull();
            return callback;
        } catch (ReflectiveOperationException e) {
            throw new LinkageError("could not read the armed chat prompt", e);
        }
    }

    private KitServices services(
            Messages messages,
            KitNotifier notifier,
            Scheduler scheduler,
            KitEditor kitEditor,
            KitEditorView editorView) {
        Permissions permissions = new AllowAllPermissions();
        KitClaimStore claims = new NoClaims();
        KitAccess access = new KitAccess(permissions, new NoCooldowns(), claims, Optional.<KitEconomy>empty());
        ClaimKit claimKit = new ClaimKit(
                repository, access, new NoGranter(), notifier, new NoEvents(), Clock.systemUTC(), Optional.empty());
        KitPreviewView preview = new KitPreviewView(messages, scheduler, BROWSE_LAYOUT);
        KitMenuView menu = new KitMenuView(
                messages,
                notifier,
                scheduler,
                claimKit,
                new StubCategoryRepository(),
                access,
                preview,
                BROWSE_LAYOUT,
                Clock.systemUTC());
        return new KitServices(
                claimKit,
                new ListKits(repository, permissions, claims, notifier),
                new ShowKit(repository, notifier),
                new CreateKit(repository, notifier),
                new DelKit(repository, notifier),
                kitEditor,
                new KitReset(repository, claims, notifier),
                menu,
                preview,
                editorView,
                managerView,
                new NoPlayerLookup(),
                null,
                null,
                null,
                null);
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** A repository that records created kits so the test can prove the create button created one. */
    private static final class RecordingRepository implements KitRepository {
        private final List<KitDefinition> kits = new CopyOnWriteArrayList<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return kits.stream().filter(kit -> kit.id().equals(id)).findFirst();
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(kits);
        }

        @Override
        public boolean exists(KitId id) {
            return find(id).isPresent();
        }

        @Override
        public void save(KitDefinition definition) {
            kits.removeIf(kit -> kit.id().equals(definition.id()));
            kits.add(definition);
        }

        @Override
        public void delete(KitId id) {
            kits.removeIf(kit -> kit.id().equals(id));
        }
    }

    private static final class StubCategoryRepository implements KitCategoryRepository {
        @Override
        public Optional<KitCategory> find(String id) {
            return Optional.empty();
        }

        @Override
        public List<KitCategory> all() {
            return List.of();
        }

        @Override
        public void save(KitCategory category) {}

        @Override
        public void delete(String id) {}
    }

    private static final class NoClaims implements KitClaimStore {
        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return false;
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {}

        @Override
        public void reset(PlayerRef who, KitId kit) {}

        @Override
        public void resetAll(PlayerRef who) {}
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                check(PlayerRef who, CooldownKind kind) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                checkLabel(PlayerRef who, String label) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoGranter implements KitGranter {
        @Override
        public Grant grant(PlayerRef recipient, KitDefinition kit) {
            return Grant.complete();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
