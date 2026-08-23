package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.packet.scoreboard.ScoreboardNumberFormat;

/** The complete immutable sidebar state desired for one viewer at one render revision. */
record ScoreboardFrame(Component title, List<Line> lines) {

    ScoreboardFrame {
        Objects.requireNonNull(title, "title");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.size() > 15) {
            throw new IllegalArgumentException("a rendered sidebar frame accepts at most 15 lines");
        }
    }

    record Line(String id, Component text, ScoreboardNumberFormat numberFormat) {
        Line {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(numberFormat, "numberFormat");
        }

        String holder() {
            return "uxm:" + id;
        }
    }
}
