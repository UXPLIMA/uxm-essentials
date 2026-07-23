package com.uxplima.uxmessentials.security.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.message.HoconLocaleCatalog;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the 2FA enrolment click-to-copy: the {@code security.2fa.setup-secret} and {@code security.2fa.setup-uri}
 * catalog lines wrap the raw value in a copy-to-clipboard click event, so a player enrolling in TOTP can click the
 * secret or the {@code otpauth://} link to put it on their clipboard. The copied payload is the raw value with no
 * colour codes or MiniMessage markup, which is exactly what an authenticator app needs pasted back.
 *
 * <p>The catalog is loaded from the bundled classpath default (an empty on-disk dir), then rendered through the same
 * {@link StyledText}/MiniMessage seam the message sink uses in production, so the assertion sees the click event a
 * real enrolment message carries.
 */
class TwoFactorEnrollmentClickToCopyTest {

    private static final MessageKey SETUP_SECRET = () -> "security.2fa.setup-secret";
    private static final MessageKey SETUP_URI = () -> "security.2fa.setup-uri";

    @Test
    void theEnrolmentSecretComponentCopiesTheRawSecret(@TempDir Path dir) {
        String secret = "JBSWY3DPEHPK3PXP";

        ClickEvent click = copyClick(render(dir, SETUP_SECRET, "secret", secret));

        assertThat(click.action()).isEqualTo(ClickEvent.Action.COPY_TO_CLIPBOARD);
        assertThat(copiedText(click)).isEqualTo(secret);
    }

    @Test
    void theEnrolmentLinkComponentCopiesTheRawOtpauthUri(@TempDir Path dir) {
        String uri = "otpauth://totp/uxmEssentials:Steve?secret=JBSWY3DPEHPK3PXP"
                + "&issuer=uxmEssentials&algorithm=SHA1&digits=6&period=30";

        ClickEvent click = copyClick(render(dir, SETUP_URI, "uri", uri));

        assertThat(click.action()).isEqualTo(ClickEvent.Action.COPY_TO_CLIPBOARD);
        assertThat(copiedText(click)).isEqualTo(uri);
    }

    /** The raw string the click event copies, read through the non-deprecated text payload. */
    private static String copiedText(ClickEvent click) {
        assertThat(click.payload()).isInstanceOf(ClickEvent.Payload.Text.class);
        return ((ClickEvent.Payload.Text) click.payload()).value();
    }

    private static Component render(Path dir, MessageKey key, String placeholder, String value) {
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(mock(Logger.class), dir);
        String template = catalog.template(Locale.ENGLISH, key);
        // Mirror CatalogMessages: literal placeholder substitution before the MiniMessage parse.
        return StyledText.render(template.replace("{" + placeholder + "}", value));
    }

    /** The first copy-to-clipboard click event anywhere in the component tree, or a failed assertion. */
    private static ClickEvent copyClick(Component component) {
        return firstCopyClick(component).orElseThrow(() -> new AssertionError("no copy-to-clipboard click event"));
    }

    private static Optional<ClickEvent> firstCopyClick(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
            return Optional.of(click);
        }
        for (Component child : component.children()) {
            Optional<ClickEvent> found = firstCopyClick(child);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
