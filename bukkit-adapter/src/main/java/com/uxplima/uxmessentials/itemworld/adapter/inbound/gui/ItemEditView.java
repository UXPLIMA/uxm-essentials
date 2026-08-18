package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.itemworld.adapter.ItemEdits;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.EnchantSpec;
import com.uxplima.uxmessentials.itemworld.domain.LorePolicy;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The click-driven {@code /itemedit} editor: a framed menu-engine panel that shows the operator's held item in a live
 * preview slot and offers one control button per edit the {@code /itemedit} subcommands expose. It is opened by a bare
 * {@code /itemedit} and applies every change through the shared {@link ItemEdits} the subcommands now also use, so a
 * click produces exactly the item (and the same feedback message) the equivalent subcommand does.
 *
 * <p>The preview reads the viewer's live main hand on every render: its icon is the real item serialized through the
 * {@code b64:} icon provider, and its name and lore come from the item itself, so an edit that renames, re-lores,
 * enchants, or toggles a flag shows in the preview after the panel re-opens. The controls reuse the engine's own
 * seams: a rename / lore line / custom-model-data / durability value is captured through the shared {@link TextInput},
 * an enchantment or item flag is chosen through an engine selector child window, and the rest are one-click toggles
 * and resets. There is no bespoke inventory here: the preview is a rendered display copy the anti-dupe layer strips,
 * and every window is a holder-backed engine menu routed and torn down by the one menu listener.
 *
 * <p>The panel edits the item in the player's main hand live. It captures the item's material as the menu subject at
 * open, and every action re-reads the hand and checks it against that material first: an emptied hand, or a hand whose
 * item changed type while a prompt was open, closes the panel with {@link ItemworldMessageKey#ITEMEDIT_GUI_ITEM_CHANGED}
 * rather than writing an edit onto the wrong item. Every visible string resolves from the itemworld catalog.
 */
@NullMarked
public final class ItemEditView {

    /** The engine spec id this panel registers and opens under. */
    public static final String SPEC_ID = "itemedit";

    private static final String SPEC_RESOURCE = "modules/itemworld/gui/itemedit.conf";
    private static final int ROWS = 5;

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    /** The enchant-add picker fits into six rows; its options are capped so a data-pack corpus still leaves the back slot. */
    private static final int ENCHANT_ROWS = 6;

    private static final int ENCHANT_OPTION_LIMIT = 45;
    private static final int ENCHANT_BACK_SLOT = 49;

    /** The enchant-remove and flags pickers are small fixed sets, so three rows and a centred back button suffice. */
    private static final int PICKER_ROWS = 3;

    private static final int PICKER_BACK_SLOT = 22;

    private final Menus menus;
    private final Messages messages;
    private final MessageSink messageSink;
    private final Scheduler scheduler;
    private final ItemworldConfig config;
    private final TextInput textInput;

    public ItemEditView(Menus menus, ItemworldServices services, TextInput textInput) {
        this.menus = Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(services, "services");
        this.messages = services.kernel().messages();
        this.messageSink = services.kernel().messageSink();
        this.scheduler = services.kernel().scheduler();
        this.config = services.config();
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /** Register the preview placeholders and the per-button actions the spec names, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("itemedit_preview", this::previewMaterial);
        bindings.placeholder("itemedit_preview_name", this::previewName);
        bindings.placeholder("itemedit_preview_lore", this::previewLore);
        bindings.action("itemedit:rename", this::rename);
        bindings.action("itemedit:reset-name", this::resetName);
        bindings.action("itemedit:lore-add", this::loreAdd);
        bindings.action("itemedit:lore-remove", this::loreRemove);
        bindings.action("itemedit:lore-clear", this::loreClear);
        bindings.action("itemedit:enchant-add", this::openEnchantAdd);
        bindings.action("itemedit:enchant-remove", this::openEnchantRemove);
        bindings.action("itemedit:flags", this::openFlags);
        bindings.action("itemedit:unbreakable", this::unbreakable);
        bindings.action("itemedit:model-set", this::modelSet);
        bindings.action("itemedit:model-clear", this::modelClear);
        bindings.action("itemedit:durability", this::durability);
        bindings.action("itemedit:repair", this::repair);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /** Open the editor on the viewer's held item; an empty hand answers with a message rather than an empty window. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                handExpired(player, viewer);
                return;
            }
            menus.open(viewer, SPEC_ID, new ItemEditTarget(hand.getType()));
        });
    }

    // ---- preview placeholders (read the live hand each render) ----

    /** The preview slot's icon: the held item serialized so the {@code b64:} provider renders it component-for-component. */
    private String previewMaterial(MenuContext ctx) {
        ItemStack hand = handOf(ctx.viewer());
        return hand == null || hand.getType().isAir() ? Material.BARRIER.name() : SerializedItems.encode(hand);
    }

    /** The preview slot's name: the held item's effective name (custom or vanilla) as MiniMessage source. */
    private String previewName(MenuContext ctx) {
        ItemStack hand = handOf(ctx.viewer());
        return hand == null || hand.getType().isAir() ? "" : MINI.serialize(hand.effectiveName());
    }

    /** The preview slot's lore: the held item's own lore lines, joined so the renderer splits them back apart. */
    private String previewLore(MenuContext ctx) {
        ItemStack hand = handOf(ctx.viewer());
        List<String> lore = hand == null ? List.of() : ItemEdits.currentLore(hand);
        return String.join("\n", lore);
    }

    // ---- text-driven edits ----

    private void rename(MenuActionContext ctx) {
        promptText(
                ctx,
                "itemedit.rename",
                ItemworldMessageKey.ITEMEDIT_GUI_RENAME_PROMPT,
                (player, viewer, hand, input) -> applyAndReopen(
                        player,
                        viewer,
                        ItemEdits.rename(hand, input),
                        ItemworldMessageKey.ITEMEDIT_NAME_SET,
                        Map.of("name", input)));
    }

    private void resetName(MenuActionContext ctx) {
        ItemStack hand = live(ctx);
        if (hand == null) {
            return;
        }
        applyAndReopen(
                ctx.player(),
                ctx.viewer(),
                ItemEdits.resetName(hand),
                ItemworldMessageKey.ITEMEDIT_NAME_RESET,
                Map.of());
    }

    private void loreAdd(MenuActionContext ctx) {
        promptText(
                ctx,
                "itemedit.lore-add",
                ItemworldMessageKey.ITEMEDIT_GUI_LORE_ADD_PROMPT,
                (player, viewer, hand, input) -> applyAndReopen(
                        player,
                        viewer,
                        ItemEdits.withLore(hand, LorePolicy.add(ItemEdits.currentLore(hand), input)),
                        ItemworldMessageKey.ITEMEDIT_LORE_ADDED,
                        Map.of("text", input)));
    }

    private void loreRemove(MenuActionContext ctx) {
        promptText(
                ctx,
                "itemedit.lore-remove",
                ItemworldMessageKey.ITEMEDIT_GUI_LORE_REMOVE_PROMPT,
                (player, viewer, hand, input) -> removeLoreLine(player, viewer, hand, input));
    }

    private void removeLoreLine(Player player, PlayerRef viewer, ItemStack hand, String input) {
        Optional<Integer> index = parseInt(input);
        if (index.isEmpty()) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_GUI_INVALID_NUMBER, Map.of());
            open(player, viewer);
            return;
        }
        Optional<List<String>> next = LorePolicy.remove(ItemEdits.currentLore(hand), index.get());
        if (next.isEmpty()) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_LORE_OUT_OF_RANGE, Map.of("index", str(index.get())));
            open(player, viewer);
            return;
        }
        applyAndReopen(
                player,
                viewer,
                ItemEdits.withLore(hand, next.get()),
                ItemworldMessageKey.ITEMEDIT_LORE_REMOVED,
                Map.of("index", str(index.get())));
    }

    private void loreClear(MenuActionContext ctx) {
        ItemStack hand = live(ctx);
        if (hand == null) {
            return;
        }
        applyAndReopen(
                ctx.player(),
                ctx.viewer(),
                ItemEdits.withLore(hand, List.of()),
                ItemworldMessageKey.ITEMEDIT_LORE_CLEARED,
                Map.of());
    }

    private void modelSet(MenuActionContext ctx) {
        promptText(
                ctx,
                "itemedit.model",
                ItemworldMessageKey.ITEMEDIT_GUI_MODEL_PROMPT,
                (player, viewer, hand, input) -> setModel(player, viewer, hand, input));
    }

    private void setModel(Player player, PlayerRef viewer, ItemStack hand, String input) {
        Optional<Integer> id = parseInt(input);
        if (id.isEmpty() || id.get() < 0) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_GUI_INVALID_NUMBER, Map.of());
            open(player, viewer);
            return;
        }
        applyAndReopen(
                player,
                viewer,
                ItemEdits.customModelData(hand, OptionalInt.of(id.get())),
                ItemworldMessageKey.ITEMEDIT_MODEL_SET,
                Map.of("id", str(id.get())));
    }

    private void modelClear(MenuActionContext ctx) {
        ItemStack hand = live(ctx);
        if (hand == null) {
            return;
        }
        applyAndReopen(
                ctx.player(),
                ctx.viewer(),
                ItemEdits.customModelData(hand, OptionalInt.empty()),
                ItemworldMessageKey.ITEMEDIT_MODEL_CLEARED,
                Map.of());
    }

    private void durability(MenuActionContext ctx) {
        promptText(
                ctx,
                "itemedit.durability",
                ItemworldMessageKey.ITEMEDIT_GUI_DURABILITY_PROMPT,
                (player, viewer, hand, input) -> setDurability(player, viewer, hand, input));
    }

    private void setDurability(Player player, PlayerRef viewer, ItemStack hand, String input) {
        short max = hand.getType().getMaxDurability();
        if (max <= 0 || !(hand.getItemMeta() instanceof Damageable)) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_DURABILITY_NOT_DAMAGEABLE, Map.of());
            open(player, viewer);
            return;
        }
        Optional<Integer> value = parseInt(input);
        if (value.isEmpty()) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_GUI_INVALID_NUMBER, Map.of());
            open(player, viewer);
            return;
        }
        if (value.get() < 0 || value.get() > max) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_DURABILITY_OUT_OF_RANGE, Map.of("max", str(max)));
            open(player, viewer);
            return;
        }
        applyAndReopen(
                player,
                viewer,
                ItemEdits.damage(hand, value.get()),
                ItemworldMessageKey.ITEMEDIT_DURABILITY_SET,
                Map.of("value", str(value.get())));
    }

    private void repair(MenuActionContext ctx) {
        ItemStack hand = live(ctx);
        if (hand == null) {
            return;
        }
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        if (!(hand.getItemMeta() instanceof Damageable damageable) || !damageable.hasDamage()) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_REPAIR_NOTHING, Map.of());
            open(player, viewer);
            return;
        }
        applyAndReopen(player, viewer, ItemEdits.damage(hand, 0), ItemworldMessageKey.ITEMEDIT_REPAIRED, Map.of());
    }

    // ---- one-click toggles ----

    private void unbreakable(MenuActionContext ctx) {
        ItemStack hand = live(ctx);
        if (hand == null) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        boolean next = meta == null || !meta.isUnbreakable();
        applyAndReopen(
                ctx.player(),
                ctx.viewer(),
                ItemEdits.unbreakable(hand, next),
                ItemworldMessageKey.ITEMEDIT_UNBREAKABLE_SET,
                Map.of("state", onOff(next)));
    }

    // ---- enchant picker ----

    private void openEnchantAdd(MenuActionContext ctx) {
        ItemStack hand = live(ctx);
        if (hand == null) {
            return;
        }
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Material expected = hand.getType();
        List<Enchantment> enchants = allEnchants();
        List<SelectorButton> buttons = new ArrayList<>();
        int count = Math.min(enchants.size(), ENCHANT_OPTION_LIMIT);
        for (int i = 0; i < count; i++) {
            Enchantment enchant = enchants.get(i);
            String id = enchant.getKey().value();
            ItemStack icon = ItemBuilder.of(Material.ENCHANTED_BOOK)
                    .name(text(viewer, ItemworldMessageKey.ITEMEDIT_GUI_ENCHANT_ADD_ENTRY, Map.of("enchant", id)))
                    .build();
            buttons.add(SelectorButton.of(i, icon, () -> promptEnchantLevel(player, viewer, expected, enchant, id)));
        }
        buttons.add(SelectorButton.of(ENCHANT_BACK_SLOT, backIcon(viewer), () -> open(player, viewer)));
        menus.openSelector(
                viewer,
                text(viewer, ItemworldMessageKey.ITEMEDIT_GUI_ENCHANT_ADD_TITLE),
                ENCHANT_ROWS,
                FILLER,
                buttons);
    }

    private void promptEnchantLevel(
            Player player, PlayerRef viewer, Material expected, Enchantment enchant, String id) {
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(
                        "itemedit.enchant-level",
                        ItemworldMessageKey.ITEMEDIT_GUI_ENCHANT_LEVEL_PROMPT,
                        Map.of("enchant", id)),
                input -> applyEnchant(player, viewer, expected, enchant, id, input),
                () -> open(player, viewer));
    }

    private void applyEnchant(
            Player player, PlayerRef viewer, Material expected, Enchantment enchant, String id, String input) {
        ItemStack hand = liveHand(player, viewer, expected);
        if (hand == null) {
            return;
        }
        Optional<Integer> level = parseInt(input);
        if (level.isEmpty() || level.get() < 1) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_GUI_INVALID_NUMBER, Map.of());
            open(player, viewer);
            return;
        }
        EnchantSpec spec = EnchantSpec.forEdit(
                        id,
                        level.get(),
                        enchant.getMaxLevel(),
                        config.itemEditAllowOverMaxEnchants(),
                        config.maxEnchantLevel())
                .orElseThrow();
        if (spec.clamped()) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_ENCHANT_CLAMPED, Map.of("level", str(spec.level())));
        }
        applyAndReopen(
                player,
                viewer,
                ItemEdits.enchant(hand, enchant, spec.level()),
                ItemworldMessageKey.ITEMEDIT_ENCHANTED,
                Map.of("enchant", id, "level", str(spec.level())));
    }

    private void openEnchantRemove(MenuActionContext ctx) {
        ItemStack hand = live(ctx);
        if (hand == null) {
            return;
        }
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Material expected = hand.getType();
        ItemMeta meta = hand.getItemMeta();
        Map<Enchantment, Integer> enchants = meta == null ? Map.of() : meta.getEnchants();
        if (enchants.isEmpty()) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_GUI_NO_ENCHANTS, Map.of());
            open(player, viewer);
            return;
        }
        List<SelectorButton> buttons = new ArrayList<>();
        int slot = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            String id = enchant.getKey().value();
            ItemStack icon = ItemBuilder.of(Material.ENCHANTED_BOOK)
                    .name(text(
                            viewer,
                            ItemworldMessageKey.ITEMEDIT_GUI_ENCHANT_REMOVE_ENTRY,
                            Map.of("enchant", id, "level", str(entry.getValue()))))
                    .build();
            buttons.add(SelectorButton.of(slot++, icon, () -> removeEnchant(player, viewer, expected, enchant, id)));
        }
        buttons.add(SelectorButton.of(PICKER_BACK_SLOT, backIcon(viewer), () -> open(player, viewer)));
        menus.openSelector(
                viewer,
                text(viewer, ItemworldMessageKey.ITEMEDIT_GUI_ENCHANT_REMOVE_TITLE),
                PICKER_ROWS,
                FILLER,
                buttons);
    }

    private void removeEnchant(Player player, PlayerRef viewer, Material expected, Enchantment enchant, String id) {
        ItemStack hand = liveHand(player, viewer, expected);
        if (hand == null) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (meta == null || !meta.hasEnchant(enchant)) {
            reply(viewer, ItemworldMessageKey.ITEMEDIT_ENCHANT_NOT_PRESENT, Map.of("enchant", id));
            open(player, viewer);
            return;
        }
        applyAndReopen(
                player,
                viewer,
                ItemEdits.removeEnchant(hand, enchant),
                ItemworldMessageKey.ITEMEDIT_ENCHANT_REMOVED,
                Map.of("enchant", id));
    }

    // ---- item-flag picker ----

    private void openFlags(MenuActionContext ctx) {
        ItemStack hand = live(ctx);
        if (hand == null) {
            return;
        }
        openFlagsFor(ctx.player(), ctx.viewer(), hand.getType());
    }

    private void openFlagsFor(Player player, PlayerRef viewer, Material expected) {
        ItemStack hand = liveHand(player, viewer, expected);
        if (hand == null) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        List<SelectorButton> buttons = new ArrayList<>();
        int slot = 0;
        for (ItemFlag flag : ItemFlag.values()) {
            boolean on = meta != null && meta.hasItemFlag(flag);
            String token = flag.name().toLowerCase(Locale.ROOT);
            ItemStack icon = ItemBuilder.of(on ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(Tiles.blankName())
                    .lore(Tiles.titled(
                            text(viewer, ItemworldMessageKey.ITEMEDIT_GUI_FLAG_ENTRY, Map.of("flag", token)),
                            List.of(text(
                                    viewer,
                                    ItemworldMessageKey.ITEMEDIT_GUI_FLAG_STATE,
                                    Map.of("state", onOffWord(viewer, on))))))
                    .build();
            buttons.add(SelectorButton.of(slot++, icon, () -> toggleFlag(player, viewer, expected, flag, token)));
        }
        buttons.add(SelectorButton.of(PICKER_BACK_SLOT, backIcon(viewer), () -> open(player, viewer)));
        menus.openSelector(
                viewer, text(viewer, ItemworldMessageKey.ITEMEDIT_GUI_FLAGS_TITLE), PICKER_ROWS, FILLER, buttons);
    }

    private void toggleFlag(Player player, PlayerRef viewer, Material expected, ItemFlag flag, String token) {
        ItemStack hand = liveHand(player, viewer, expected);
        if (hand == null) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        boolean next = meta == null || !meta.hasItemFlag(flag);
        player.getInventory().setItemInMainHand(ItemEdits.setFlag(hand, flag, next));
        reply(viewer, ItemworldMessageKey.ITEMEDIT_FLAG_TOGGLED, Map.of("flag", token, "state", onOff(next)));
        openFlagsFor(player, viewer, expected);
    }

    // ---- shared helpers ----

    /** Resolve the live hand for an action still in the panel, closing gracefully when the item is gone or changed. */
    private @Nullable ItemStack live(MenuActionContext ctx) {
        return liveHand(
                ctx.player(), ctx.viewer(), ctx.subject(ItemEditTarget.class).type());
    }

    /**
     * The player's main-hand item when it still matches {@code expected}, else {@code null} after telling the viewer
     * the item changed and closing the panel: the guard every edit runs so a prompt held open across a hand swap
     * never writes onto the wrong item.
     */
    private @Nullable ItemStack liveHand(Player player, PlayerRef viewer, Material expected) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getType() != expected) {
            handExpired(player, viewer);
            return null;
        }
        return hand;
    }

    /** Write the edited item back to the main hand, report the same key the subcommand does, and re-open the panel. */
    private void applyAndReopen(
            Player player, PlayerRef viewer, ItemStack updated, MessageKey key, Map<String, String> placeholders) {
        player.getInventory().setItemInMainHand(updated);
        reply(viewer, key, placeholders);
        open(player, viewer);
    }

    /**
     * Capture a line of text for {@code key} through the shared input seam, then apply {@code edit} to the freshly
     * re-resolved hand on submit; a cancel re-opens the panel unchanged. The expected material is captured before the
     * prompt so the submit still guards against a hand swap while the anvil/chat was open.
     */
    private void promptText(MenuActionContext ctx, String inputKey, MessageKey label, TextEdit edit) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Material expected = ctx.subject(ItemEditTarget.class).type();
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(inputKey, label),
                input -> {
                    ItemStack hand = liveHand(player, viewer, expected);
                    if (hand != null) {
                        edit.apply(player, viewer, hand, input);
                    }
                },
                () -> open(player, viewer));
    }

    /** Tell the viewer their edit target is gone and close the panel: the graceful exit for an emptied or swapped hand. */
    private void handExpired(Player player, PlayerRef viewer) {
        player.closeInventory();
        reply(viewer, ItemworldMessageKey.ITEMEDIT_GUI_ITEM_CHANGED, Map.of());
    }

    private @Nullable ItemStack handOf(PlayerRef viewer) {
        Player player = Bukkit.getPlayer(viewer.uuid());
        return player == null ? null : player.getInventory().getItemInMainHand();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.ARROW)
                .name(text(viewer, ItemworldMessageKey.ITEMEDIT_GUI_SELECTOR_BACK))
                .build();
    }

    private String onOffWord(PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? ItemworldMessageKey.ITEMEDIT_GUI_VALUE_ON : ItemworldMessageKey.ITEMEDIT_GUI_VALUE_OFF,
                Map.of());
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    private void reply(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /** Every enchantment in the live registry, sorted by key so the picker order is stable. */
    private static List<Enchantment> allEnchants() {
        List<Enchantment> out = new ArrayList<>();
        RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).forEach(out::add);
        out.sort(Comparator.comparing(enchant -> enchant.getKey().value()));
        return out;
    }

    private static Optional<Integer> parseInt(String raw) {
        try {
            return Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static String str(int value) {
        return Integer.toString(value);
    }

    /** The held item's material at open, the subject the type-change guard compares the live hand against. */
    private record ItemEditTarget(Material type) {}

    /** A text-driven edit applied to the freshly re-resolved hand once the shared input seam accepts a line. */
    @FunctionalInterface
    private interface TextEdit {
        void apply(Player player, PlayerRef viewer, ItemStack hand, String input);
    }
}
