package com.uxplima.uxmessentials.security.adapter;

import java.text.Normalizer;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockMenuChoice;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.gui.input.InputMode;
import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.gui.input.InputSettings;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The text-input seam for the security prompts, which carry a PIN or an authenticator code and so must never be typed
 * into chat.
 *
 * <p>A chat line is not private. The capture that takes a prompt's answer listens to Paper's chat event, but when any
 * plugin listens to the legacy chat event Paper raises that one first, so a chat logger or a Discord bridge would see
 * the PIN before the capture could cancel it. So for these input points the operator's {@code chat} mode is refused:
 * the prompt opens as an anvil instead and the refusal is logged once. Every other mode, and the native form a
 * Bedrock player is shown, goes through the shared {@link TextInput} unchanged.
 */
@NullMarked
public final class SecureEntryInput {

    private static final Material PROMPT_ITEM = Material.PAPER;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final TextInput textInput;
    private final InputSettings settings;
    private final AnvilInput anvil;
    private final GuiText guiText;
    private final BedrockDetector bedrock;
    private final BedrockScreen bedrockScreen;
    private final BedrockMenuChoice menuChoice = BedrockMenuChoice.onServer();
    private final Scheduler scheduler;
    private final Logger log;
    private final AtomicBoolean chatRefusalLogged = new AtomicBoolean();

    public SecureEntryInput(
            TextInput textInput,
            InputSettings settings,
            AnvilInput anvil,
            GuiText guiText,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen,
            Scheduler scheduler,
            Logger log) {
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.bedrockScreen = Objects.requireNonNull(bedrockScreen, "bedrockScreen");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The digits a player meant, from what a text field handed back. A phone keyboard or an input method can produce
     * fullwidth digits, which compatibility normalisation turns into the ASCII ones a keypad would have entered, and an
     * authenticator app shows its code in two groups, so whitespace goes too. Anything else is left as typed, so a
     * digit from another script is still not an ASCII digit and is refused or fails like any wrong entry.
     */
    public static String normalise(String typed) {
        Objects.requireNonNull(typed, "typed");
        return WHITESPACE
                .matcher(Normalizer.normalize(typed, Normalizer.Form.NFKC))
                .replaceAll("");
    }

    /** Whether {@code player} connected from a Bedrock client, through Floodgate or Geyser alone. */
    public boolean isBedrock(Player player) {
        return bedrock.isBedrock(Objects.requireNonNull(player, "player").getUniqueId());
    }

    /**
     * Ask {@code player} for one line at the input point {@code key}, labelled with the catalog key {@code label}.
     * Exactly one of {@code onSubmit} (with the typed line) or {@code onCancel} runs, on the player's own thread.
     */
    public void prompt(
            Player player, PlayerRef viewer, String key, String label, Consumer<String> onSubmit, Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        if (showsForm(player) || settings.modeFor(key) != InputMode.CHAT) {
            textInput.prompt(player, InputRequest.of(key, label), onSubmit, onCancel);
            return;
        }
        if (chatRefusalLogged.compareAndSet(false, true)) {
            log.warn(
                    "event=security_input_chat_refused key={} fallback=anvil reason=a pin or code typed into chat can"
                            + " reach chat loggers before it is captured",
                    key);
        }
        Component prompt = guiText.textUnprefixed(player, label, Map.of());
        ItemStack promptItem = ItemBuilder.of(PROMPT_ITEM).name(prompt).build();
        anvil.open(
                player,
                promptItem,
                result -> scheduler.onEntity(viewer, () -> settle(player, result, onSubmit, onCancel)));
    }

    /** The anvil's answer, under the same cancel words and the same "cancelled" line the shared seam uses. */
    private void settle(Player player, AnvilResult result, Consumer<String> onSubmit, Runnable onCancel) {
        if (result instanceof AnvilResult.Submitted submitted && !settings.isCancel(submitted.text())) {
            onSubmit.accept(submitted.text());
            return;
        }
        player.sendMessage(guiText.text(player, TextInput.CANCELLED_KEY));
        onCancel.run();
    }

    /** Whether the shared seam would show {@code player} a native form, the one prompt that ignores the mode. */
    private boolean showsForm(Player player) {
        return bedrockScreen.available() && isBedrock(player) && !menuChoice.wantsChest(player);
    }
}
