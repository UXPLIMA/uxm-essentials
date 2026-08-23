package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.util.Objects;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import org.jspecify.annotations.NullMarked;

/**
 * The shared render transform for operator-authored HUD content (scoreboard sidebar lines, tablist header/footer):
 * the per-viewer PlaceholderAPI bridge ({@code %papi%} expansion, identity without PlaceholderAPI) followed by a
 * {@link MiniMessage} parse, the same two-step transform the message sink applies. Operator content may therefore
 * embed third-party placeholders. Every source string is raw operator MiniMessage, never a {@code MessageKey}, so
 * nothing here is parity-checked.
 */
@NullMarked
public final class HudText {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private HudText() {}

    /** Render {@code miniMessageSource} for {@code viewer}: expand its placeholders, then parse the MiniMessage. */
    public static Component render(UUID viewer, String miniMessageSource) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(miniMessageSource, "miniMessageSource");
        String expanded = PlaceholderApiSupport.messageBridge(viewer).apply(miniMessageSource);
        return parse(expanded);
    }

    /** Parse already-expanded operator MiniMessage without running PlaceholderAPI a second time. */
    public static Component parse(String miniMessageSource) {
        Objects.requireNonNull(miniMessageSource, "miniMessageSource");
        return MINI_MESSAGE.deserialize(miniMessageSource, StyleTags.resolver());
    }
}
