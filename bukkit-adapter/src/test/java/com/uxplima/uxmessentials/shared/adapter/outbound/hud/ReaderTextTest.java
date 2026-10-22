package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * A line by a name in the tab list or over a head is read by every other player. These tests prove which lines read
 * differently per reader, and that a rank written as a catalogue line and a relational placeholder are filled for the
 * reader the line is drawn for.
 */
class ReaderTextTest {

    private static final String OWNER_LINE = "@custom.rank.owner";

    private final Map<UUID, ReaderText.Affix> affixes = new HashMap<>();
    private final UUID turk = UUID.randomUUID();

    @Test
    void aLineDependsOnItsReaderOnlyWhenItOrAnAffixItShowsNamesACatalogueLineOrItIsRelational() {
        UUID owner = UUID.randomUUID();
        UUID plain = UUID.randomUUID();
        affixes.put(owner, new ReaderText.Affix(OWNER_LINE, ""));
        affixes.put(plain, new ReaderText.Affix("<red>[VIP] ", ""));
        ReaderText text = text();

        assertThat(text.dependsOnReader(owner, "{prefix}{player}")).isTrue();
        assertThat(text.dependsOnReader(owner, "{player}")).isFalse();
        assertThat(text.dependsOnReader(plain, "{prefix}{player}")).isFalse();
        assertThat(text.dependsOnReader(plain, "@custom.tab.name")).isTrue();
        assertThat(text.dependsOnReader(plain, "%rel_uxmtags_tag%{player}")).isTrue();
    }

    @Test
    void theAffixTokensReadInTheReadersLanguage() {
        UUID owner = UUID.randomUUID();
        UUID vip = UUID.randomUUID();
        affixes.put(owner, new ReaderText.Affix(OWNER_LINE, " *"));
        affixes.put(vip, new ReaderText.Affix("<red>[VIP]</red> ", ""));
        ReaderText text = text();

        assertThat(text.forReader(owner, "{prefix}{player}{suffix}", turk)).isEqualTo("[KURUCU] {player} *");
        assertThat(text.forReader(owner, "{prefix}{player}", PlayerRef.SYSTEM_UUID))
                .isEqualTo("[OWNER] {player}");
        assertThat(text.forReader(vip, "{prefix}{player}", turk)).isEqualTo("<red>[VIP]</red> {player}");
    }

    @Test
    void aRelationalPlaceholderIsFilledForTheReaderAndTheServersCopyReadsAsThePlayerWould() {
        UUID wearer = UUID.randomUUID();
        ReaderText text = text();

        assertThat(text.forReader(wearer, "%rel_tag%{player}", turk)).isEqualTo("[EFSANE] {player}");
        assertThat(text.forReader(wearer, "%rel_tag%{player}", PlayerRef.SYSTEM_UUID))
                .isEqualTo("[LEGEND] {player}");
    }

    private ReaderText text() {
        return new ReaderText(
                line -> line.startsWith("@"),
                (reader, line) -> line.equals(OWNER_LINE) ? (reader.equals(turk) ? "[KURUCU] " : "[OWNER] ") : line,
                id -> affixes.getOrDefault(id, ReaderText.Affix.NONE),
                (reader, target) -> line -> line.replace("%rel_tag%", reader.equals(turk) ? "[EFSANE] " : "[LEGEND] "));
    }
}
