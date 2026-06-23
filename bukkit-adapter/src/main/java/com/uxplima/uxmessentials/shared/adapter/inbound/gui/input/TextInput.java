package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The one entry point for capturing a line of text from a player, whether through an anvil or through chat. A call
 * site hands a {@link InputRequest} (a stable key, a prompt label, an optional anvil pre-fill) plus a submit and a
 * cancel callback; the seam reads the operator's per-key mode from {@link InputSettings}, opens the matching backend,
 * and routes the result. This replaces the two split mechanisms — uxmLib's {@code AnvilInput} and the per-context chat
 * listeners — so every input point is configurable as anvil or chat without the call site knowing which ran.
 *
 * <p><b>Cancel policy (one place).</b> Both backends report a raw {@link InputResult}; the cancel-keyword check lives
 * here. A structural cancel (anvil closed) and a {@code Submitted} line that matches a configured cancel keyword both
 * resolve to a cancellation: the viewer is sent the {@code gui.input.cancelled} acknowledgement and {@code onCancel}
 * runs (reopening the prior menu, as before). Any other line runs {@code onSubmit} with the typed text.
 *
 * <p><b>Folia.</b> The backend may report on an async thread (chat) or the region thread (anvil); the seam hops both
 * the submit and the cancel branch onto the viewer's entity region before the callback runs, so a call site's callback
 * always executes where it can safely touch the player and reopen a GUI. The call site no longer hops for itself.
 */
@NullMarked
public final class TextInput {

    private final InputSettings settings;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final AnvilTextBackend anvilBackend;
    private final ChatTextBackend chatBackend;

    public TextInput(
            InputSettings settings,
            GuiText guiText,
            Scheduler scheduler,
            AnvilTextBackend anvilBackend,
            ChatTextBackend chatBackend) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.anvilBackend = Objects.requireNonNull(anvilBackend, "anvilBackend");
        this.chatBackend = Objects.requireNonNull(chatBackend, "chatBackend");
    }

    /**
     * Prompt {@code player} for a line of text per the request, then run exactly one of the callbacks on the viewer's
     * region thread: {@code onSubmit} with the typed line, or {@code onCancel} if they cancelled (closed the anvil or
     * typed a cancel keyword).
     *
     * @param player the live player to prompt
     * @param viewer the viewer reference — locale, identity, and the region the callbacks run on
     * @param request the input point: its key (config lookup), label, and optional pre-fill
     * @param onSubmit receives the accepted line
     * @param onCancel runs on cancellation; typically reopens the menu the player came from
     */
    public void prompt(
            Player player, PlayerRef viewer, InputRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        InputMode mode = settings.modeFor(request.key());
        TextInputBackend backend = mode == InputMode.CHAT ? chatBackend : anvilBackend;
        Component prompt = buildPrompt(viewer, request.label(), request.placeholders(), mode);
        backend.open(
                player,
                viewer,
                prompt,
                request.initialText(),
                result -> scheduler.onEntity(viewer, () -> route(player, viewer, result, onSubmit, onCancel)));
    }

    private Component buildPrompt(
            PlayerRef viewer, MessageKey label, Map<String, String> placeholders, InputMode mode) {
        Component prompt = guiText.text(viewer, label, placeholders);
        if (mode != InputMode.CHAT) {
            return prompt;
        }
        // In chat mode a player cannot see a cancel button, so append how to abort.
        Component hint = guiText.text(
                viewer, GuiMessageKey.INPUT_CANCEL_HINT, Map.of("keyword", settings.primaryCancelKeyword()));
        return prompt.append(Component.space()).append(hint);
    }

    private void route(
            Player player, PlayerRef viewer, InputResult result, Consumer<String> onSubmit, Runnable onCancel) {
        if (result instanceof InputResult.Submitted submitted && !settings.isCancel(submitted.text())) {
            onSubmit.accept(submitted.text());
            return;
        }
        player.sendMessage(guiText.text(viewer, GuiMessageKey.INPUT_CANCELLED));
        onCancel.run();
    }
}
