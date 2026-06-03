package com.uxplima.uxmessentials.kits.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitNotifier;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.PaginatedGui;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Proves the {@code /kits} menu honours its injected {@link GuiLayout}: a layout with three rows opens a
 * 27-slot menu (so the conf's row count drives the geometry), the unchanged default still opens 54 slots, and
 * the title resolved from {@link KitsMessageKey#KIT_MENU_TITLE} is preserved regardless of the layout, so the
 * externalised layout never disturbs the catalog-driven title.
 */
class KitMenuLayoutTest {

    private static final String TITLE = KitsMessageKey.KIT_MENU_TITLE.key();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        com.uxplima.uxmlib.gui.Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        com.uxplima.uxmlib.gui.Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void aThreeRowLayoutOpensATwentySevenSlotMenu() {
        open(layout(3));

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(PaginatedGui.class);
        assertThat(menu.getSize()).isEqualTo(27);
    }

    @Test
    void theDefaultLayoutStillOpensFiftyFourSlots() {
        open(GuiLayout.paginatedDefault(Material.CHEST));

        assertThat(player.getOpenInventory().getTopInventory().getSize()).isEqualTo(54);
    }

    @Test
    void theTitleStaysCatalogDrivenAcrossLayouts() {
        PaginatedGui gui = openAndReturn(layout(3));

        assertThat(gui.title()).isEqualTo(Component.text(TITLE));
    }

    private void open(GuiLayout layout) {
        openAndReturn(layout);
    }

    private PaginatedGui openAndReturn(GuiLayout layout) {
        KitMenuView view = new KitMenuView(new KeyMessages(), new SyncScheduler(), claimKit(), layout);
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        view.open(player, viewer, kits());
        return (PaginatedGui) player.getOpenInventory().getTopInventory().getHolder();
    }

    private static GuiLayout layout(int rows) {
        return new GuiLayout(rows, Material.CHEST, Material.ARROW, rows * 9 - 6, rows * 9 - 4, List.of());
    }

    private static List<KitDefinition> kits() {
        return List.of(KitDefinition.repeatable(KitId.of("starter"), List.<KitItem>of(), Duration.ofSeconds(60)));
    }

    private ClaimKit claimKit() {
        Messages messages = new KeyMessages();
        Permissions permissions = new AllowAllPermissions();
        KitClaimStore claims = new NoClaims();
        KitNotifier notifier = new KitNotifier(messages, new NoSink());
        KitRepository repository = new FakeRepository();
        KitGranter granter = (who, items) -> KitGranter.Grant.complete();
        KitAccess access = new KitAccess(permissions, new NoCooldowns(), claims, Optional.<KitEconomy>empty());
        return new ClaimKit(repository, access, granter, notifier, new NoEvents(), Clock.systemUTC());
    }

    private static final class FakeRepository implements KitRepository {
        @Override
        public Optional<KitDefinition> find(KitId id) {
            return Optional.empty();
        }

        @Override
        public List<KitDefinition> all() {
            return kits();
        }

        @Override
        public boolean exists(KitId id) {
            return false;
        }

        @Override
        public void save(KitDefinition definition) {}

        @Override
        public void delete(KitId id) {}
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

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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

    private static final class NoCooldowns implements com.uxplima.uxmessentials.shared.application.port.Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }
}
