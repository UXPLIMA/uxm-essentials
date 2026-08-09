package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentClick;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the engine's content regions: the {@code content {}} block that hands a block of slots to a
 * feature's {@link ContentProvider} instead of drawing it from the spec. The rules that matter are the ones that keep
 * items where they belong, so each is pinned here: the region is painted over the chrome underneath it, a region with
 * no registered provider is empty and untouchable, a read-only region refuses every gesture, an editable one moves an
 * item only when its provider says so, the gestures that reach beyond the clicked slot are always refused, a
 * shift-click from the viewer's own inventory lands in the region rather than wherever vanilla would scatter it, and
 * the region's final contents reach the provider when the window closes.
 */
class MenuContentRegionTest {

    private static final String SPEC_ID = "content-region-fixture";

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private TestMenuEngine engine;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        engine.installListener(plugin);
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aRegionIsPaintedOverTheChromeUnderneathIt() {
        RecordingProvider provider = new RecordingProvider(Arrays.asList(diamond(), null, diamond()));
        register(provider, true);
        open();

        Inventory top = player.getOpenInventory().getTopInventory();
        // The filler covers the whole window, but the region wins its own three slots: two items and one hole.
        assertThat(top.getItem(0).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(top.getItem(10).getType()).isEqualTo(Material.DIAMOND);
        assertThat(top.getItem(11)).isNull();
        assertThat(top.getItem(12).getType()).isEqualTo(Material.DIAMOND);
    }

    @Test
    void aRegionWithNoRegisteredProviderStaysEmptyAndRefusesClicks() {
        // No provider is registered for the region the spec names.
        registerSpec(true);
        open();

        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top.getItem(10)).isNull();
        assertThat(click(10, ClickType.LEFT, null).isCancelled()).isTrue();
    }

    @Test
    void aReadOnlyRegionRefusesEveryClickEvenWithAProvider() {
        RecordingProvider provider = new RecordingProvider(Arrays.asList(diamond(), diamond(), diamond()));
        provider.allowEverything = true;
        register(provider, false);
        open();

        assertThat(click(10, ClickType.LEFT, null).isCancelled()).isTrue();
        assertThat(provider.asked).isEmpty();
    }

    @Test
    void anEditableRegionMovesAnItemOnlyWhenItsProviderAllowsIt() {
        RecordingProvider provider = new RecordingProvider(Arrays.asList(diamond(), null, null));
        provider.allowedKinds.add(ContentClick.Kind.TAKE);
        register(provider, true);
        open();

        // Taking the diamond out is allowed, so the engine lifts its blanket cancel and vanilla performs the move.
        assertThat(click(10, ClickType.LEFT, null).isCancelled()).isFalse();
        // Putting an item in is not, so that click stays cancelled.
        assertThat(click(11, ClickType.LEFT, diamond()).isCancelled()).isTrue();
        assertThat(provider.asked)
                .extracting(ContentClick::kind)
                .containsExactly(ContentClick.Kind.TAKE, ContentClick.Kind.INSERT);
    }

    @Test
    void theGesturesThatReachBeyondTheClickedSlotAreAlwaysRefused() {
        RecordingProvider provider = new RecordingProvider(Arrays.asList(diamond(), diamond(), diamond()));
        provider.allowEverything = true;
        register(provider, true);
        open();

        // A double-click gathers matching stacks from the whole window, including the chrome tiles, so it can never
        // be let through; the same goes for a hotbar swap, whose source slot the provider was not asked about.
        assertThat(click(10, ClickType.DOUBLE_CLICK, null).isCancelled()).isTrue();
        assertThat(click(10, ClickType.NUMBER_KEY, null).isCancelled()).isTrue();
        assertThat(click(10, ClickType.SWAP_OFFHAND, null).isCancelled()).isTrue();
        assertThat(provider.asked).isEmpty();
    }

    @Test
    void aShiftClickFromTheViewersInventoryLandsInTheFirstFreeRegionSlot() {
        RecordingProvider provider = new RecordingProvider(Arrays.asList(diamond(), null, null));
        provider.allowEverything = true;
        register(provider, true);
        open();
        player.getInventory().setItem(0, diamond());

        InventoryClickEvent event = click(bottomRawSlot(0), ClickType.SHIFT_LEFT, null);

        Inventory top = player.getOpenInventory().getTopInventory();
        // The event stays cancelled: the engine performed the insert itself, into the region's first free slot.
        assertThat(event.isCancelled()).isTrue();
        assertThat(top.getItem(11).getType()).isEqualTo(Material.DIAMOND);
        assertThat(player.getInventory().getItem(0)).isNull();
    }

    @Test
    void aShiftClickIsLeftAloneWhenNoRegionAcceptsIt() {
        RecordingProvider provider = new RecordingProvider(Arrays.asList(null, null, null));
        register(provider, true); // provider refuses everything by default
        open();
        player.getInventory().setItem(0, diamond());

        InventoryClickEvent event = click(bottomRawSlot(0), ClickType.SHIFT_LEFT, null);

        assertThat(event.isCancelled()).isTrue();
        assertThat(player.getOpenInventory().getTopInventory().getItem(10)).isNull();
        assertThat(player.getInventory().getItem(0).getType()).isEqualTo(Material.DIAMOND);
    }

    @Test
    void aDragGoesThroughOnlyWhileItStaysInsideAnAcceptingRegion() {
        RecordingProvider provider = new RecordingProvider(Arrays.asList(null, null, null));
        provider.allowEverything = true;
        register(provider, true);
        open();

        // Spread across two region slots: every touched top slot belongs to the region and its provider accepts.
        assertThat(drag(Map.of(10, diamond(), 11, diamond())).isCancelled()).isFalse();
        // The same drag reaching one slot of chrome is refused outright, so a drag can never paint over a button.
        assertThat(drag(Map.of(10, diamond(), 9, diamond())).isCancelled()).isTrue();
    }

    @Test
    void theRegionsFinalContentsReachTheProviderOnClose() {
        RecordingProvider provider = new RecordingProvider(Arrays.asList(diamond(), null, null));
        provider.allowEverything = true;
        register(provider, true);
        open();
        player.getOpenInventory().getTopInventory().setItem(11, new ItemStack(Material.EMERALD));

        player.closeInventory();

        assertThat(provider.readBack).hasSize(3);
        assertThat(provider.readBack.get(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(provider.readBack.get(1).getType()).isEqualTo(Material.EMERALD);
        assertThat(provider.readBack.get(2)).isNull();
    }

    // --- helpers ---

    /** Register {@code provider} under the fixture region id, then the spec that names it. */
    private void register(ContentProvider provider, boolean editable) {
        engine.bindings().content("test:region", provider);
        registerSpec(editable);
    }

    /**
     * A three-row window: a filler across every slot and a three-slot content region at 10, 11 and 12, parsed from
     * the same HOCON an operator would write, so the loader's own parse of the block is exercised here too.
     */
    private void registerSpec(boolean editable) {
        engine.menus().registerSpec(SPEC_ID, new MenuSpecLoader().parse("""
                        title = "Content fixture"
                        rows = 3

                        content {
                          "test:region" {
                            slots    = ["10-12"]
                            editable = %s
                          }
                        }

                        items {
                          filler {
                            slots    = ["0-26"]
                            material = GRAY_STAINED_GLASS_PANE
                            name     = ""
                            priority = 0
                          }
                        }
                        """.formatted(editable)));
    }

    private void open() {
        engine.menus().open(viewer, SPEC_ID, null);
    }

    /** Dispatch a click on {@code rawSlot} with {@code cursor} held, and hand the event back for its cancel state. */
    private InventoryClickEvent click(int rawSlot, ClickType type, ItemStack cursor) {
        player.setItemOnCursor(cursor == null ? new ItemStack(Material.AIR) : cursor);
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(),
                rawSlot < player.getOpenInventory().getTopInventory().getSize()
                        ? InventoryType.SlotType.CONTAINER
                        : InventoryType.SlotType.QUICKBAR,
                rawSlot,
                type,
                InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** Dispatch a drag that would place {@code newItems} into the window, and hand the event back. */
    private InventoryDragEvent drag(Map<Integer, ItemStack> newItems) {
        InventoryDragEvent event = new InventoryDragEvent(player.getOpenInventory(), null, diamond(), false, newItems);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /**
     * The raw slot of the viewer's own inventory slot {@code index} in the open view: the bottom inventory always
     * starts where the top ends, which is the only thing the engine's routing reads (it asks the event itself for
     * the clicked stack rather than mapping the index).
     */
    private int bottomRawSlot(int index) {
        return player.getOpenInventory().getTopInventory().getSize() + index;
    }

    private static ItemStack diamond() {
        return new ItemStack(Material.DIAMOND);
    }

    /** A provider that records what it was asked and answers by the flags a test sets. */
    private static final class RecordingProvider implements ContentProvider {

        private final List<ItemStack> painted;
        private final List<ContentClick> asked = new ArrayList<>();
        private final List<ContentClick.Kind> allowedKinds = new ArrayList<>();
        private final List<ItemStack> readBack = new ArrayList<>();
        private boolean allowEverything;

        private RecordingProvider(List<ItemStack> painted) {
            this.painted = new ArrayList<>(painted);
        }

        @Override
        public List<ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
            return painted;
        }

        @Override
        public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
            asked.add(click);
            return allowEverything || allowedKinds.contains(click.kind());
        }

        @Override
        public void readBack(MenuContext ctx, ContentRegionSpec region, List<ItemStack> contents) {
            readBack.clear();
            readBack.addAll(Arrays.asList(contents.toArray(new ItemStack[0])));
        }
    }

    private static final class KeyMessages implements Messages {
        private final Map<String, String> resolved = new HashMap<>();

        @Override
        public String resolve(PlayerRef who, MessageKey key, Map<String, String> placeholders) {
            resolved.put(key.key(), key.key());
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
