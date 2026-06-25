package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.CurrencyPickerView;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
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
 * The currency-picker golden test: the engine-rendered picker must draw the exact grid the original
 * {@code CurrencyPickerView} drew on uxmLib's {@code PaginatedGui}. The fixture is two currencies (the
 * {@code SUNFLOWER}-iconed default "coins" and a {@code GOLD_INGOT}-iconed "rubies"), with "rubies" active, over the
 * picker's six-row layout (content slots 0..44, prev at 45, next at 53, gray-glass filler). Page 0 places one icon
 * per currency at content slots 0 and 1, with the two ARROW nav buttons at 45 and 53. The engine window is
 * snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the analytic baseline
 * the old view produced for this fixture; the active currency's icon must carry the {@code UNBREAKING} glint with
 * {@code HIDE_ENCHANTS} while the inactive one carries neither. Then a left click on the inactive currency through the
 * engine's own {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener} proves the
 * migrated path hands that currency to the recording {@code onPick} the old view drove — faithful in both
 * appearance and behaviour.
 */
class CurrencyPickerGoldenTest {

    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private static final Currency RUBIES = Currency.builder(CurrencyId.of("rubies"))
            .symbol("R")
            .plural("rubies")
            .precision(0)
            .iconMaterial("GOLD_INGOT")
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private CurrencyPickerView picker;
    private final List<Currency> picked = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        picker = new CurrencyPickerView(engine.menus(), guiText, scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameCurrencyGridNavAndFillerAsTheOldView() {
        openPicker();

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void theActiveCurrencyIsGlintedAndTheInactiveOneIsNot() {
        openPicker();
        Inventory inv = player.getOpenInventory().getTopInventory();

        ItemStack coinsIcon = Objects.requireNonNull(inv.getItem(0), "coins icon"); // content slot 0 holds coins
        ItemStack rubiesIcon = Objects.requireNonNull(inv.getItem(1), "rubies icon"); // content slot 1 holds rubies

        // "rubies" is the active currency, so its icon carries the glint enchant and hides it; "coins" carries neither.
        assertThat(rubiesIcon.getEnchantments()).containsKey(Enchantment.UNBREAKING);
        assertThat(Objects.requireNonNull(rubiesIcon.getItemMeta()).getItemFlags())
                .contains(ItemFlag.HIDE_ENCHANTS);
        assertThat(coinsIcon.getEnchantments()).isEmpty();
        assertThat(Objects.requireNonNull(coinsIcon.getItemMeta()).getItemFlags())
                .doesNotContain(ItemFlag.HIDE_ENCHANTS);
    }

    @Test
    void clickingAnInactiveCurrencyThroughTheEngineRunsOnPickForThatCurrency() {
        openPicker();

        fireClick(0); // content slot 0 holds the inactive "coins"

        assertThat(picked).containsExactly(COINS);
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        openPicker();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    private void openPicker() {
        picker.open(player, viewer, List.of(COINS, RUBIES), RUBIES, picked::add);
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code CurrencyPickerView} produced for this fixture: a
     * SUNFLOWER for the default "coins" at content slot 0 and a GOLD_INGOT for the active "rubies" at slot 1, each
     * named through the {@code eco.admin-gui.currency-name} key the test's {@code KeyMessages} returns verbatim, and
     * the two ARROW nav buttons at 45 and 53 with their previous/next catalog names. The gray-glass filler slots are
     * dropped from the snapshot, so a wrong material, name, or misplaced icon still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.SUNFLOWER, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_NAME.key()));
        baseline.put(1, new Snapshot(Material.GOLD_INGOT, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_NAME.key()));
        baseline.put(
                PREV_SLOT,
                new Snapshot(Material.ARROW, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_PICKER_PREVIOUS.key()));
        baseline.put(
                NEXT_SLOT, new Snapshot(Material.ARROW, EconomyMessageKey.ECO_ADMIN_GUI_CURRENCY_PICKER_NEXT.key()));
        return baseline;
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        Component name = Objects.requireNonNull(item.getItemMeta()).displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

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
}
