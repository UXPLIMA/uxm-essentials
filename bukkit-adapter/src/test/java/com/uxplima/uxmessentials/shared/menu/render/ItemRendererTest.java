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

    @Test
    void multiLinePlaceholderExpandsIntoSeparateLoreLines() {
        // A per-entry icon (kit/warp browse) emits a variable number of lines through one placeholder; the engine
        // must turn each newline-separated segment into its own lore component, in order.
        PlaceholderRegistry ph = new PlaceholderRegistry();
        ph.register("status", c -> "line one\nline two\nline three");
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), ph);

        assertThat(plainLore(r, itemWithLore(List.of("%status%"))))
                .containsExactly("line one", "line two", "line three");
    }

    @Test
    void singleLineLiteralStaysOneLoreLine() {
        // Regression: a spec line with no embedded newline must still render as exactly one component.
        assertThat(plainLore(renderer, itemWithLore(List.of("just one line")))).containsExactly("just one line");
    }

    @Test
    void catalogLineIsNotSplitEvenWhenMultiLine() {
        // Catalog output owns its own layout, so an @key line stays a single lore component even if the resolved
        // text carries a newline — only inline/placeholder literals expand. Asserted on the renderer's own output,
        // since the downstream ItemBuilder breaks any newline-bearing component into visual lines.
        assertThat(plainLore(renderer, itemWithLore(List.of("@first\nsecond")))).containsExactly("first\nsecond");
    }

    @Test
    void mixedLoreExpandsOnlyTheMultiLinePlaceholder() {
        // A real browse icon mixes a catalog header, a multi-line placeholder body, and a plain footer: 1 + 2 + 1.
        PlaceholderRegistry ph = new PlaceholderRegistry();
        ph.register("status", c -> "ok\ncost 10");
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), ph);

        assertThat(plainLore(r, itemWithLore(List.of("@some_key", "%status%", "plain"))))
                .containsExactly("some_key", "ok", "cost 10", "plain");
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

    private static MenuItemSpec itemWithLore(List<String> lore) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                "STONE",
                "",
                lore,
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    // Assert on the renderer's own lore components, before ItemBuilder breaks any newline-bearing line into
    // visual lines on the stack; this is the layer the expansion feature owns.
    private List<String> plainLore(ItemRenderer r, MenuItemSpec spec) {
        return plainLore(r, spec, ctx);
    }

    private static List<String> plainLore(ItemRenderer r, MenuItemSpec spec, MenuContext c) {
        return r.lore(spec, c).stream()
                .map(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(line))
                .toList();
    }

    @Test
    void argumentTokenExpandsFromTheOpenContextArguments() {
        // A command opened with typed arguments carries them on the context; %argument_<name>% must render its value
        // in a name (and lore) without needing a registered placeholder.
        MenuContext argCtx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("amount", "5"));

        ItemStack it = renderer.render(itemNamed("You gave %argument_amount%"), argCtx);

        assertThat(plainName(it)).isEqualTo("You gave 5");
    }

    @Test
    void anUnknownArgumentTokenRendersEmpty() {
        MenuContext argCtx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("amount", "5"));

        ItemStack it = renderer.render(itemNamed("[%argument_missing%]"), argCtx);

        assertThat(plainName(it)).isEqualTo("[]");
    }

    @Test
    void anArgumentTokenInLoreExpandsWhileANormalPlaceholderStillResolves() {
        // Regression guard: adding the argument_ special-case must not shadow the registry — %icon% still resolves
        // (registered to "DIAMOND" in setUp), and %argument_target% resolves from the context arguments.
        MenuContext argCtx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0, Map.of("target", "Steve"));

        assertThat(plainLore(renderer, itemWithLore(List.of("to %argument_target%", "icon %icon%")), argCtx))
                .containsExactly("to Steve", "icon DIAMOND");
    }

    @Test
    void catalogKeyFillsTokensFromPlaceholders() {
        // A list template re-renders each entry with ctx.withEntry(...); the catalog key carries {sound}, which
        // the engine must fill from the registered placeholder so the option shows that entry's name, not "{sound}".
        PlaceholderRegistry ph = new PlaceholderRegistry();
        ph.register("sound", c -> c.entry(String.class));
        ItemRenderer r = new ItemRenderer(new GuiText(new KeyMessages()), ph);
        MenuContext entryCtx =
                MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0).withEntry("Enderman Teleport");

        ItemStack it = r.render(itemNamed("@Sound: {sound}"), entryCtx);

        assertThat(plainName(it)).isEqualTo("Sound: Enderman Teleport");
    }

    private static MenuItemSpec itemNamed(String name) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                "STONE",
                name,
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static String plainName(ItemStack item) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            // Mirror the real catalog: the key's text carries {token} arguments the placeholders map fills.
            String text = key.key();
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return text;
        }
    }
}
