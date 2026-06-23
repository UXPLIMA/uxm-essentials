package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A reusable duration step for the timed sanction flows ({@code /tempban}, {@code /tempmute}): a fixed grid of
 * preset duration buttons (each labelled with its own {@code SanctionDuration}-grammar string, e.g. {@code 30m},
 * {@code 1h}, {@code 7d}) plus a "custom" button that opens a vanilla anvil for a typed span. Clicking a preset,
 * or submitting a string the supplied validator accepts, fires the caller's {@code onPick} with that exact
 * duration string; a malformed typed string replies with the caller's reject {@link MessageKey} and reopens the
 * anvil.
 *
 * <p>The view holds no sanction logic. One instance is shared across callers — the framework collaborators
 * (text, scheduler, anvil, sink, messages) live on the instance, and the per-use parts (the title, the preset
 * list, the pick callback, the reject key, and whether a permanent parse is allowed) are passed to {@link #open}
 * through a {@link Request}. The typed string is validated through {@link Request#validator}, which a caller
 * backs with {@code SanctionDuration.parse} so the moderation context owns its own grammar; this class only
 * distinguishes "accepted" from "rejected".
 *
 * <p>Folia: the menu is built and opened on the viewer's own entity region thread (where its clicks also run),
 * matching {@link PlayerPickerView}. No roster is read, so there is no global-thread hop. The validator is pure
 * and runs inline on the click thread.
 */
@NullMarked
public final class DurationPickerView {

    private static final int ROWS = 3;
    private static final String INPUT_KEY = "picker.duration";
    private static final int CUSTOM_SLOT = 22;
    private static final int BACK_SLOT = 18;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material PRESET_ICON = Material.CLOCK;
    private static final Material CUSTOM_ICON = Material.WRITABLE_BOOK;
    private static final Material BACK_ICON = Material.ARROW;

    // The default preset ladder offered when a caller does not supply its own — a sensible spread from a short
    // cooldown to a month, all valid SanctionDuration grammar.
    private static final List<String> DEFAULT_PRESETS =
            List.of("30m", "1h", "6h", "12h", "1d", "3d", "7d", "14d", "30d");

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final Messages messages;
    private final MessageSink sink;

    public DurationPickerView(
            GuiText guiText, Scheduler scheduler, TextInput textInput, Messages messages, MessageSink sink) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** The sensible default preset ladder, exposed so a caller can offer it without re-listing the spans. */
    public static List<String> defaultPresets() {
        return DEFAULT_PRESETS;
    }

    /** Open the picker for {@code viewer}; a preset click or an accepted typed span fires {@code request.onPick}. */
    public void open(Player viewer, PlayerRef viewerRef, Request request) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(request, "request");
        scheduler.onEntity(viewerRef, () -> build(viewer, viewerRef, request).open(viewer));
    }

    private SimpleGui build(Player viewer, PlayerRef viewerRef, Request request) {
        SimpleGui gui = Guis.gui()
                .title(guiText.text(viewerRef, request.title()))
                .rows(ROWS)
                .build();
        fill(gui);
        List<String> presets = request.presets();
        for (int i = 0; i < presets.size() && i < CUSTOM_SLOT; i++) {
            String preset = presets.get(i);
            gui.set(
                    presetSlot(i),
                    GuiItem.button(
                            presetIcon(viewerRef, preset), e -> request.onPick().accept(preset)));
        }
        gui.set(CUSTOM_SLOT, GuiItem.button(customIcon(viewerRef), e -> promptCustom(viewer, viewerRef, request)));
        request.onBack().ifPresent(back -> gui.set(BACK_SLOT, GuiItem.button(backIcon(viewerRef), e -> back.run())));
        return gui;
    }

    /** Open the input prompt for a typed span; a submission flows through {@link #resolveTyped}. */
    private void promptCustom(Player viewer, PlayerRef viewerRef, Request request) {
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of(INPUT_KEY, GuiMessageKey.DURATION_PICKER_CUSTOM_PROMPT),
                text -> resolveTyped(viewer, viewerRef, request, text),
                () -> open(viewer, viewerRef, request));
    }

    /**
     * Validate the typed span on the viewer's entity thread: a rejected string replies with the reject key and
     * reopens the anvil, an accepted one fires the pick callback with the trimmed text. Package-private so the
     * accept/reject branches are unit-tested without driving a live anvil.
     */
    void resolveTyped(Player viewer, PlayerRef viewerRef, Request request, String input) {
        String trimmed = input.strip();
        if (!request.validator().apply(trimmed)) {
            sink.deliver(viewerRef, messages.resolve(viewerRef, request.rejectKey(), Map.of("input", trimmed)));
            promptCustom(viewer, viewerRef, request);
            return;
        }
        request.onPick().accept(trimmed);
    }

    private ItemStack presetIcon(PlayerRef viewer, String preset) {
        return ItemBuilder.of(PRESET_ICON)
                .name(guiText.text(viewer, GuiMessageKey.DURATION_PICKER_PRESET_NAME, Map.of("duration", preset)))
                .lore(List.of(guiText.text(viewer, GuiMessageKey.DURATION_PICKER_PRESET_LORE)))
                .build();
    }

    private ItemStack customIcon(PlayerRef viewer) {
        return ItemBuilder.of(CUSTOM_ICON)
                .name(guiText.text(viewer, GuiMessageKey.DURATION_PICKER_CUSTOM))
                .lore(List.of(guiText.text(viewer, GuiMessageKey.DURATION_PICKER_CUSTOM_LORE)))
                .build();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(BACK_ICON)
                .name(guiText.text(viewer, GuiMessageKey.DURATION_PICKER_BACK))
                .build();
    }

    // The presets occupy the centre of the top two rows; the bottom row carries the custom and back buttons.
    private static int presetSlot(int index) {
        return index + (index / 7) * 2 + 1;
    }

    private void fill(SimpleGui gui) {
        ItemStack filler = ItemBuilder.of(FILLER).name(Component.empty()).build();
        for (int slot = 0; slot < ROWS * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }

    /**
     * One picker invocation's caller-supplied parts, keeping {@link DurationPickerView} generic over the sanction
     * verb: the menu title, the preset spans offered, the callback fired with the chosen duration string, the
     * validator that decides whether a typed span is acceptable, the reply key for a rejected one, and an optional
     * back action (the previous step in the flow).
     *
     * @param title the menu-title catalog key
     * @param presets the preset duration strings offered as buttons (SanctionDuration grammar)
     * @param onPick invoked with the chosen duration string (a clicked preset, or an accepted typed span)
     * @param validator decides whether a typed span is acceptable for this verb (e.g. rejecting a permanent parse
     *     for {@code /tempban}); a caller backs it with {@code SanctionDuration.parse}
     * @param rejectKey the reply key for a rejected typed span (filled with {@code {input}})
     * @param onBack the action the back button runs, or empty for no back button
     */
    public record Request(
            MessageKey title,
            List<String> presets,
            Consumer<String> onPick,
            Function<String, Boolean> validator,
            MessageKey rejectKey,
            java.util.Optional<Runnable> onBack) {

        public Request {
            Objects.requireNonNull(title, "title");
            presets = List.copyOf(Objects.requireNonNull(presets, "presets"));
            Objects.requireNonNull(onPick, "onPick");
            Objects.requireNonNull(validator, "validator");
            Objects.requireNonNull(rejectKey, "rejectKey");
            Objects.requireNonNull(onBack, "onBack");
        }
    }
}
