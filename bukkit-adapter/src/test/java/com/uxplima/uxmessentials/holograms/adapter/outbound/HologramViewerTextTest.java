package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.outbound.board.BoardHeads;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class HologramViewerTextTest {

    private static final WorldRef WORLD = new WorldRef(new UUID(0L, 1L), "world");

    private final HologramViewerText text = new HologramViewerText(
            viewer -> UnaryOperator.identity(),
            MiniMessage.miniMessage(),
            TagResolver::empty,
            new BoardHeads(),
            new HologramPageState());

    @Test
    void theReadersOwnHeadIsReadForEachViewer() {
        assertThat(text.hasPerViewerText(board("<uxm_essentials_head> <gold>You")))
                .isTrue();
    }

    @Test
    void aPlaceOnABoardReadsTheSameForEverybody() {
        assertThat(text.hasPerViewerText(board("<uxm_essentials_top_playtime_weekly_1_head> First")))
                .isFalse();
    }

    @Test
    void aHeadTheBoardsDoNotAnswerYetIsLeftAsWritten() {
        Hologram top = board("<uxm_essentials_top_playtime_weekly_1_head> First");

        assertThat(PlainTextComponentSerializer.plainText()
                        .serialize(text.sharedText(top, UnaryOperator.identity(), 0)))
                .isEqualTo("<uxm_essentials_top_playtime_weekly_1_head> First");
    }

    private static Hologram board(String line) {
        return Hologram.create(
                HologramName.of("board"),
                Position.of(WORLD, 0, 70, 0),
                List.of(new HologramLine(line)),
                Instant.parse("2026-11-18T12:00:00Z"));
    }
}
