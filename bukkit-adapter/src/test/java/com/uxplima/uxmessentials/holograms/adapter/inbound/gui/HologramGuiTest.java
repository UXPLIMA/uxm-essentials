package com.uxplima.uxmessentials.holograms.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.AddHologramAction;
import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.AddHologramPage;
import com.uxplima.uxmessentials.holograms.application.CenterHologram;
import com.uxplima.uxmessentials.holograms.application.ClearHologramActions;
import com.uxplima.uxmessentials.holograms.application.CopyHologram;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.DescribeHologram;
import com.uxplima.uxmessentials.holograms.application.HologramNotifier;
import com.uxplima.uxmessentials.holograms.application.InsertHologramAction;
import com.uxplima.uxmessentials.holograms.application.InsertHologramLine;
import com.uxplima.uxmessentials.holograms.application.LinkHologramToNpc;
import com.uxplima.uxmessentials.holograms.application.ListHologramActions;
import com.uxplima.uxmessentials.holograms.application.ListHologramPages;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.ManageHologramBlacklist;
import com.uxplima.uxmessentials.holograms.application.ManageHologramViewer;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.MoveHologramAction;
import com.uxplima.uxmessentials.holograms.application.NearbyHolograms;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramAction;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramPage;
import com.uxplima.uxmessentials.holograms.application.RotateHologram;
import com.uxplima.uxmessentials.holograms.application.SetHologramAction;
import com.uxplima.uxmessentials.holograms.application.SetHologramAppearance;
import com.uxplima.uxmessentials.holograms.application.SetHologramClickCommand;
import com.uxplima.uxmessentials.holograms.application.SetHologramGrowUp;
import com.uxplima.uxmessentials.holograms.application.SetHologramLeaderboard;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramModel;
import com.uxplima.uxmessentials.holograms.application.SetHologramRefresh;
import com.uxplima.uxmessentials.holograms.application.SetHologramVisibility;
import com.uxplima.uxmessentials.holograms.application.TeleportToHologram;
import com.uxplima.uxmessentials.holograms.application.UnlinkHologramFromNpc;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramTeleporter;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContexts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the holograms property editor: a toggle property (text-shadow) cycles and persists
 * through the appearance use case; a number property (scale) steps, clamps, and persists; a text property (name)
 * routes a validated anvil line to a rename; a list property (lines) adds a line through the line use case; the
 * colour buttons open the picker and persist a swatch; and the delete button is gated behind a confirm menu. The
 * editor is laid out from a temp conf (no hardcoded slots); the scheduler runs every hop inline so the off-thread
 * writes land synchronously. The list that opens this editor is covered by {@code HologramListGoldenTest}.
 */
class HologramGuiTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    // Editor property slots, in the order HologramEditorView builds its properties.
    private static final List<Integer> EDITOR_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42,
            43, 46, 47, 48);
    private static final int NAME_SLOT = EDITOR_SLOTS.get(0);
    private static final int TELEPORT_SLOT = EDITOR_SLOTS.get(2); // "Teleport here", inserted after "Move here"
    private static final int LINES_SLOT = EDITOR_SLOTS.get(3);
    private static final int SCALE_SLOT = EDITOR_SLOTS.get(4);
    private static final int TEXT_SHADOW_SLOT = EDITOR_SLOTS.get(13);
    private static final int BACKGROUND_SLOT = EDITOR_SLOTS.get(23);
    private static final int GLOW_SLOT = EDITOR_SLOTS.get(24);
    private static final int DELETE_SLOT = 53;
    private static final int CONFIRM_SLOT =
            11; // the engine confirm window's yes button slot (ConfirmRenderer.YES_SLOT)

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private HologramServices services;
    private HologramEditorView editorView;
    private final List<Position> teleportDestinations = new java.util.ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new FakeRepository();
        services = assembleServices();
        Guis.install(plugin);

        EntityEditorLayout editorLayout = editorLayout(dir);
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        editorView = new HologramEditorView(
                editorEngine(),
                guiText,
                scheduler,
                repository,
                services,
                textInput,
                new FakeLookup(),
                new KeyMessages(),
                editorLayout,
                HologramEditorSubLayouts.codeDefault(),
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout.codeDefault(),
                (p, v) -> {});
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void togglePropertyCyclesAndPersistsThroughTheUseCase() {
        create("alpha");
        editorView.open(
                player, viewer, repository.find(HologramName.of("alpha")).orElseThrow());
        assertThat(appearance("alpha").textShadow()).isFalse();

        fireClick(TEXT_SHADOW_SLOT, ClickType.LEFT);

        assertThat(appearance("alpha").textShadow()).isTrue();
    }

    @Test
    void numberPropertyStepsClampsAndPersists() {
        create("alpha");
        editorView.open(
                player, viewer, repository.find(HologramName.of("alpha")).orElseThrow());
        // Scale starts at the default 1.0 (×100 = 100); a +10 step lands at 110 → scale 1.1.
        fireClick(SCALE_SLOT, ClickType.LEFT);
        assertThat(Math.round(appearance("alpha").scale() * 100)).isEqualTo(110);

        // Step down past the floor: it clamps at the minimum scale (0.05 → ×100 = 5), never below.
        for (int i = 0; i < 50; i++) {
            fireClick(SCALE_SLOT, ClickType.RIGHT);
        }
        assertThat(Math.round(appearance("alpha").scale() * 100)).isEqualTo(5);
    }

    @Test
    void textPropertyRoutesAnvilInputToRename() {
        create("alpha");
        Hologram holo = repository.find(HologramName.of("alpha")).orElseThrow();
        EditableProperty name = editorView.grid().propertyAt(NAME_SLOT, holo).orElseThrow();
        assertThat(name).isInstanceOf(TextProperty.class);

        ((TextProperty) name).applyInput(ClickContexts.carrier(player, viewer), "renamed");

        assertThat(repository.find(HologramName.of("renamed"))).isPresent();
        assertThat(repository.find(HologramName.of("alpha"))).isEmpty();
    }

    @Test
    void listPropertyAddsALineThroughTheUseCase() {
        create("alpha");
        // The lines list setter is the same path the sub-menu's add uses; drive it directly with a longer list.
        Hologram holo = repository.find(HologramName.of("alpha")).orElseThrow();
        EditableProperty lines = editorView.grid().propertyAt(LINES_SLOT, holo).orElseThrow();
        assertThat(lines.label().key()).isEqualTo("hologram.gui.prop.lines");

        // Apply a two-line list (the original line plus a new one) through the property's setter seam.
        applyLines("alpha", List.of("alpha", "second line"));

        List<HologramLine> stored =
                repository.find(HologramName.of("alpha")).orElseThrow().lines();
        assertThat(stored).hasSize(2);
        assertThat(stored.get(1).value()).isEqualTo("second line");
    }

    @Test
    void backgroundColourButtonOpensThePickerWithThePalette() {
        create("alpha");
        editorView.open(
                player, viewer, repository.find(HologramName.of("alpha")).orElseThrow());

        fireClick(BACKGROUND_SLOT, ClickType.LEFT);

        // The picker's first palette slot (code-default slot 10) carries the white swatch's stained-glass pane.
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(10).getType()).isEqualTo(Material.WHITE_STAINED_GLASS_PANE);
        assertThat(inv.getItem(11).getType()).isEqualTo(Material.ORANGE_STAINED_GLASS_PANE);
    }

    @Test
    void pickingAPaletteColourPersistsThroughTheAppearanceUseCase() {
        create("alpha");
        editorView.open(
                player, viewer, repository.find(HologramName.of("alpha")).orElseThrow());
        assertThat(appearance("alpha").hasBackground()).isFalse();

        fireClick(BACKGROUND_SLOT, ClickType.LEFT); // open the picker
        fireClick(10, ClickType.LEFT); // the white swatch

        // White is opaque (0xFFFFFFFF), the packed ARGB the white swatch selects.
        assertThat(appearance("alpha").backgroundArgb()).isEqualTo(0xFFFFFFFF);
    }

    @Test
    void glowColourButtonPicksAndPersists() {
        create("alpha");
        editorView.open(
                player, viewer, repository.find(HologramName.of("alpha")).orElseThrow());
        assertThat(appearance("alpha").hasGlow()).isFalse();

        fireClick(GLOW_SLOT, ClickType.LEFT); // open the glow picker
        fireClick(11, ClickType.LEFT); // the orange swatch (0xFFD87F33)

        assertThat(appearance("alpha").glowArgb()).isEqualTo(0xFFD87F33);
    }

    @Test
    void customHexParsesAValidColourAndRejectsAnInvalidOne() {
        create("alpha");
        Hologram holo = repository.find(HologramName.of("alpha")).orElseThrow();
        ColourProperty background = (ColourProperty)
                editorView.grid().propertyAt(BACKGROUND_SLOT, holo).orElseThrow();
        var ctx = ClickContexts.carrier(player, viewer);

        background.applyCustom(ctx, "#112233");
        assertThat(appearance("alpha").backgroundArgb()).isEqualTo(0xFF112233);

        // An invalid line leaves the previously-set colour untouched.
        background.applyCustom(ctx, "not-a-colour");
        assertThat(appearance("alpha").backgroundArgb()).isEqualTo(0xFF112233);
    }

    @Test
    void clearButtonResetsTheColourToItsSentinel() {
        create("alpha");
        // Seed an explicit background, then clear it back to the no-override sentinel.
        services.appearance().apply(viewer, HologramName.of("alpha"), a -> a.withBackgroundArgb(0xFF112233));
        editorView.open(
                player, viewer, repository.find(HologramName.of("alpha")).orElseThrow());
        assertThat(appearance("alpha").hasBackground()).isTrue();

        fireClick(BACKGROUND_SLOT, ClickType.LEFT); // open the picker
        fireClick(42, ClickType.LEFT); // the clear button (code-default slot 42)

        assertThat(appearance("alpha").backgroundArgb()).isEqualTo(Appearance.DEFAULT_BACKGROUND);
        assertThat(appearance("alpha").hasBackground()).isFalse();
    }

    @Test
    void deleteIsGatedByAConfirmMenu() {
        create("alpha");
        editorView.open(
                player, viewer, repository.find(HologramName.of("alpha")).orElseThrow());

        fireClick(DELETE_SLOT, ClickType.LEFT); // opens the confirm menu, does not delete
        assertThat(repository.find(HologramName.of("alpha"))).isPresent();

        fireClick(CONFIRM_SLOT, ClickType.LEFT); // the confirm button deletes
        assertThat(repository.find(HologramName.of("alpha"))).isEmpty();
    }

    @Test
    void teleportButtonSendsTheViewerToTheHologramLocation() {
        create("alpha");
        editorView.open(
                player, viewer, repository.find(HologramName.of("alpha")).orElseThrow());

        fireClick(TELEPORT_SLOT, ClickType.LEFT);

        assertThat(teleportDestinations).containsExactly(AT);
    }

    // --- helpers ---

    private void create(String name) {
        services.create().create(viewer, HologramName.of(name), AT, HologramLine.of(name));
    }

    /** Drive the lines use-case path the GUI list setter runs: append the new tail, set the overlap. */
    private void applyLines(String name, List<String> next) {
        HologramName id = HologramName.of(name);
        List<HologramLine> current = repository.find(id).orElseThrow().lines();
        for (int i = 0; i < next.size(); i++) {
            HologramLine line = HologramLine.of(next.get(i));
            if (i < current.size()) {
                services.setLine().set(viewer, id, i, line);
            } else {
                services.addLine().add(viewer, id, line);
            }
        }
        for (int i = current.size() - 1; i >= next.size(); i--) {
            services.removeLine().remove(viewer, id, i);
        }
    }

    private Appearance appearance(String name) {
        return repository.find(HologramName.of(name)).orElseThrow().appearance();
    }

    /**
     * One editor-capable engine plus its single listener: the editor opens through this {@link Menus} and its colour
     * picker, list and delete-confirm children are routed by the one registered {@link MenuListener}, the engine path
     * the production wiring uses.
     */
    private Menus editorEngine() {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        return menus;
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private EntityEditorLayout editorLayout(Path dir) throws Exception {
        Path file = dir.resolve("modules").resolve("holograms").resolve("gui").resolve("hologram-editor.conf");
        Files.createDirectories(file.getParent());
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < EDITOR_SLOTS.size(); i++) {
            slots.append(EDITOR_SLOTS.get(i));
            if (i < EDITOR_SLOTS.size() - 1) {
                slots.append(", ");
            }
        }
        Files.writeString(
                file,
                "rows = 6\nback-icon = \"ARROW\"\ndelete-icon = \"BARRIER\"\n"
                        + "filler = \"BLACK_STAINED_GLASS_PANE\"\nback-slot = 49\ndelete-slot = 53\n"
                        + "property-slots = [" + slots + "]\n");
        EntityEditorLayout codeDefault = new EntityEditorLayout(
                6,
                EDITOR_SLOTS,
                49,
                java.util.OptionalInt.of(53),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
        return new GuiLayouts(dir, NOOP).loadEntityEditor("holograms", "hologram-editor", codeDefault);
    }

    private HologramServices assembleServices() {
        HologramNotifier notifier = new HologramNotifier(new KeyMessages(), new SilentSink());
        HologramView view = new SilentView();
        DomainEventPublisher events = new SilentEvents();
        HologramTeleporter teleporter = (who, destination) -> teleportDestinations.add(destination);
        LinkedNpcLocator npc = name -> Optional.of(AT);
        return new HologramServices(
                new CreateHologram(repository, view, notifier, events, Clock.systemUTC()),
                new DeleteHologram(repository, view, notifier, events),
                new ListHolograms(repository, notifier),
                new AddHologramLine(repository, view, notifier),
                new SetHologramLine(repository, view, notifier),
                new InsertHologramLine(repository, view, notifier),
                new RemoveHologramLine(repository, view, notifier),
                new MoveHologram(repository, view, notifier),
                new CenterHologram(repository, view, notifier),
                new TeleportToHologram(repository, teleporter, notifier),
                new CopyHologram(repository, view, notifier),
                new RotateHologram(repository, view, notifier),
                new DescribeHologram(repository, notifier),
                new NearbyHolograms(repository, notifier),
                new SetHologramAppearance(repository, view, notifier),
                new SetHologramRefresh(repository, view, notifier),
                new SetHologramVisibility(repository, view, notifier),
                new ManageHologramViewer(repository, view, notifier),
                new SetHologramModel(repository, view, notifier),
                new SetHologramClickCommand(repository, view, notifier),
                new SetHologramLeaderboard(repository, view, notifier),
                new LinkHologramToNpc(repository, view, notifier, npc),
                new UnlinkHologramFromNpc(repository, view, notifier),
                new AddHologramPage(repository, view, notifier),
                new RemoveHologramPage(repository, view, notifier),
                new ListHologramPages(repository, notifier),
                new SetHologramGrowUp(repository, view, notifier),
                new ManageHologramBlacklist(repository, view, notifier),
                new AddHologramAction(repository, notifier),
                new InsertHologramAction(repository, notifier),
                new SetHologramAction(repository, notifier),
                new MoveHologramAction(repository, notifier),
                new RemoveHologramAction(repository, notifier),
                new ClearHologramActions(repository, notifier),
                new ListHologramActions(repository, notifier));
    }

    // --- fakes ---

    private static final class FakeRepository implements HologramRepository {
        private final java.util.Map<String, Hologram> byName = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, java.util.Set<UUID>> manual = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, java.util.Set<UUID>> black = new java.util.LinkedHashMap<>();

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            byName.put(hologram.name().value(), hologram);
        }

        @Override
        public void delete(HologramName name) {
            byName.remove(name.value());
            manual.remove(name.value());
            black.remove(name.value());
        }

        @Override
        public java.util.Set<UUID> manualViewers(HologramName name) {
            return new java.util.LinkedHashSet<>(manual.getOrDefault(name.value(), java.util.Set.of()));
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {
            manual.computeIfAbsent(name.value(), k -> new java.util.LinkedHashSet<>())
                    .add(viewer);
        }

        @Override
        public void hideFrom(HologramName name, UUID viewer) {
            java.util.Set<UUID> v = manual.get(name.value());
            if (v != null) {
                v.remove(viewer);
            }
        }

        @Override
        public java.util.Set<UUID> blacklisted(HologramName name) {
            return new java.util.LinkedHashSet<>(black.getOrDefault(name.value(), java.util.Set.of()));
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {
            black.computeIfAbsent(name.value(), k -> new java.util.LinkedHashSet<>())
                    .add(viewer);
        }

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {
            java.util.Set<UUID> v = black.get(name.value());
            if (v != null) {
                v.remove(viewer);
            }
        }
    }

    private static final class SilentView implements HologramView {
        @Override
        public void render(Hologram hologram) {}

        @Override
        public void despawn(HologramName name) {}

        @Override
        public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {}
    }

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class FakeLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.of(new PlayerRef(
                    UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.of(new PlayerRef(uuid, "player"));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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
}
