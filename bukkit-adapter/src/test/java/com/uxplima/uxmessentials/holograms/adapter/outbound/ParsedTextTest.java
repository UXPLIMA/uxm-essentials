package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.junit.jupiter.api.Test;

class ParsedTextTest {

    private final AtomicInteger parses = new AtomicInteger();
    private final ParsedText parsed = new ParsedText(MiniMessage.builder()
            .preProcessor(source -> {
                parses.incrementAndGet();
                return source;
            })
            .build());

    @Test
    void wordsManyViewersReadAlikeAreParsedOnce() {
        Component first = parsed.parse("<gold>Top</gold> <head:Steve> Steve", TagResolver.empty());
        for (int viewer = 0; viewer < 99; viewer++) {
            assertThat(parsed.parse("<gold>Top</gold> <head:Steve> Steve", TagResolver.empty()))
                    .isEqualTo(first);
        }

        assertThat(parses).hasValue(1);
    }

    @Test
    void differentWordsAreEachParsed() {
        Component steve = parsed.parse("<gold>Steve", TagResolver.empty());
        Component alex = parsed.parse("<gold>Alex", TagResolver.empty());

        assertThat(parses).hasValue(2);
        assertThat(steve).isNotEqualTo(alex);
    }

    @Test
    void tagsThatMayReadLiveStateAreParsedEveryTime() {
        TagResolver online = Placeholder.unparsed("online", "12");

        parsed.parse("Online: <online>", online);
        parsed.parse("Online: <online>", online);

        assertThat(parses).hasValue(2);
    }

    @Test
    void theStoreStartsAgainOnceFull() {
        parsed.parse("first", TagResolver.empty());
        for (int i = 0; i < ParsedText.LIMIT; i++) {
            parsed.parse("value " + i, TagResolver.empty());
        }
        parses.set(0);

        parsed.parse("first", TagResolver.empty());

        assertThat(parses).hasValue(1);
    }
}
