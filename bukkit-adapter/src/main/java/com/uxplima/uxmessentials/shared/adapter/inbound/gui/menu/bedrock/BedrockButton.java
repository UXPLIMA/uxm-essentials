package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * One Bedrock SimpleForm button: its label plus an optional icon. The engine builds these from a menu's items and
 * hands them to the {@link BedrockScreen}, which turns each into a Cumulus button — with the icon when present, a
 * plain text button when {@code image} is {@code null}. Kept SDK-free so the engine never names Cumulus.
 *
 * @param text the button label as plain text; never {@code null}
 * @param image the button's icon, or {@code null} for a text-only button
 */
public record BedrockButton(String text, @Nullable BedrockImage image) {

    public BedrockButton {
        Objects.requireNonNull(text, "text");
    }
}
