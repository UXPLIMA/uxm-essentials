package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.ItemActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the item action pack. {@code give-item} / {@code take-item} / {@code set-item} run against a
 * live {@link PlayerMock} inventory, so their effects are asserted concretely against its contents; the give-overflow
 * path is proved by filling the inventory and finding the surplus as a dropped {@link Item} in the world. The pure
 * {@code <material> [amount]} and {@code <slot> <material> [amount]} grammar records are exercised by plain-JUnit
 * assertions, which is where the malformed-input branches are pinned.
 */
class ItemActionsTest {

    private ServerMock server;
    private PlayerMock viewer;
    private MenuBindings bindings;
    private RecordingLogger log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");
        bindings = new MenuBindings();
        log = new RecordingLogger();
        ItemActions.register(bindings, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- give ----------------------------------------------------------------------------------------------------

    @Test
    void giveItemPutsTheMaterialAndAmountInTheInventory() {
        invoke("give-item", "DIAMOND 5");

        assertThat(viewer.getInventory().contains(Material.DIAMOND, 5)).isTrue();
    }

    @Test
    void giveAliasIsRegistered() {
        invoke("give", "DIAMOND 2");

        assertThat(viewer.getInventory().contains(Material.DIAMOND, 2)).isTrue();
    }

    @Test
    void giveItemDefaultsTheAmountToOne() {
        invoke("give-item", "DIAMOND");

        assertThat(viewer.getInventory().contains(Material.DIAMOND, 1)).isTrue();
    }

    @Test
    void giveItemDropsTheOverflowWhenTheInventoryIsFull() {
        fillInventoryWith(new ItemStack(Material.STONE, 64));

        assertThatCode(() -> invoke("give-item", "DIAMOND 5")).doesNotThrowAnyException();

        // The inventory has no room, so the diamonds land in the world as a dropped item rather than being lost. The
        // drop is read off the server's registered entities, which is robust to how MockBukkit indexes items by world.
        List<ItemStack> dropped = server.getEntities().stream()
                .filter(Item.class::isInstance)
                .map(entity -> ((Item) entity).getItemStack())
                .toList();
        assertThat(dropped).anyMatch(stack -> stack.getType() == Material.DIAMOND && stack.getAmount() == 5);
        assertThat(viewer.getInventory().contains(Material.DIAMOND)).isFalse();
    }

    @Test
    void giveItemAcceptsASerializedStack() {
        ItemStack golden = new ItemStack(Material.GOLDEN_APPLE, 3);

        invoke("give-item", SerializedItems.encode(golden));

        assertThat(viewer.getInventory().contains(Material.GOLDEN_APPLE, 3)).isTrue();
    }

    @Test
    void giveItemWithAnUnknownMaterialIsAFailSoftNoOp() {
        assertThatCode(() -> invoke("give-item", "NOT_A_REAL_ITEM")).doesNotThrowAnyException();

        assertThat(viewer.getInventory().isEmpty()).isTrue();
        assertThat(log.warnings).anyMatch(line -> line.contains("unresolved_item"));
    }

    // --- take ----------------------------------------------------------------------------------------------------

    @Test
    void takeItemRemovesUpToTheAmount() {
        viewer.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

        invoke("take-item", "DIAMOND 3");

        assertThat(viewer.getInventory().contains(Material.DIAMOND, 2)).isTrue();
        assertThat(viewer.getInventory().contains(Material.DIAMOND, 3)).isFalse();
    }

    @Test
    void takeItemWithAnUnknownMaterialIsAFailSoftNoOp() {
        viewer.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

        assertThatCode(() -> invoke("take-item", "NOT_A_REAL_ITEM")).doesNotThrowAnyException();

        assertThat(viewer.getInventory().contains(Material.DIAMOND, 5)).isTrue();
    }

    // --- set -----------------------------------------------------------------------------------------------------

    @Test
    void setItemOverwritesTheSlot() {
        invoke("set-item", "0 DIAMOND 1");

        ItemStack slotZero = viewer.getInventory().getItem(0);
        assertThat(slotZero).isNotNull();
        assertThat(slotZero.getType()).isEqualTo(Material.DIAMOND);
        assertThat(slotZero.getAmount()).isEqualTo(1);
    }

    @Test
    void setItemWithAnOutOfRangeSlotIsAFailSoftNoOp() {
        assertThatCode(() -> invoke("set-item", "999 DIAMOND")).doesNotThrowAnyException();

        assertThat(viewer.getInventory().contains(Material.DIAMOND)).isFalse();
        assertThat(log.warnings).anyMatch(line -> line.contains("slot_out_of_range"));
    }

    @Test
    void setItemWithAnUnknownMaterialIsAFailSoftNoOp() {
        assertThatCode(() -> invoke("set-item", "0 NOT_A_REAL_ITEM")).doesNotThrowAnyException();

        assertThat(viewer.getInventory().getItem(0)).isNull();
        assertThat(log.warnings).anyMatch(line -> line.contains("unresolved_item"));
    }

    // --- pure grammar --------------------------------------------------------------------------------------------

    @Test
    void itemArgParsesMaterialAndAmount() {
        ItemActions.ItemArg arg = ItemActions.ItemArg.parse("DIAMOND 5").orElseThrow();

        assertThat(arg.material()).isEqualTo("DIAMOND");
        assertThat(arg.amount()).isEqualTo(5);
    }

    @Test
    void itemArgDefaultsTheAmountToOne() {
        ItemActions.ItemArg arg = ItemActions.ItemArg.parse("DIAMOND").orElseThrow();

        assertThat(arg.amount()).isEqualTo(1);
    }

    @Test
    void itemArgRejectsABlankOrNonNumericAmount() {
        assertThat(ItemActions.ItemArg.parse("   ")).isEmpty();
        assertThat(ItemActions.ItemArg.parse("DIAMOND lots")).isEmpty();
        assertThat(ItemActions.ItemArg.parse("DIAMOND 0")).isEmpty();
    }

    @Test
    void slotItemParsesSlotAndItemToken() {
        ItemActions.SlotItem slotItem =
                ItemActions.SlotItem.parse("3 DIAMOND 2").orElseThrow();

        assertThat(slotItem.slot()).isEqualTo(3);
        assertThat(slotItem.item()).isEqualTo("DIAMOND 2");
    }

    @Test
    void slotItemRejectsAMissingItemOrNonNumericSlot() {
        assertThat(ItemActions.SlotItem.parse("3")).isEmpty();
        assertThat(ItemActions.SlotItem.parse("notaslot DIAMOND")).isEmpty();
    }

    // --- harness -------------------------------------------------------------------------------------------------

    private void fillInventoryWith(ItemStack filler) {
        for (int slot = 0; slot < viewer.getInventory().getSize(); slot++) {
            viewer.getInventory().setItem(slot, filler.clone());
        }
    }

    /** Builds the context the click listener would build, then fires the handler {@code id}. */
    private void invoke(String id, String arg) {
        Consumer<MenuActionContext> handler =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        PlayerRef ref = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), viewer, ClickKind.LEFT, Map.of("value", arg));
        handler.accept(ctx);
    }

    /** Captures expanded info/warn lines so a test can assert what the console operator logger recorded. */
    private static final class RecordingLogger implements Logger {
        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            infos.add(expand(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(expand(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String expand(String message, Object... args) {
            String expanded = message;
            for (Object arg : args) {
                expanded = expanded.replaceFirst("\\{}", String.valueOf(arg));
            }
            return expanded;
        }
    }
}
