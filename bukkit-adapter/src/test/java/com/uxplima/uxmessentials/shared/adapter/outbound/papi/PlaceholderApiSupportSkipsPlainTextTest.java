package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A line with no placeholder in it is not handed to PlaceholderAPI. Every sidebar and tab list line went through it
 * each second for every viewer, and PlaceholderAPI scans each text with a regular expression whether or not a
 * placeholder is there: on a test lobby with thirty players that scan was a visible share of the tick.
 */
class PlaceholderApiSupportSkipsPlainTextTest {

    private final List<String> handed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void plainTextIsReturnedWithoutCallingPlaceholderApi() {
        String plain = "<accent>UXM</accent> <muted>lobby</muted>";

        String out = PlaceholderApiSupport.expand(UUID.randomUUID(), plain, (player, text) -> {
            handed.add(text);
            return text;
        });

        assertThat(out).isSameAs(plain);
        assertThat(handed).isEmpty();
    }

    @Test
    void aPlaceholderIsStillHandedToPlaceholderApi() {
        String line = "<muted>Online:</muted> %server_online%";

        String out = PlaceholderApiSupport.expand(UUID.randomUUID(), line, (player, text) -> {
            handed.add(text);
            return "<muted>Online:</muted> 3";
        });

        assertThat(out).isEqualTo("<muted>Online:</muted> 3");
        assertThat(handed).containsExactly(line);
    }
}
