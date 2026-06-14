package com.uxplima.uxmessentials.vaults.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorView;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultCharge;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.StorageGui;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /vault} selector menu. A player owning vaults 1 and 2 under an amount cap of
 * 4 opens a paginated picker with four icons: two owned (clickable) and two locked. Clicking an owned icon opens
 * that vault as a {@link StorageGui} through the same {@link OpenVault} path the command drives; clicking a
 * locked icon opens nothing and only sends the {@code VAULT_SELECTOR_LOCKED_CLICK} nudge. A one-owned player
 * still opens the menu without throwing.
 *
 * <p>The selector loads owned indices off-thread and builds on the entity thread; both run inline through a
 * synchronous scheduler double. uxmLib's menu listener is installed against a mock plugin, and a click is
 * dispatched as a real {@link InventoryClickEvent} through the plugin manager — the production click path.
 */
class VaultSelectorTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private RecordingSink sink;
    private FakeRepository repository;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        sink = new RecordingSink();
        repository = new FakeRepository();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void theSelectorOpensForAMultiVaultOwner() {
        repository.allocate(1);
        repository.allocate(2);
        selector(4).open(player, ref());

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);
    }

    @Test
    void clickingAnOwnedIconOpensThatVault() {
        repository.allocate(1);
        repository.allocate(2);
        selector(4).open(player, ref());

        fireClick(1); // content slot 1 -> the second owned icon, vault 2

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(StorageGui.class);
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_OPENED);
    }

    @Test
    void clickingALockedIconOpensNothingAndNudges() {
        repository.allocate(1);
        repository.allocate(2);
        selector(4).open(player, ref());

        fireClick(2); // content slot 2 -> the first locked index (vault 3)

        // Still on the selector — no storage vault opened — and only the locked nudge was sent.
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(sink.keys).contains(VaultsMessageKey.VAULT_SELECTOR_LOCKED_CLICK);
        assertThat(sink.keys).doesNotContain(VaultsMessageKey.VAULT_OPENED);
    }

    @Test
    void aSingleOwnedPlayerStillOpensTheMenuWithoutThrowing() {
        repository.allocate(1);
        assertThatCode(() -> selector(4).open(player, ref())).doesNotThrowAnyException();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(PaginatedGui.class);
    }

    /** Left-click the given content slot of the open menu through the installed listener. */
    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** A selector whose amount quota resolves to {@code cap}, wired off the real open/list use cases. */
    private VaultSelectorView selector(int cap) {
        Messages messages = new KeyMessages();
        Permissions permissions = new CapPermissions(cap);
        Scheduler scheduler = new SyncScheduler();
        VaultAmountQuota amount = new VaultAmountQuota(permissions, cap);
        VaultSizeQuota size = new VaultSizeQuota(permissions, 6);
        VaultNotifier notifier = new VaultNotifier(messages, sink);
        VaultChargeSettings chargeSettings = VaultChargeSettings.allFree();
        VaultCharge charge = new VaultCharge(permissions, Optional.<VaultEconomy>empty(), chargeSettings);
        SaveVault saveVault = new SaveVault(repository, new NoEvents(), Clock.systemUTC());
        VaultView view =
                new VaultView(messages, sink, saveVault, scheduler, permissions, VaultItemPolicy.allowAll(), null);
        OpenVault openVault = new OpenVault(repository, amount, size, charge, Clock.systemUTC());
        ListVaults listVaults = new ListVaults(repository);
        return new VaultSelectorView(
                messages,
                sink,
                scheduler,
                listVaults,
                amount,
                openVault,
                view,
                notifier,
                chargeSettings,
                VaultViews.selectorSettings());
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** An in-memory vault store the test seeds with owned indices. */
    private final class FakeRepository implements VaultRepository {
        private final Map<Integer, Vault> vaults = new HashMap<>();

        void allocate(int index) {
            vaults.put(index, Vault.allocate(VaultId.of(ref(), index), VaultSize.ofClamped(6), Instant.now()));
        }

        @Override
        public Optional<Vault> find(VaultId id) {
            return Optional.ofNullable(vaults.get(id.index()));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            List<Integer> indices = new ArrayList<>(vaults.keySet());
            indices.sort(Integer::compareTo);
            return indices;
        }

        @Override
        public int count(PlayerRef owner) {
            return vaults.size();
        }

        @Override
        public void save(Vault vault) {
            vaults.put(vault.id().index(), vault);
        }

        @Override
        public void delete(VaultId id) {
            vaults.remove(id.index());
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            int before = vaults.size();
            vaults.values().removeIf(vault -> vault.lastTouched().isBefore(cutoff));
            return before - vaults.size();
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            for (VaultsMessageKey key : VaultsMessageKey.values()) {
                if (key.key().equals(renderedText)) {
                    keys.add(key);
                }
            }
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

    /** Resolves the amount quota to a fixed cap; grants no other node. */
    private static final class CapPermissions implements Permissions {
        private final int cap;

        private CapPermissions(int cap) {
            this.cap = cap;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            if (family.equals(VaultAmountQuota.FAMILY)) {
                return QuotaResult.limited(cap);
            }
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoEvents
            implements com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher {
        @Override
        public void publish(com.uxplima.uxmessentials.shared.domain.DomainEvent event) {}
    }
}
