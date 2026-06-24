package com.uxplima.uxmessentials.shared.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Renders single menu items against a fake catalog so the material-resolution and decor paths can be checked
 * without loading a real spec. MockBukkit gives a server context so {@code ItemStack}/{@code ItemMeta} behave
 * as on a live server; the catalog returns the key verbatim, which is enough for material/decor assertions.
 */
class ItemRendererTest {

    private ItemRenderer renderer;
    private MenuContext ctx;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        GuiText guiText = new GuiText(new KeyMessages());
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("icon", c -> "DIAMOND");
        renderer = new ItemRenderer(guiText, placeholders);
        ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesPlaceholderMaterial() {
        ItemStack it = renderer.render(item("%icon%", new ItemDecor(1, Optional.empty(), false, List.of())), ctx);
        assertThat(it.getType()).isEqualTo(Material.DIAMOND);
    }

    @Test
    void unknownMaterialFallsBackToStone() {
        ItemStack it =
                renderer.render(item("not_a_material", new ItemDecor(1, Optional.empty(), false, List.of())), ctx);
        assertThat(it.getType()).isEqualTo(Material.STONE);
    }

    @Test
    void glowMakesTheItemEnchanted() {
        ItemStack it = renderer.render(item("STONE", new ItemDecor(1, Optional.empty(), true, List.of())), ctx);
        // ItemBuilder.glow(true) uses the native glint override, not a dummy enchant, so assert the override.
        assertThat(it.getItemMeta().getEnchantmentGlintOverride()).isTrue();
    }

    private static MenuItemSpec item(String material, ItemDecor decor) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                material,
                "",
                List.of(),
                decor,
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
