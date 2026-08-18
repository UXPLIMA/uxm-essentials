package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagKind;
import com.uxplima.uxmessentials.regions.domain.FlagState;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The per-region flag editor: an engine-backed paginated panel (the shared {@link EntityListView}, so it renders
 * through the menu engine and needs no raw-inventory allow-list entry) with one icon per registered WorldGuard flag,
 * showing that flag's current value in the region. Every registered flag is listed by default (config
 * {@code flags.editable} is an optional allow-list); the panel paginates when there are more flags than fit a page.
 *
 * <p>A click opens a control appropriate to the flag's {@link FlagKind}: a state cycles allow / deny / unset, a boolean
 * toggles true / false / unset, a fixed-choice flag opens a choice picker, a text / number flag opens a text prompt,
 * and an unsupported-complex flag is shown read-only. Whatever the control, the chosen value is written back through
 * {@link RegionService#setFlag}: the same seam a command would use, so the GUI holds no WorldGuard logic of its own.
 *
 * <p>Each flag's current value is read off the tick thread through the {@link RegionService} (WorldGuard's region store
 * is queried on the global region thread, never a viewer's region thread), then the panel opens on the staff member's
 * own entity thread over that snapshot. A write applies on the global region thread, where WorldGuard mutations belong,
 * then re-reads the region and re-opens the panel on the viewer's entity thread, so a fresh panel always reflects the
 * value that just landed. A fresh {@link EntityListView} is built per open, so two staff editing different regions never
 * share panel state.
 *
 * <p>As the region's detail panel, it carries one extra button: a "members &amp; owners" control that hands the region
 * to the injected {@code onManageMembers} callback: the wiring points it at the roster editor, gated on the members
 * permission, so the roster panel is reachable by a click from the same detail the region list opens.
 */
@NullMarked
public final class RegionFlagEditorView {

    /** The filler and glass materials the choice picker paints its background with. */
    private static final Material PICKER_FILLER = Material.BLACK_STAINED_GLASS_PANE;

    /** The stable input-point key the operator flips between anvil and chat for the string/number flag prompt. */
    private static final String INPUT_KEY = "regions.flag-value";

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final RegionService service;
    private final FlagValuePrompt prompt;
    private final List<String> editableFlags;
    private final EntityListLayout layout;
    private final BiConsumer<Player, RegionRef> onManageMembers;

    public RegionFlagEditorView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink,
            RegionService service,
            FlagValuePrompt prompt,
            List<String> editableFlags,
            EntityListLayout layout,
            BiConsumer<Player, RegionRef> onManageMembers) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.service = Objects.requireNonNull(service, "service");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.editableFlags = List.copyOf(Objects.requireNonNull(editableFlags, "editableFlags"));
        this.layout = Objects.requireNonNull(layout, "layout");
        this.onManageMembers = Objects.requireNonNull(onManageMembers, "onManageMembers");
    }

    /** Read {@code region}'s flag descriptors off the tick thread, then open the editor for {@code staff}. */
    public void open(PlayerRef staff, RegionRef region) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(region, "region");
        scheduler.onGlobal(() -> {
            List<FlagRow> rows = readRows(region);
            scheduler.onEntity(staff, () -> openResolved(staff, region, rows));
        });
    }

    /**
     * Snapshot the flag rows the panel shows, off the reading thread. With no allow-list the whole registry shows in
     * its natural (name) order; a non-empty allow-list restricts and re-orders the list to exactly the named flags.
     */
    private List<FlagRow> readRows(RegionRef region) {
        List<FlagDescriptor> descriptors = service.flagDescriptors(region);
        if (editableFlags.isEmpty()) {
            return descriptors.stream().map(FlagRow::new).toList();
        }
        Map<String, FlagDescriptor> byName = new HashMap<>();
        for (FlagDescriptor descriptor : descriptors) {
            byName.put(descriptor.name().toLowerCase(Locale.ROOT), descriptor);
        }
        List<FlagRow> rows = new ArrayList<>();
        for (String name : editableFlags) {
            FlagDescriptor descriptor = byName.get(name);
            if (descriptor != null) {
                rows.add(new FlagRow(descriptor));
            }
        }
        return rows;
    }

    private void openResolved(PlayerRef staff, RegionRef region, List<FlagRow> rows) {
        Player viewer = Bukkit.getPlayer(staff.uuid());
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        EntityListView.<FlagRow>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(RegionsMessageKey.REGIONS_FLAGS_TITLE)
                .emptyTitle(RegionsMessageKey.REGIONS_FLAGS_EMPTY)
                .navNames(RegionsMessageKey.REGIONS_FLAGS_PREV, RegionsMessageKey.REGIONS_FLAGS_NEXT)
                .entities(() -> rows)
                .iconRenderer(this::icon)
                .onSelect((clicker, row) -> select(region, clicker, row))
                .onAction(RegionsMessageKey.REGIONS_MEMBERS_ACTION, clicker -> onManageMembers.accept(clicker, region))
                .build()
                .open(viewer, staff);
    }

    private ItemStack icon(PlayerRef viewer, FlagRow row) {
        Map<String, String> placeholders = Map.of(
                "flag", row.flag(),
                "value", displayValue(viewer, row),
                "kind", messages.resolve(viewer, kindKey(row.kind()), Map.of()),
                "hint", messages.resolve(viewer, hintKey(row.kind()), Map.of()));
        return ItemBuilder.of(material(row))
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, RegionsMessageKey.REGIONS_FLAGS_FLAG, placeholders),
                        guiText.text(viewer, RegionsMessageKey.REGIONS_FLAGS_FLAG_INFO, placeholders)))
                .build();
    }

    /** Route a click to the control the flag's kind wants: cycle, toggle, choose, prompt, or a read-only refusal. */
    private void select(RegionRef region, Player clicker, FlagRow row) {
        PlayerRef ref = BukkitRefs.toRef(clicker);
        switch (row.kind()) {
            case STATE ->
                applyValue(
                        region,
                        ref,
                        FlagValue.of(row.flag(), FlagState.of(row.value()).next()));
            case BOOLEAN -> applyValue(region, ref, new FlagValue(row.flag(), nextBoolean(row.value())));
            case ENUM -> openChoicePicker(region, ref, row);
            case STRING, INTEGER, DOUBLE -> promptValue(region, clicker, ref, row);
            case OTHER -> {
                notify(ref, RegionsMessageKey.REGIONS_FLAGS_NOT_EDITABLE, Map.of("flag", row.flag()));
                reopen(ref, region);
            }
        }
    }

    /** Write {@code next} on the global thread, then re-read and re-open on the entity thread; a rejected write reports. */
    private void applyValue(RegionRef region, PlayerRef ref, FlagValue next) {
        scheduler.onGlobal(() -> {
            boolean failed = !trySet(region, next);
            List<FlagRow> refreshed = readRows(region);
            scheduler.onEntity(ref, () -> {
                if (failed) {
                    notify(ref, RegionsMessageKey.REGIONS_FLAGS_FAILED, Map.of("flag", next.name()));
                }
                openResolved(ref, region, refreshed);
            });
        });
    }

    /** Apply the write, reporting whether it landed; a service failure is turned into a caller-visible {@code false}. */
    private boolean trySet(RegionRef region, FlagValue next) {
        try {
            service.setFlag(region, next);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** Open a choice picker of the flag's choices plus an unset option; a pick writes the chosen value and re-opens. */
    private void openChoicePicker(RegionRef region, PlayerRef ref, FlagRow row) {
        List<String> choices = row.descriptor().choices();
        int total = choices.size() + 1;
        int rows = Math.min(6, Math.max(1, (total + 8) / 9));
        int capacity = rows * 9;
        List<SelectorButton> buttons = new ArrayList<>();
        buttons.add(SelectorButton.of(0, unsetOption(ref, row), () -> applyValue(region, ref, clear(row.flag()))));
        for (int i = 0; i < choices.size() && i + 1 < capacity; i++) {
            String choice = choices.get(i);
            buttons.add(SelectorButton.of(
                    i + 1,
                    choiceOption(ref, choice, choice.equalsIgnoreCase(row.value())),
                    () -> applyValue(region, ref, new FlagValue(row.flag(), choice))));
        }
        Component title = guiText.text(ref, RegionsMessageKey.REGIONS_FLAGS_PICK_TITLE, Map.of("flag", row.flag()));
        menus.openSelector(ref, title, rows, PICKER_FILLER, buttons);
    }

    /** Prompt for a line, then write it (validating a number); an empty line clears the flag, a cancel re-opens. */
    private void promptValue(RegionRef region, Player clicker, PlayerRef ref, FlagRow row) {
        String initial = row.value().isEmpty() ? null : row.value();
        InputRequest request = new InputRequest(
                INPUT_KEY, RegionsMessageKey.REGIONS_FLAGS_INPUT_PROMPT, Map.of("flag", row.flag()), initial);
        prompt.prompt(clicker, ref, request, line -> submitValue(region, ref, row, line), () -> reopen(ref, region));
    }

    private void submitValue(RegionRef region, PlayerRef ref, FlagRow row, String line) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && !parsesAsNumber(row.kind(), trimmed)) {
            notify(ref, RegionsMessageKey.REGIONS_FLAGS_NUMBER_INVALID, Map.of("value", trimmed));
            reopen(ref, region);
            return;
        }
        applyValue(region, ref, new FlagValue(row.flag(), trimmed));
    }

    /** Re-read the region and re-open the flag panel, from any thread the callback ran on. */
    private void reopen(PlayerRef ref, RegionRef region) {
        scheduler.onGlobal(() -> {
            List<FlagRow> refreshed = readRows(region);
            scheduler.onEntity(ref, () -> openResolved(ref, region, refreshed));
        });
    }

    /** The value shown in a flag's lore: a localised word for state/boolean/unset, else the raw value. */
    private String displayValue(PlayerRef viewer, FlagRow row) {
        if (row.descriptor().unset()) {
            return word(viewer, RegionsMessageKey.REGIONS_FLAGS_STATE_UNSET);
        }
        return switch (row.kind()) {
            case STATE -> word(viewer, stateKey(FlagState.of(row.value())));
            case BOOLEAN ->
                word(
                        viewer,
                        isTrue(row.value())
                                ? RegionsMessageKey.REGIONS_FLAGS_BOOL_TRUE
                                : RegionsMessageKey.REGIONS_FLAGS_BOOL_FALSE);
            default -> row.value();
        };
    }

    /** The icon material: a coloured dye for a state/boolean, a distinct item per other kind. */
    private static Material material(FlagRow row) {
        if (row.descriptor().unset() && (row.kind() == FlagKind.STATE || row.kind() == FlagKind.BOOLEAN)) {
            return Material.GRAY_DYE;
        }
        return switch (row.kind()) {
            case STATE -> stateIcon(FlagState.of(row.value()));
            case BOOLEAN -> isTrue(row.value()) ? Material.LIME_DYE : Material.RED_DYE;
            case STRING -> Material.NAME_TAG;
            case INTEGER -> Material.REPEATER;
            case DOUBLE -> Material.COMPARATOR;
            case ENUM -> Material.BOOK;
            case OTHER -> Material.BARRIER;
        };
    }

    private ItemStack unsetOption(PlayerRef viewer, FlagRow row) {
        boolean selected = row.descriptor().unset();
        ItemBuilder builder = ItemBuilder.of(Material.GRAY_DYE)
                .name(guiText.text(viewer, RegionsMessageKey.REGIONS_FLAGS_STATE_UNSET));
        return glintIf(builder, selected).build();
    }

    private ItemStack choiceOption(PlayerRef viewer, String choice, boolean selected) {
        ItemBuilder builder = ItemBuilder.of(Material.PAPER)
                .name(guiText.text(viewer, RegionsMessageKey.REGIONS_FLAGS_PICK_OPTION, Map.of("choice", choice)));
        return glintIf(builder, selected).build();
    }

    /** Add a glint marking the live option, hiding the enchant from the tooltip (mirrors the shared enum property). */
    private static ItemBuilder glintIf(ItemBuilder builder, boolean selected) {
        return selected ? builder.enchant(Enchantment.UNBREAKING, 1).flags(ItemFlag.HIDE_ENCHANTS) : builder;
    }

    private String word(PlayerRef viewer, MessageKey key) {
        return messages.resolve(viewer, key, Map.of());
    }

    private void notify(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }

    /** The clear-this-flag request: an empty value the service reads as "unset". */
    private static FlagValue clear(String flag) {
        return new FlagValue(flag, "");
    }

    /** The next value in the boolean toggle: {@code unset → true → false → unset}. */
    private static String nextBoolean(String value) {
        if (value.isBlank()) {
            return "true";
        }
        return isTrue(value) ? "false" : "";
    }

    private static boolean isTrue(String value) {
        return value.equalsIgnoreCase("true");
    }

    /** Whether {@code value} parses as the integer/double the number prompt requires; other kinds never reject here. */
    private static boolean parsesAsNumber(FlagKind kind, String value) {
        try {
            switch (kind) {
                case INTEGER -> Integer.parseInt(value);
                case DOUBLE -> Double.parseDouble(value);
                default -> {
                    return true;
                }
            }
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    private static MessageKey stateKey(FlagState state) {
        return switch (state) {
            case ALLOW -> RegionsMessageKey.REGIONS_FLAGS_STATE_ALLOW;
            case DENY -> RegionsMessageKey.REGIONS_FLAGS_STATE_DENY;
            case UNSET -> RegionsMessageKey.REGIONS_FLAGS_STATE_UNSET;
        };
    }

    private static Material stateIcon(FlagState state) {
        return switch (state) {
            case ALLOW -> Material.LIME_DYE;
            case DENY -> Material.RED_DYE;
            case UNSET -> Material.GRAY_DYE;
        };
    }

    private static MessageKey kindKey(FlagKind kind) {
        return switch (kind) {
            case STATE -> RegionsMessageKey.REGIONS_FLAGS_KIND_STATE;
            case BOOLEAN -> RegionsMessageKey.REGIONS_FLAGS_KIND_BOOLEAN;
            case STRING -> RegionsMessageKey.REGIONS_FLAGS_KIND_STRING;
            case INTEGER -> RegionsMessageKey.REGIONS_FLAGS_KIND_INTEGER;
            case DOUBLE -> RegionsMessageKey.REGIONS_FLAGS_KIND_DOUBLE;
            case ENUM -> RegionsMessageKey.REGIONS_FLAGS_KIND_ENUM;
            case OTHER -> RegionsMessageKey.REGIONS_FLAGS_KIND_OTHER;
        };
    }

    private static MessageKey hintKey(FlagKind kind) {
        return switch (kind) {
            case STATE, BOOLEAN -> RegionsMessageKey.REGIONS_FLAGS_HINT_CYCLE;
            case STRING, INTEGER, DOUBLE -> RegionsMessageKey.REGIONS_FLAGS_HINT_INPUT;
            case ENUM -> RegionsMessageKey.REGIONS_FLAGS_HINT_PICK;
            case OTHER -> RegionsMessageKey.REGIONS_FLAGS_HINT_READONLY;
        };
    }
}
