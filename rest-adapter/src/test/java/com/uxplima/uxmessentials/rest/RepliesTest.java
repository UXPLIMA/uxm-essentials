package com.uxplima.uxmessentials.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;

import org.junit.jupiter.api.Test;

/** The replies read from the host's catalogue, and in English on a host too old to answer. */
class RepliesTest {

    private final List<String> logged = new ArrayList<>();

    @Test
    void aHostThatAnswersIsAsked() {
        Component line = new Replies(true, recording())
                .line(
                        (key, values) -> Component.text("host:" + key + ":" + values.get("label")),
                        "admin.rest.revoked",
                        Map.of("label", "panel"));

        assertThat(plain(line)).isEqualTo("host:admin.rest.revoked:panel");
        assertThat(logged).isEmpty();
    }

    @Test
    void aHostWithoutTheCallGetsEnglishAndIsNeverAsked() {
        List<String> asked = new ArrayList<>();
        Replies.Host older = (key, values) -> {
            asked.add(key);
            throw new NoSuchMethodError("message");
        };
        Replies replies = new Replies(false, recording());

        Component first = replies.line(older, "admin.rest.revoked", Map.of("label", "panel"));
        Component second = replies.line(older, "admin.rest.not-found", Map.of("label", "gone"));

        assertThat(plain(first)).isEqualTo("Token panel revoked.");
        assertThat(plain(second)).isEqualTo("No token named gone.");
        assertThat(asked).isEmpty();
        assertThat(logged).hasSize(1);
    }

    @Test
    void aFaultInsideAHostThatHasTheCallIsNotTakenForAnOldHost() {
        Replies.Host failing = (key, values) -> {
            throw new NoSuchMethodError("something inside the host");
        };
        Replies replies = new Replies(true, recording());

        assertThatThrownBy(() -> replies.line(failing, "admin.rest.revoked", Map.of("label", "panel")))
                .isInstanceOf(NoSuchMethodError.class);
        assertThat(logged).isEmpty();
    }

    @Test
    void theHostThisBuildsAgainstHasTheCall() {
        assertThat(Replies.hostHasMessages()).isTrue();
    }

    @Test
    void theSecretIsStillShownAndCopyableOnAnOlderHost() {
        Replies.Host older = (key, values) -> {
            throw new AbstractMethodError("message");
        };

        Component secret =
                new Replies(false, recording()).line(older, "admin.rest.secret", Map.of("secret", "uxm_abc"));

        assertThat(plain(secret)).isEqualTo("uxm_abc");
        assertThat(secret.clickEvent()).isEqualTo(ClickEvent.copyToClipboard("uxm_abc"));
    }

    @Test
    void aValueIsShownAsTypedOnAnOlderHost() {
        Replies.Host older = (key, values) -> {
            throw new NoSuchMethodError("message");
        };

        Component line = new Replies(false, recording()).line(older, "admin.rest.exists", Map.of("label", "<red>x"));

        assertThat(plain(line)).isEqualTo("A token named <red>x already exists.");
    }

    private Logger recording() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return log;
    }

    private static String plain(Component component) {
        StringBuilder text = new StringBuilder();
        append(component, text);
        return text.toString();
    }

    private static void append(Component component, StringBuilder text) {
        if (component instanceof TextComponent words) {
            text.append(words.content());
        }
        component.children().forEach(child -> append(child, text));
    }
}
