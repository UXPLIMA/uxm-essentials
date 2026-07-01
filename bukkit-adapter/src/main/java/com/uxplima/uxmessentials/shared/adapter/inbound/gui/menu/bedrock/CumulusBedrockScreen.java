package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jspecify.annotations.Nullable;

/**
 * The Cumulus-backed {@link BedrockScreen}, delegating to the Cumulus form library and the Floodgate SDK. This is
 * the one class that names {@code org.geysermc.cumulus} and {@code org.geysermc.floodgate}; it is instantiated only
 * by {@code BedrockScreen.forServer} behind the {@code isPluginEnabled("floodgate")} guard, so it never loads on a
 * Java-only server and a {@code NoClassDefFoundError} is impossible there.
 *
 * <p>The SDK calls are wrapped defensively: a Floodgate that is present but mid-reload or otherwise not ready can
 * return a null instance or throw, and a menu open must never fail because the form send hiccuped — so any runtime
 * failure is logged and swallowed rather than thrown into the open path. The valid-result handler routes the tapped
 * button's id back through the supplied callback; it fires off the main thread when the viewer responds, and the
 * callback the engine supplies does its own entity-thread hop before touching anything.
 */
final class CumulusBedrockScreen implements BedrockScreen {

    private static final Logger LOG = Logger.getLogger(CumulusBedrockScreen.class.getName());

    @Override
    public void sendSimpleForm(
            Player player, String title, @Nullable String content, List<BedrockButton> buttons, IntConsumer onSelect) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(buttons, "buttons");
        Objects.requireNonNull(onSelect, "onSelect");
        try {
            SimpleForm.Builder builder = SimpleForm.builder().title(title);
            if (content != null) {
                builder.content(content);
            }
            for (BedrockButton button : buttons) {
                addButton(builder, button);
            }
            builder.validResultHandler((form, response) -> {
                int id = response.clickedButtonId();
                if (id >= 0 && id < buttons.size()) {
                    onSelect.accept(id);
                }
            });
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
        } catch (RuntimeException notReady) {
            LOG.warning("event=bedrock_form_failed player=" + player.getName() + " reason=" + notReady.getMessage());
        }
    }

    /**
     * Add one button to a SimpleForm: with its icon as a {@link FormImage} — a {@link FormImage.Type#PATH} for a
     * texture path, a {@link FormImage.Type#URL} for a web image the client fetches — when the button carries one,
     * else a plain text button. This is the only place the engine's {@link BedrockImage} maps onto the Cumulus type.
     */
    private void addButton(SimpleForm.Builder builder, BedrockButton button) {
        BedrockImage image = button.image();
        if (image != null) {
            FormImage.Type type = image.kind() == BedrockImage.Kind.PATH ? FormImage.Type.PATH : FormImage.Type.URL;
            builder.button(button.text(), type, image.value());
        } else {
            builder.button(button.text());
        }
    }

    @Override
    public void sendModalForm(
            Player player,
            String title,
            @Nullable String content,
            String button1,
            String button2,
            Runnable onButton1,
            Runnable onButton2) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(button1, "button1");
        Objects.requireNonNull(button2, "button2");
        Objects.requireNonNull(onButton1, "onButton1");
        Objects.requireNonNull(onButton2, "onButton2");
        try {
            ModalForm.Builder builder = ModalForm.builder().title(title);
            if (content != null) {
                builder.content(content);
            }
            builder.button1(button1);
            builder.button2(button2);
            builder.validResultHandler(
                    (form, response) -> (response.clickedButtonId() == 0 ? onButton1 : onButton2).run());
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
        } catch (RuntimeException notReady) {
            LOG.warning("event=bedrock_modal_failed player=" + player.getName() + " reason=" + notReady.getMessage());
        }
    }

    @Override
    public void sendInputForm(
            Player player,
            String title,
            String inputLabel,
            @Nullable String initial,
            Consumer<String> onSubmit,
            Runnable onClose) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(inputLabel, "inputLabel");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onClose, "onClose");
        try {
            CustomForm.Builder builder = CustomForm.builder().title(title);
            builder.input(inputLabel, "", initial != null ? initial : "");
            builder.validResultHandler((form, response) -> onSubmit.accept(response.asInput(0)));
            builder.closedOrInvalidResultHandler(onClose);
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
        } catch (RuntimeException notReady) {
            LOG.warning("event=bedrock_input_failed player=" + player.getName() + " reason=" + notReady.getMessage());
        }
    }
}
