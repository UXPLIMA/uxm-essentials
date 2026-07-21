package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Pins the AxTrade-style slot map: the editable and mirror item grids, the control row (confirm, money, experience for
 * the viewer; the partner's experience, money, and status read-only), and the centre glass divider. Editable slot
 * {@code k} and mirror slot {@code k} must line up on the same row so a placed stack shows opposite the counterpart.
 */
class TradeLayoutTest {

    private static final PlayerRef VIEWER = new PlayerRef(UUID.randomUUID(), "Viewer");

    private static final List<Integer> EDITABLE =
            List.of(9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39, 45, 46, 47, 48);
    private static final List<Integer> MIRROR =
            List.of(14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35, 41, 42, 43, 44, 50, 51, 52, 53);
    private static final List<Integer> DIVIDER = List.of(1, 4, 7, 13, 22, 31, 40, 49);

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void editableAndMirrorGridsMatchTheAxTradeSlots() {
        TradeLayout layout = new TradeLayout(20, List.of("coins"));
        for (int k = 0; k < EDITABLE.size(); k++) {
            assertThat(layout.editableSlot(k)).isEqualTo(EDITABLE.get(k));
            assertThat(layout.mirrorSlot(k)).isEqualTo(MIRROR.get(k));
            assertThat(layout.isEditable(EDITABLE.get(k))).isTrue();
            assertThat(layout.isEditable(MIRROR.get(k))).isFalse();
        }
    }

    @Test
    void everyEditableSlotSharesItsRowWithItsMirror() {
        TradeLayout layout = new TradeLayout(20, List.of("coins"));
        for (int k = 0; k < EDITABLE.size(); k++) {
            assertThat(layout.editableSlot(k) / 9)
                    .as("editable slot %d and its mirror line up on the same row", k)
                    .isEqualTo(layout.mirrorSlot(k) / 9);
        }
    }

    @Test
    void seedFramePaintsTheDividerAndLeavesTheGridsEmpty() {
        TradeLayout layout = new TradeLayout(20, List.of("coins"));
        Inventory inv = Bukkit.createInventory(null, TradeLayout.SIZE);

        layout.seedFrame(inv);

        for (int slot : DIVIDER) {
            assertThat(material(inv, slot))
                    .as("divider slot %d is glass", slot)
                    .isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        }
        for (int slot : EDITABLE) {
            assertThat(inv.getItem(slot))
                    .as("editable slot %d starts empty", slot)
                    .isNull();
        }
        for (int slot : MIRROR) {
            assertThat(inv.getItem(slot))
                    .as("mirror slot %d starts empty", slot)
                    .isNull();
        }
    }

    @Test
    void controlRowCarriesConfirmMoneyAndExperienceForBothSides() {
        TradeLayout layout = new TradeLayout(20, List.of("coins"));
        Inventory inv = Bukkit.createInventory(null, TradeLayout.SIZE);
        Messages messages = new KeyMessages();

        layout.seedFrame(inv);
        layout.renderControls(inv, messages, VIEWER, false, false);
        layout.renderMoney(inv, messages, VIEWER, 0, Map.of(), Map.of());
        layout.renderExperience(inv, messages, VIEWER, 0L, 0L);

        assertThat(material(inv, 0)).isEqualTo(Material.YELLOW_WOOL); // viewer confirm
        assertThat(material(inv, 2)).isEqualTo(Material.GOLD_INGOT); // viewer money
        assertThat(material(inv, 3)).isEqualTo(Material.EXPERIENCE_BOTTLE); // viewer experience
        assertThat(material(inv, 5)).isEqualTo(Material.EXPERIENCE_BOTTLE); // partner experience
        assertThat(material(inv, 6)).isEqualTo(Material.SUNFLOWER); // partner money
        assertThat(material(inv, 8)).isEqualTo(Material.RED_STAINED_GLASS_PANE); // partner status
        assertThat(layout.isMoneySlot(2)).isTrue();
        assertThat(layout.isExperienceSlot(3)).isTrue();
        assertThat(layout.confirmSlot()).isZero();
    }

    @Test
    void moneyAndExperienceSlotsFallBackToDividerWhenDisabled() {
        // The items-only cross-server window: money off (no currencies) and experience off.
        TradeLayout layout = new TradeLayout(20, List.of(), false);
        Inventory inv = Bukkit.createInventory(null, TradeLayout.SIZE);

        layout.seedFrame(inv);

        assertThat(layout.moneyEnabled()).isFalse();
        assertThat(layout.isMoneySlot(2)).isFalse();
        assertThat(layout.isExperienceSlot(3)).isFalse();
        for (int slot : List.of(2, 3, 5, 6)) {
            assertThat(material(inv, slot))
                    .as("disabled control slot %d is divider glass", slot)
                    .isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        }
    }

    @Test
    void theSingleMoneyButtonCyclesThroughEveryAllowedCurrency() {
        TradeLayout layout = new TradeLayout(20, List.of("coins", "gems"));

        assertThat(layout.currencyCount()).isEqualTo(2);
        assertThat(layout.currencyAt(0)).isEqualTo("coins");
        assertThat(layout.currencyAt(1)).isEqualTo("gems");
        assertThat(layout.currencyAt(2)).isEqualTo("coins"); // wraps
    }

    private static Material material(Inventory inv, int slot) {
        ItemStack stack = inv.getItem(slot);
        return stack == null ? Material.AIR : stack.getType();
    }

    /** Resolves any key to its plain key string; the layout renders it as literal text. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
