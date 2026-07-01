package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * Sends a Bedrock (Floodgate) player a native Cumulus form in place of a chest menu. The hybrid renderer builds a
 * SimpleForm from a menu's visible items and hands it here; the SDK detail of turning that into a Cumulus form and
 * routing the tap back lives behind this interface, so the engine never names Cumulus or Floodgate.
 *
 * <p>The Java-only default is {@link #NONE}: it does nothing and carries no SDK reference, so a server without
 * Floodgate never touches Cumulus. When Floodgate is installed the {@link #forServer(Server)} factory returns the
 * Cumulus-backed screen instead — that concrete class is the only place the {@code org.geysermc} SDK is named, and
 * the factory constructs it strictly behind the {@code isPluginEnabled("floodgate")} guard, so it (and the SDK it
 * references) never loads on a Java-only server. The engine only ever reaches this after a
 * {@link BedrockDetector} has confirmed the viewer is a Bedrock player, so {@link #NONE} is never actually sent a
 * form.
 */
public interface BedrockScreen {

    /** The Java-only default: sending any form is a no-op, and no {@code org.geysermc} class is referenced. */
    BedrockScreen NONE = new BedrockScreen() {
        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {}

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {}

        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {}
    };

    /**
     * Send {@code player} a SimpleForm — a titled list of buttons — and route the tapped button back through
     * {@code onSelect}. Each button carries a label and an optional icon ({@link BedrockButton}); the icon becomes the
     * button's image when present, else the button is text-only. The {@code onSelect} callback is invoked with the
     * zero-based index of the tapped button, an index into {@code buttons}; the caller is responsible for hopping onto
     * the viewer's entity thread inside it, because a Cumulus response arrives off the main thread.
     *
     * @param player the Bedrock viewer to send the form to; never {@code null}
     * @param title the form title as plain text; never {@code null}
     * @param content optional body text shown above the buttons, or {@code null} for none
     * @param buttons the buttons in order, each a label plus an optional icon; never {@code null}
     * @param onSelect invoked with the tapped button's index when the viewer taps one; never {@code null}
     */
    void sendSimpleForm(
            Player player, String title, @Nullable String content, List<BedrockButton> buttons, IntConsumer onSelect);

    /**
     * Send {@code player} a ModalForm — a titled window with exactly two buttons — running {@code onButton1} when the
     * first button is tapped and {@code onButton2} when the second is. The engine's two-button confirm window renders
     * here for a Bedrock viewer instead of the confirm chest. The callbacks fire off the main thread when the viewer
     * responds, so the caller wraps each in its own entity-thread hop before it touches the world.
     *
     * @param player the Bedrock viewer to send the form to; never {@code null}
     * @param title the form title as plain text; never {@code null}
     * @param content optional body text shown above the buttons, or {@code null} for none
     * @param button1 the first (confirm) button's label; never {@code null}
     * @param button2 the second (cancel) button's label; never {@code null}
     * @param onButton1 invoked when the viewer taps the first button; never {@code null}
     * @param onButton2 invoked when the viewer taps the second button; never {@code null}
     */
    void sendModalForm(
            Player player,
            String title,
            @Nullable String content,
            String button1,
            String button2,
            Runnable onButton1,
            Runnable onButton2);

    /**
     * Send {@code player} a CustomForm carrying a single text input, running {@code onSubmit} with the typed value on
     * submit and {@code onClose} when the viewer closes or dismisses the form without submitting. The engine's
     * text-input prompt renders here for a Bedrock viewer instead of the anvil or chat prompt a Java player sees. Like
     * the other forms, both callbacks fire off the main thread when the viewer responds, so the caller wraps each in
     * its own entity-thread hop before it touches the world.
     *
     * @param player the Bedrock viewer to send the form to; never {@code null}
     * @param title the form title as plain text; never {@code null}
     * @param inputLabel the label shown above the input field as plain text; never {@code null}
     * @param initial the field's pre-fill, or {@code null} for an empty field
     * @param onSubmit invoked with the typed value when the viewer submits; never {@code null}
     * @param onClose invoked when the viewer closes or dismisses the form without submitting; never {@code null}
     */
    void sendInputForm(
            Player player,
            String title,
            String inputLabel,
            @Nullable String initial,
            Consumer<String> onSubmit,
            Runnable onClose);

    /**
     * Selects the screen for this server: the Cumulus-backed one when the {@code floodgate} plugin is enabled,
     * otherwise {@link #NONE}. The Cumulus-backed screen is named only inside the enabled branch, so on a server
     * without Floodgate its class — and the {@code org.geysermc} SDK it references — is never loaded.
     *
     * @param server the running server, used only to probe plugin presence; never {@code null}
     * @return a Cumulus-backed screen when Floodgate is enabled, else {@link #NONE}
     */
    static BedrockScreen forServer(Server server) {
        Objects.requireNonNull(server, "server");
        if (server.getPluginManager().isPluginEnabled("floodgate")) {
            return new CumulusBedrockScreen();
        }
        return NONE;
    }
}
