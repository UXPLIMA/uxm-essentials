package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
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
 * End-to-end proof that a menu-local item pattern survives a live open. A real {@link MenuSpecLoader} expands the
 * pattern into an ordinary item at load, filling its declared {@code %var%}s, and a real {@link Menus} renders it,
 * so the assertions land on the live top inventory's icon rather than the parsed model alone. The point a golden
 * makes that a loader test cannot: an undeclared {@code %placeholder%} the pattern left verbatim still resolves at
 * render time, proving the two token kinds coexist — {@code %var%} filled at load, {@code %placeholder%} at draw.
 */
class MenuPatternGoldenTest {

    private ServerMock server;
    private PlayerMock player;
    private MenuBindings bindings;
    private Menus menus;
    private MenuSpecLoader loader;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Viewer");

        bindings = new MenuBindings();
        // A render-time placeholder the pattern never declares as a var: it must survive load untouched to be filled
        // here.
        bindings.placeholder("viewer_tag", ctx -> "Steve");
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        menus = new Menus(renderer, new InlineScheduler(), bindings.lists());
        loader = new MenuSpecLoader();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPatternedItemOpensWithItsVarsFilledAndUndeclaredPlaceholdersSurviveToRender() {
        Inventory inv = open("shop", """
                patterns {
                  shop-button {
                    material = "%mat%"
                    name = "<gold>%label%"
                    lore = ["Buy %label%", "Seen by %viewer_tag%"]
                    defaults { mat = STONE }
                  }
                }
                items {
                  diamonds { slot = 0, pattern = "shop-button", vars { mat = "DIAMOND", label = "Diamonds" } }
                }
                """);

        ItemStack item = Objects.requireNonNull(inv.getItem(0), "the patterned item renders at slot 0");
        assertThat(item.getType()).isEqualTo(Material.DIAMOND);
        assertThat(plainName(item)).isEqualTo("Diamonds");
        assertThat(plainLore(item))
                .as("the declared %label% is filled at load; the undeclared %viewer_tag% is filled at render")
                .containsExactly("Buy Diamonds", "Seen by Steve");
    }

    private Inventory open(String id, String hocon) {
        menus.registerSpec(id, loader.parse(hocon));
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), id, null);
        return player.getOpenInventory().getTopInventory();
    }

    private static String plainName(ItemStack item) {
        Component name = Objects.requireNonNull(item.getItemMeta()).displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    private static List<String> plainLore(ItemStack item) {
        List<Component> lore = Objects.requireNonNull(item.getItemMeta()).lore();
        return lore == null
                ? List.of()
                : lore.stream()
                        .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                        .toList();
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled hop inline so the open completes on the test thread. */
    private static final class InlineScheduler implements Scheduler {
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
