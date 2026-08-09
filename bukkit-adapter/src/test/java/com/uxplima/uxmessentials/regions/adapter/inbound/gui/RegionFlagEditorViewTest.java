package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagKind;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link RegionFlagEditorView} over a real menu engine and a fake {@link RegionService}
 * (WorldGuard is not on the test classpath, so the editor is exercised through the port). It draws one icon per
 * registered flag with a type-appropriate icon, and a click opens the control the flag's {@link FlagKind} wants: a
 * state cycles, a boolean toggles, a choice flag opens a picker, a text/number flag opens a prompt, and an
 * unsupported flag is read-only. Every control writes the chosen value back through {@link RegionService#setFlag}.
 */
class RegionFlagEditorViewTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final RegionRef REGION = new RegionRef(WORLD, "spawn");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock staff;
    private PlayerRef staffRef;
    private FakeRegionService service;
    private FakePrompt prompt;
    private List<String> manageMembersFor;
    private RegionFlagEditorView editor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        staff = server.addPlayer("Staff");
        staffRef = new PlayerRef(staff.getUniqueId(), staff.getName());
        service = new FakeRegionService();
        prompt = new FakePrompt();
        manageMembersFor = new ArrayList<>();

        Scheduler scheduler = new SyncScheduler();
        Messages messages = keyEcho();
        GuiText guiText = new GuiText(messages);
        TestMenuEngine engine = TestMenuEngine.create(messages, scheduler);
        engine.installListener(plugin);
        Menus menus = engine.menus();
        editor = new RegionFlagEditorView(
                menus,
                guiText,
                scheduler,
                messages,
                (viewer, renderedText) -> {},
                service,
                prompt,
                List.of(), // no allow-list: every registered flag shows
                // the members button sits where the shipped regions/gui/region-flags.conf puts it
                EntityListLayout.paginatedDefault(Material.GRAY_DYE).withAction(53, Material.PLAYER_HEAD),
                (clicker, region) -> manageMembersFor.add(region.id()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void drawsATypeAppropriateIconPerRegisteredFlag() {
        service.setDescriptors(oneOfEachKind());

        editor.open(staffRef, REGION);

        Inventory inv = staff.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.RED_DYE); // state=DENY
        assertThat(inv.getItem(1).getType()).isEqualTo(Material.LIME_DYE); // boolean=true
        assertThat(inv.getItem(2).getType()).isEqualTo(Material.NAME_TAG); // string
        assertThat(inv.getItem(3).getType()).isEqualTo(Material.REPEATER); // integer
        assertThat(inv.getItem(4).getType()).isEqualTo(Material.COMPARATOR); // double (unset)
        assertThat(inv.getItem(5).getType()).isEqualTo(Material.BOOK); // enum
        assertThat(inv.getItem(6).getType()).isEqualTo(Material.BARRIER); // other (read-only)
    }

    @Test
    void aStateFlagCyclesAndWritesTheNextStateThroughThePort() {
        service.setDescriptors(oneOfEachKind());
        editor.open(staffRef, REGION);

        // pvp is DENY, so a click cycles DENY -> UNSET and writes the empty (cleared) value.
        fireClick(0);

        assertThat(service.lastSetFlag()).isEqualTo(new FlagValue("pvp", ""));
        assertThat(reopenedTop().getItem(0).getType()).isEqualTo(Material.GRAY_DYE); // now unset
    }

    @Test
    void aBooleanFlagTogglesAndWritesThroughThePort() {
        service.setDescriptors(oneOfEachKind());
        editor.open(staffRef, REGION);

        // notify-enter is true, so a click toggles it to false.
        fireClick(1);

        assertThat(service.lastSetFlag()).isEqualTo(new FlagValue("notify-enter", "false"));
    }

    @Test
    void aStringFlagPromptsThenWritesTheTypedLine() {
        service.setDescriptors(oneOfEachKind());
        editor.open(staffRef, REGION);

        fireClick(2); // greeting -> opens a text prompt
        assertThat(prompt.captured()).isTrue();

        prompt.submit("welcome home");

        assertThat(service.lastSetFlag()).isEqualTo(new FlagValue("greeting", "welcome home"));
    }

    @Test
    void anIntegerFlagAcceptsAValidNumberAndRejectsANonNumber() {
        service.setDescriptors(oneOfEachKind());
        editor.open(staffRef, REGION);

        fireClick(3); // heal-amount
        prompt.submit("9");
        assertThat(service.lastSetFlag()).isEqualTo(new FlagValue("heal-amount", "9"));

        service.reset();
        fireClick(3);
        prompt.submit("not-a-number");
        assertThat(service.lastSetFlag()).isNull(); // rejected, nothing written
    }

    @Test
    void cancellingATextPromptWritesNothingAndReopensThePanel() {
        service.setDescriptors(oneOfEachKind());
        editor.open(staffRef, REGION);

        fireClick(2); // greeting -> opens a text prompt
        prompt.cancel();

        assertThat(service.lastSetFlag()).isNull();
        assertThat(staff.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void aChoiceFlagOpensAPickerAndWritesTheChosenValue() {
        service.setDescriptors(oneOfEachKind());
        editor.open(staffRef, REGION);

        fireClick(5); // game-mode -> opens the choice picker
        Inventory picker = staff.getOpenInventory().getTopInventory();
        assertThat(picker.getHolder()).isInstanceOf(MenuHolder.class);

        // Slot 0 is the unset option; the choices follow, so slot 2 is "creative".
        fireClick(2);

        assertThat(service.lastSetFlag()).isEqualTo(new FlagValue("game-mode", "creative"));
    }

    @Test
    void aChoicePickerUnsetOptionClearsTheFlag() {
        service.setDescriptors(
                List.of(new FlagDescriptor("game-mode", FlagKind.ENUM, "creative", List.of("survival", "creative"))));
        editor.open(staffRef, REGION);

        fireClick(0); // the sole enum flag -> opens the picker
        fireClick(0); // the unset option

        assertThat(service.lastSetFlag()).isEqualTo(new FlagValue("game-mode", ""));
    }

    @Test
    void anUnsupportedFlagIsReadOnlyAndWritesNothing() {
        service.setDescriptors(oneOfEachKind());
        editor.open(staffRef, REGION);

        fireClick(6); // the OTHER flag

        assertThat(service.lastSetFlag()).isNull();
    }

    @Test
    void theMembersButtonHandsTheRegionToTheManageMembersCallback() {
        service.setDescriptors(oneOfEachKind());
        editor.open(staffRef, REGION);

        fireClick(53);

        assertThat(manageMembersFor).containsExactly("spawn");
    }

    @Test
    void paginatesALargeFlagSet() {
        List<FlagDescriptor> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(FlagDescriptor.of(String.format("flag-%03d", i), FlagKind.STATE, ""));
        }
        service.setDescriptors(many);

        editor.open(staffRef, REGION);
        Inventory inv = staff.getOpenInventory().getTopInventory();

        // A full first content page (slots 0..44) plus a next-page arrow at slot 50 proves the set paginated.
        assertThat(inv.getItem(44)).isNotNull();
        assertThat(inv.getItem(50).getType()).isEqualTo(Material.ARROW);

        // The next button flips the page in place without tearing the menu down.
        fireClick(50);
        assertThat(staff.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(staff.getOpenInventory().getTopInventory().getItem(0)).isNotNull();
    }

    /** One flag of each kind, in a fixed order so a test can address a kind by its slot index. */
    private static List<FlagDescriptor> oneOfEachKind() {
        return List.of(
                FlagDescriptor.of("pvp", FlagKind.STATE, "DENY"),
                FlagDescriptor.of("notify-enter", FlagKind.BOOLEAN, "true"),
                FlagDescriptor.of("greeting", FlagKind.STRING, "hi"),
                FlagDescriptor.of("heal-amount", FlagKind.INTEGER, "5"),
                FlagDescriptor.of("heal-min-health", FlagKind.DOUBLE, ""),
                new FlagDescriptor("game-mode", FlagKind.ENUM, "", List.of("survival", "creative", "adventure")),
                FlagDescriptor.of("teleport", FlagKind.OTHER, "somewhere"));
    }

    private Inventory reopenedTop() {
        return staff.getOpenInventory().getTopInventory();
    }

    private void fireClick(int slot) {
        InventoryView view = staff.getOpenInventory();
        server.getPluginManager()
                .callEvent(new org.bukkit.event.inventory.InventoryClickEvent(
                        view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private static Messages keyEcho() {
        return (viewer, key, placeholders) -> key.key();
    }

    /** A {@link FlagValuePrompt} that captures the request and lets a test drive submit/cancel synchronously. */
    private static final class FakePrompt implements FlagValuePrompt {
        private @Nullable Consumer<String> onSubmit;
        private @Nullable Runnable onCancel;

        boolean captured() {
            return onSubmit != null;
        }

        void submit(String line) {
            Consumer<String> submit = onSubmit;
            onSubmit = null;
            onCancel = null;
            if (submit != null) {
                submit.accept(line);
            }
        }

        void cancel() {
            Runnable cancel = onCancel;
            onSubmit = null;
            onCancel = null;
            if (cancel != null) {
                cancel.run();
            }
        }

        @Override
        public void prompt(
                org.bukkit.entity.Player player,
                PlayerRef viewer,
                InputRequest request,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            this.onSubmit = onSubmit;
            this.onCancel = onCancel;
        }
    }

    /** An in-memory {@link RegionService} serving flag descriptors and recording the last {@code setFlag}. */
    private static final class FakeRegionService implements RegionService {
        private final Map<String, FlagDescriptor> descriptors = new LinkedHashMap<>();
        private @Nullable FlagValue lastSetFlag;

        void setDescriptors(List<FlagDescriptor> values) {
            descriptors.clear();
            for (FlagDescriptor value : values) {
                descriptors.put(value.name(), value);
            }
        }

        void reset() {
            lastSetFlag = null;
        }

        @Nullable FlagValue lastSetFlag() {
            return lastSetFlag;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public List<RegionRef> regionsIn(WorldRef world) {
            return List.of(REGION);
        }

        @Override
        public Optional<RegionRef> region(WorldRef world, String id) {
            return Optional.of(REGION);
        }

        @Override
        public List<FlagValue> flags(RegionRef region) {
            return List.of();
        }

        @Override
        public List<FlagDescriptor> flagDescriptors(RegionRef region) {
            return new ArrayList<>(descriptors.values());
        }

        @Override
        public List<String> members(RegionRef region) {
            return List.of();
        }

        @Override
        public List<String> owners(RegionRef region) {
            return List.of();
        }

        @Override
        public int priority(RegionRef region) {
            return 0;
        }

        @Override
        public RegionRef create(WorldRef world, String id, Position min, Position max) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFlag(RegionRef region, FlagValue flag) {
            this.lastSetFlag = flag;
            FlagDescriptor existing = descriptors.get(flag.name());
            if (existing != null) {
                descriptors.put(
                        flag.name(),
                        new FlagDescriptor(flag.name(), existing.kind(), flag.value(), existing.choices()));
            }
        }

        @Override
        public void applyMemberChange(RegionMemberChange change) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPriority(RegionRef region, int priority) {
            throw new UnsupportedOperationException();
        }
    }

    /** Runs every scheduler hop inline. */
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
