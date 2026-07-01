package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
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
            Player player, String title, @Nullable String content, List<String> buttons, IntConsumer onSelect) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(buttons, "buttons");
        Objects.requireNonNull(onSelect, "onSelect");
        try {
            SimpleForm.Builder builder = SimpleForm.builder().title(title);
            if (content != null) {
                builder.content(content);
            }
            for (String button : buttons) {
                builder.button(button);
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
}
