package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ActionProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /menu editor} item property editor: clicking a filled grid cell opens a
 * holder-backed engine editor whose rows are the item's fields. A {@link NumberProperty} click steps the amount, a
 * {@link ToggleProperty} click flips the glow, the capture button copies the held item into the material slot as a
 * {@code b64:} token, Back returns to the grid, and an edit made here shows in the reloaded menu once the grid saves.
 * Everything is an engine window, so the editor never touches a raw Bukkit inventory.
 */
class MenuItemEditorViewTest {

    private static final int GRID_SAVE_SLOT = 14; // grid control row (9) + SAVE_COLUMN 5
    private static final int MATERIAL_SLOT = 10; // PROPERTY_SLOTS[0]
    private static final int CAPTURE_SLOT = 11; // PROPERTY_SLOTS[1]
    private static final int AMOUNT_SLOT = 15; // PROPERTY_SLOTS[5]
    private static final int GLOW_SLOT = 20; // PROPERTY_SLOTS[8]
    private static final int LORE_MODE_SLOT = 21; // PROPERTY_SLOTS[9]
    private static final int CLICK_ACTIONS_SLOT = 23; // PROPERTY_SLOTS[11]
    private static final int REQUIREMENTS_SLOT = 24; // PROPERTY_SLOTS[12]
    private static final int GESTURE_LEFT_SLOT = 10; // MenuClickActionsView.GESTURE_SLOTS[0] (ClickKind.LEFT)
    private static final int REF_ADD_SLOT = 48; // MenuRefListEditor.ADD_SLOT
    private static final int REQ_CONDITIONS_SLOT = 11; // MenuRequirementsView.PROPERTY_SLOTS[0]
    private static final int EDITOR_BACK_SLOT = 49;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;
    private CustomMenuLoader loader;
    private final List<String> names = new ArrayList<>();
    private MenuGridView grid;
    private MenuItemEditorView itemEditor;
    private MenuRefListEditor refListEditor;

    @TempDir
    Path menusDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        GuiText guiText = new GuiText(new KeyMessages());
        Scheduler scheduler = new SyncScheduler();

        MenuBindings bindings = new MenuBindings();
        bindings.action("close", ctx -> {});
        bindings.action("message", ctx -> {});
        bindings.action("sound", ctx -> {});
        bindings.condition("perm", (ctx, args) -> true);
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
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

        loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, menus, NOOP);
        writeMenu("alpha", 1, "x { slot = 0, material = STONE, click { left = [\"close\"] } }");
        names.addAll(loader.loadFrom(menusDir).loadedNames());

        MenuSpecPersistence persistence = new MenuSpecPersistence(new MenuSpecWriter(), bindings, NOOP);
        MenuEditorService service = new MenuEditorService(
                menusDir,
                persistence,
                menus::registeredSpec,
                name -> Optional.empty(),
                this::reloadOne,
                this::forget,
                NOOP);
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        AtomicReference<MenuGridView> gridRef = new AtomicReference<>();
        AtomicReference<MenuItemEditorView> itemEditorRef = new AtomicReference<>();
        refListEditor = new MenuRefListEditor(guiText, scheduler, textInput, "menu.action-editor.arg");
        MenuClickActionsView clickActions = new MenuClickActionsView(guiText, refListEditor, bindings::schema);
        MenuRequirementsView requirements = new MenuRequirementsView(
                menus,
                guiText,
                scheduler,
                new GuiLayouts(menusDir, NOOP),
                refListEditor,
                bindings::schema,
                (p, v) -> Objects.requireNonNull(itemEditorRef.get()).reopen(p, v));
        itemEditor = new MenuItemEditorView(
                menus,
                guiText,
                scheduler,
                new KeyMessages(),
                textInput,
                new GuiLayouts(menusDir, NOOP),
                (p, v) -> Objects.requireNonNull(gridRef.get()).reopenGrid(v),
                clickActions,
                requirements);
        itemEditorRef.set(itemEditor);
        grid = new MenuGridView(menus, guiText, scheduler, service, menus::registeredSpec, (p, id) -> {}, itemEditor);
        gridRef.set(grid);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void clickingAFilledCellOpensTheItemEditorWithItsPropertyRows() {
        grid.open(player, viewer, "alpha");

        fireClick(0, ClickType.LEFT); // filled cell 0 opens the item editor

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getSize()).isEqualTo(54); // the six-row property editor
        assertThat(inv.getItem(MATERIAL_SLOT).getType()).isEqualTo(Material.STONE); // material field
        assertThat(inv.getItem(AMOUNT_SLOT).getType()).isEqualTo(Material.PAPER); // amount field
        assertThat(inv.getItem(GLOW_SLOT).getType()).isEqualTo(Material.GLOWSTONE_DUST); // glow field
        assertThat(inv.getItem(EDITOR_BACK_SLOT).getType()).isEqualTo(Material.ARROW); // back
    }

    @Test
    void theRowsResolveToTheExpectedPropertyTypes() {
        grid.open(player, viewer, "alpha");
        MenuEditSession session = grid.editSession(viewer.uuid()).orElseThrow();

        assertThat(itemEditor.propertyAt(MATERIAL_SLOT, session, "x")).get().isInstanceOf(TextProperty.class);
        assertThat(itemEditor.propertyAt(CAPTURE_SLOT, session, "x")).get().isInstanceOf(ActionProperty.class);
        assertThat(itemEditor.propertyAt(13, session, "x")).get().isInstanceOf(ListProperty.class); // lore
        assertThat(itemEditor.propertyAt(AMOUNT_SLOT, session, "x")).get().isInstanceOf(NumberProperty.class);
        assertThat(itemEditor.propertyAt(GLOW_SLOT, session, "x")).get().isInstanceOf(ToggleProperty.class);
        assertThat(itemEditor.propertyAt(LORE_MODE_SLOT, session, "x")).get().isInstanceOf(EnumProperty.class);
    }

    @Test
    void aNumberPropertyClickStepsTheAmountInTheSession() {
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT); // open the item editor

        fireClick(AMOUNT_SLOT, ClickType.LEFT); // +1 from the default amount of 1

        assertThat(session().item("x").orElseThrow().decor().amount()).isEqualTo(2);
    }

    @Test
    void aTogglePropertyClickFlipsTheGlowInTheSession() {
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT);

        fireClick(GLOW_SLOT, ClickType.LEFT);

        assertThat(session().item("x").orElseThrow().decor().glow()).isTrue();
    }

    @Test
    void captureFromHandSetsTheMaterialToTheHeldItemsToken() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT);

        fireClick(CAPTURE_SLOT, ClickType.LEFT);

        assertThat(session().item("x").orElseThrow().material()).startsWith("b64:");
    }

    @Test
    void theBackButtonReopensTheGrid() {
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT); // item editor (54 slots)

        fireClick(EDITOR_BACK_SLOT, ClickType.LEFT);

        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(18); // back to the grid
    }

    @Test
    void anEditMadeInTheItemEditorIsSavedAndReloaded() {
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT); // open the item editor
        fireClick(GLOW_SLOT, ClickType.LEFT); // turn on the glow in the working copy
        fireClick(EDITOR_BACK_SLOT, ClickType.LEFT); // back to the grid

        fireClick(GRID_SAVE_SLOT, ClickType.LEFT); // save the grid

        MenuItemSpec saved = Objects.requireNonNull(
                menus.registeredSpec("alpha").orElseThrow().items().get("x"));
        assertThat(saved.decor().glow()).isTrue();
    }

    // --- P4: click actions + requirements ---

    @Test
    void theClickActionsAndRequirementsRowsAreOpenerProperties() {
        grid.open(player, viewer, "alpha");
        MenuEditSession session = session();

        assertThat(itemEditor.propertyAt(CLICK_ACTIONS_SLOT, session, "x"))
                .get()
                .isInstanceOf(MenuOpenerProperty.class);
        assertThat(itemEditor.propertyAt(REQUIREMENTS_SLOT, session, "x")).get().isInstanceOf(MenuOpenerProperty.class);
    }

    @Test
    void clickingTheClickActionsRowOpensTheGestureList() {
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT); // open the item editor

        fireClick(CLICK_ACTIONS_SLOT, ClickType.LEFT);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getSize()).isEqualTo(27); // the three-row gesture list
        assertThat(inv.getItem(GESTURE_LEFT_SLOT).getType()).isEqualTo(Material.LEVER); // the LEFT gesture button
    }

    @Test
    void selectingAGestureOpensItsActionRefList() {
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT);
        fireClick(CLICK_ACTIONS_SLOT, ClickType.LEFT); // gesture list

        fireClick(GESTURE_LEFT_SLOT, ClickType.LEFT); // open LEFT's action list

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(54); // the six-row ref list
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.PAPER); // the existing "close" action entry
        assertThat(inv.getItem(REF_ADD_SLOT).getType()).isEqualTo(Material.LIME_DYE); // add button
    }

    @Test
    void theAddButtonOpensTheActionIdPicker() {
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT);
        fireClick(CLICK_ACTIONS_SLOT, ClickType.LEFT);
        fireClick(GESTURE_LEFT_SLOT, ClickType.LEFT); // action list

        fireClick(REF_ADD_SLOT, ClickType.LEFT); // open the id picker

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(54);
        // The picker offers one paper button per registered action id (close / message / sound in this fixture).
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.PAPER);
    }

    @Test
    void addingARefThroughThePickerAndArgAppendsItToTheGesture() {
        grid.open(player, viewer, "alpha");
        MenuEditSession session = session();
        MenuRefListEditor.RefList list = new MenuRefListEditor.RefList(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_ACTIONS_TITLE,
                Map.of("gesture", "LEFT"),
                List.of("close", "message", "sound"),
                () -> session.clickActions("x", ClickKind.LEFT),
                refs -> session.setClickActions("x", ClickKind.LEFT, refs),
                () -> {});

        refListEditor.applyRef(context(), list, "sound", "PLING", -1); // picked "sound", typed the arg

        List<Ref> left = session.clickActions("x", ClickKind.LEFT);
        assertThat(left).hasSize(2);
        assertThat(left.get(1).id()).isEqualTo("sound");
        assertThat(left.get(1).value()).isEqualTo("PLING");
    }

    @Test
    void clickingTheRequirementsRowOpensTheRequirementEditor() {
        grid.open(player, viewer, "alpha");
        fireClick(0, ClickType.LEFT);

        fireClick(REQUIREMENTS_SLOT, ClickType.LEFT);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getSize()).isEqualTo(27); // the three-row requirement editor
        assertThat(inv.getItem(REQ_CONDITIONS_SLOT).getType()).isEqualTo(Material.COMPASS); // conditions row
    }

    @Test
    void addingAViewRequirementThroughThePickerAppendsIt() {
        grid.open(player, viewer, "alpha");
        MenuEditSession session = session();
        MenuRefListEditor.RefList list = new MenuRefListEditor.RefList(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_CONDITIONS_TITLE,
                Map.of(),
                List.of("perm"),
                () -> session.viewConditions("x"),
                refs -> session.setViewConditions("x", refs),
                () -> {});

        refListEditor.applyRef(context(), list, "perm", "shop.use", -1);

        List<Ref> conditions = session.viewConditions("x");
        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0).id()).isEqualTo("perm");
    }

    @Test
    void afterSaveTheReloadedMenuHasTheClickActionAndViewRequirement() {
        grid.open(player, viewer, "alpha");
        MenuEditSession session = session();
        session.addAction("x", ClickKind.LEFT, Ref.parse("sound:PLING"));
        session.addRequirement("x", Ref.parse("perm:shop.use"));
        session.setViewMinimum("x", 1);

        fireClick(GRID_SAVE_SLOT, ClickType.LEFT); // save the grid

        MenuItemSpec saved = Objects.requireNonNull(
                menus.registeredSpec("alpha").orElseThrow().items().get("x"));
        assertThat(saved.click().actions().get(ClickKind.LEFT)).hasSize(2);
        assertThat(saved.view().requirements()).hasSize(1);
        assertThat(saved.view().minimum()).isEqualTo(1);
    }

    /** A hand-built editor click context for driving a ref-list apply seam directly (no live anvil). */
    private ClickContext context() {
        return new ClickContext(player, viewer, false, false, () -> {}, menus.selectorOpener(), menus.confirmOpener());
    }

    // --- helpers ---

    private MenuEditSession session() {
        return grid.editSession(viewer.uuid()).orElseThrow();
    }

    private CustomMenuLoader.SingleLoad reloadOne(String name) {
        CustomMenuLoader.SingleLoad result = loader.loadSingle(menusDir, name);
        if (result.found() && result.loaded() > 0 && !names.contains(name)) {
            names.add(name);
        }
        return result;
    }

    private void forget(String name) {
        menus.unregisterSpec(name);
        names.remove(name);
    }

    private void writeMenu(String name, int rows, String item) {
        String hocon = "rows = " + rows + "\nitems { " + item + " }\n";
        try {
            Files.writeString(menusDir.resolve(name + ".conf"), hocon);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to seed menu " + name, failure);
        }
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView openView = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                openView, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
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
