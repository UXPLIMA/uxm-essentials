package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.menu.EntityEditorLayout;
import com.uxplima.uxmlib.menu.EntityEditorView;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.ListProperty;
import com.uxplima.uxmlib.menu.property.ListPropertyLayout;
import com.uxplima.uxmlib.menu.property.ListPropertyText;
import com.uxplima.uxmlib.menu.property.TextProperty;
import com.uxplima.uxmlib.menu.property.ToggleProperty;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.NullMarked;

/**
 * The open-command sub-editor opened from the menu-property editor's "Open command" row: it edits the menu's
 * {@code command {}} block: the {@code /shop}-style command a menu registers for itself. It is a thin consumer of the
 * shared {@link EntityEditorView}. An enabled {@link ToggleProperty} that adds a command block to a menu that has none
 * or clears it, the command name / permission / deny-message / usage as {@link TextProperty} anvils, the aliases as a
 * {@link ListProperty}, and a console {@link ToggleProperty}, so no raw Bukkit inventory is built and the sub-editor
 * stays on the menu engine like every other editor surface.
 *
 * <p>Every field reads the menu's command block fresh from the {@link MenuEditSession} and writes back through
 * {@link MenuEditSession#setCommand}. A menu that declares no command reads as "disabled" with empty fields; editing
 * the name (or flipping the enabled toggle on) creates a block seeded from the menu id, and the enabled toggle off
 * clears it. Back returns to the menu-property editor, which the row's caller supplies as {@code onBack}. A saved
 * command block lands in the file on the property editor's Save; because Brigadier registers commands only at startup,
 * a newly added or renamed open command needs a server restart to become live, exactly as elsewhere.
 */
@NullMarked
public final class MenuCommandEditorView {

    private static final String MODULE = "custommenus";
    private static final String LAYOUT = "menu-command-editor";
    private static final String TEXT_INPUT_KEY = "editor.text-field";
    private static final String LIST_INPUT_KEY = "editor.list-entry";

    /** A valid Brigadier command literal, mirrored from {@link OpenCommandSpec}: lowercase word, no whitespace. */
    private static final Pattern COMMAND_WORD = Pattern.compile("[a-z0-9_-]+");

    /** The seven field slots, in the order {@link #properties} builds them; a three-row chest with room to spare. */
    private static final List<Integer> PROPERTY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16);

    private static final int BACK_SLOT = 22;

    private final GuiText guiText;

    /** The colours the engine draws a list window in; asked per render so a theme reload is picked up. */
    private final Supplier<Theme> theme;

    private final Scheduler scheduler;
    private final Messages messages;
    private final TextInput textInput;
    private final ListPropertyLayout aliasLayout;
    private final EntityEditorView<CommandTarget> view;

    public MenuCommandEditorView(
            Menus menus,
            GuiText guiText,
            Supplier<Theme> theme,
            Scheduler scheduler,
            Messages messages,
            TextInput textInput,
            GuiLayouts guiLayouts,
            BiConsumer<Player, PlayerRef> onBack) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(onBack, "onBack");
        this.aliasLayout = defaultAliasLayout();
        EntityEditorLayout layout = guiLayouts.loadEntityEditor(MODULE, LAYOUT, codeDefault());
        this.view = EntityEditorView.<CommandTarget>builder()
                .menus(menus)
                .guiText(guiText)
                .layout(layout)
                .title(this::title)
                .valueLore(CustomMenusMessageKey.MENU_PROPERTIES_VALUE_LORE.key())
                .backName(CustomMenusMessageKey.MENU_COMMAND_BACK.key())
                .properties(this::properties)
                .onBack(player -> onBack.accept(player, BukkitRefs.toRef(player)))
                .build();
    }

    /** Open the command sub-editor for the menu {@code menuId} in {@code session}, on the viewer's entity thread. */
    void open(Player player, PlayerRef viewer, MenuEditSession session, String menuId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(menuId, "menuId");
        view.open(player, new CommandTarget(session, menuId));
    }

    /** The property drawn at {@code slot}, exposed so a test can resolve it without firing a click. */
    Optional<EditableProperty> propertyAt(int slot, MenuEditSession session, String menuId) {
        return view.propertyAt(slot, new CommandTarget(session, menuId));
    }

    private Component title(Player viewer, CommandTarget target) {
        return guiText.text(viewer, CustomMenusMessageKey.MENU_COMMAND_TITLE.key(), Map.of("name", target.menuId()));
    }

    private List<EditableProperty> properties(CommandTarget target) {
        return List.of(
                enabledRow(target),
                nameRow(target),
                aliasesRow(target),
                permissionRow(target),
                denyMessageRow(target),
                consoleRow(target),
                usageRow(target));
    }

    // --- rows -----------------------------------------------------------------------------------------------------

    private EditableProperty enabledRow(CommandTarget target) {
        return ToggleProperty.ofBoolean(
                CustomMenusMessageKey.MENU_COMMAND_ENABLED.key(),
                Material.COMMAND_BLOCK,
                () -> target.session().command().isPresent(),
                this::onOff,
                on -> target.session().setCommand(on ? seed(target) : null),
                scheduler);
    }

    private EditableProperty nameRow(CommandTarget target) {
        return new TextProperty(
                TEXT_INPUT_KEY,
                CustomMenusMessageKey.MENU_COMMAND_NAME.key(),
                CustomMenusMessageKey.MENU_COMMAND_NAME_PROMPT.key(),
                Material.NAME_TAG,
                () -> command(target).map(OpenCommandSpec::name).orElse(""),
                MenuCommandEditorView::validateName,
                value -> apply(target, command -> command.withName(value)),
                textInput::prompt,
                scheduler);
    }

    private EditableProperty aliasesRow(CommandTarget target) {
        return new ListProperty(
                LIST_INPUT_KEY,
                CustomMenusMessageKey.MENU_COMMAND_ALIASES.key(),
                Material.PAPER,
                guiText,
                theme,
                () -> command(target).map(OpenCommandSpec::aliases).orElseGet(List::of),
                aliases -> apply(target, command -> command.withAliases(aliases)),
                new ListPropertyText(
                        CustomMenusMessageKey.MENU_COMMAND_ALIASES_TITLE.key(),
                        CustomMenusMessageKey.MENU_COMMAND_ALIASES_ENTRY_NAME.key(),
                        CustomMenusMessageKey.MENU_COMMAND_ALIASES_ENTRY_HINTS.key(),
                        CustomMenusMessageKey.MENU_COMMAND_ALIASES_ADD.key(),
                        CustomMenusMessageKey.MENU_COMMAND_ALIASES_ADD_PROMPT.key(),
                        CustomMenusMessageKey.MENU_COMMAND_ALIASES_EDIT_PROMPT.key(),
                        CustomMenusMessageKey.MENU_COMMAND_ALIASES_REMOVE_CONFIRM.key(),
                        CustomMenusMessageKey.MENU_COMMAND_ALIASES_BACK.key()),
                aliasLayout,
                textInput::prompt,
                scheduler);
    }

    private EditableProperty permissionRow(CommandTarget target) {
        return optionalTextRow(
                target,
                CustomMenusMessageKey.MENU_COMMAND_PERMISSION,
                CustomMenusMessageKey.MENU_COMMAND_PERMISSION_PROMPT,
                Material.TRIPWIRE_HOOK,
                () -> command(target).flatMap(OpenCommandSpec::permission).orElse(""),
                (command, value) -> command.withPermission(value));
    }

    private EditableProperty denyMessageRow(CommandTarget target) {
        return optionalTextRow(
                target,
                CustomMenusMessageKey.MENU_COMMAND_DENY_MESSAGE,
                CustomMenusMessageKey.MENU_COMMAND_DENY_MESSAGE_PROMPT,
                Material.BARRIER,
                () -> command(target).flatMap(OpenCommandSpec::denyMessage).orElse(""),
                (command, value) -> command.withDenyMessage(value));
    }

    private EditableProperty consoleRow(CommandTarget target) {
        return ToggleProperty.ofBoolean(
                CustomMenusMessageKey.MENU_COMMAND_CONSOLE.key(),
                Material.LEVER,
                () -> command(target).map(OpenCommandSpec::consoleAllowed).orElse(false),
                this::onOff,
                on -> apply(target, command -> command.withConsoleAllowed(on)),
                scheduler);
    }

    private EditableProperty usageRow(CommandTarget target) {
        return optionalTextRow(
                target,
                CustomMenusMessageKey.MENU_COMMAND_USAGE,
                CustomMenusMessageKey.MENU_COMMAND_USAGE_PROMPT,
                Material.WRITABLE_BOOK,
                () -> command(target).flatMap(OpenCommandSpec::usage).orElse(""),
                (command, value) -> command.withUsage(value));
    }

    /** A text field for an optional command field: a blank or clear token empties the field, otherwise it is set. */
    private EditableProperty optionalTextRow(
            CommandTarget target,
            MessageKey label,
            MessageKey prompt,
            Material icon,
            Supplier<String> current,
            BiFunction<OpenCommandSpec, Optional<String>, OpenCommandSpec> set) {
        return new TextProperty(
                TEXT_INPUT_KEY,
                label.key(),
                prompt.key(),
                icon,
                current,
                raw -> Optional.of(raw),
                value -> setOptional(target, value, set),
                textInput::prompt,
                scheduler);
    }

    // --- command mutation helpers ---------------------------------------------------------------------------------

    /** Apply {@code mutation} to the menu's command, creating a default block first when the menu declares none. */
    private void apply(CommandTarget target, UnaryOperator<OpenCommandSpec> mutation) {
        target.session().setCommand(mutation.apply(seed(target)));
    }

    /** Set an optional command field: clearing it on a menu with no command block stays a no-op (adds nothing). */
    private void setOptional(
            CommandTarget target, String raw, BiFunction<OpenCommandSpec, Optional<String>, OpenCommandSpec> set) {
        Optional<String> value = isClear(raw) ? Optional.empty() : Optional.of(raw.strip());
        if (value.isEmpty() && command(target).isEmpty()) {
            return;
        }
        target.session().setCommand(set.apply(seed(target), value));
    }

    /** The menu's current command block, or a default one named after the menu when it declares none. */
    private static OpenCommandSpec seed(CommandTarget target) {
        return command(target)
                .orElseGet(() -> new OpenCommandSpec(
                        target.menuId().toLowerCase(Locale.ROOT),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        false));
    }

    private static Optional<OpenCommandSpec> command(CommandTarget target) {
        return target.session().command();
    }

    /** Accept a typed command word (rejecting anything Brigadier could not register), normalised to lowercase. */
    private static Optional<String> validateName(String raw) {
        String normalised = raw.strip().toLowerCase(Locale.ROOT);
        return COMMAND_WORD.matcher(normalised).matches() ? Optional.of(normalised) : Optional.empty();
    }

    private static boolean isClear(String raw) {
        String value = raw.strip();
        return value.isEmpty()
                || value.equals("-")
                || value.equalsIgnoreCase("none")
                || value.equalsIgnoreCase("clear");
    }

    private String onOff(Player viewer, boolean on) {
        return messages.resolve(
                BukkitRefs.toRef(viewer),
                on ? CustomMenusMessageKey.MENU_PROPERTIES_VALUE_ON : CustomMenusMessageKey.MENU_PROPERTIES_VALUE_OFF,
                Map.of());
    }

    private static EntityEditorLayout codeDefault() {
        return new EntityEditorLayout(
                3,
                PROPERTY_SLOTS,
                BACK_SLOT,
                OptionalInt.empty(),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    private static ListPropertyLayout defaultAliasLayout() {
        return new ListPropertyLayout(
                6,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
                48,
                50,
                Material.PAPER,
                Material.LIME_DYE,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /** The subject a command-editor open carries: the menu's working copy and the id of the menu being edited. */
    private record CommandTarget(MenuEditSession session, String menuId) {
        private CommandTarget {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(menuId, "menuId");
        }
    }
}
