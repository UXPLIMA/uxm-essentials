package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.LayoutFit;
import org.junit.jupiter.api.Test;

/**
 * Four windows in the engine paint a list into configured slots and stop at the shorter of the two, which is all
 * they can do. None of them used to say so, so an operator who sized a layout too small saw a window that opened
 * and looked deliberate, with the tail absent, which is indistinguishable from having edited the wrong file.
 */
class LayoutFitTest {

    @Test
    void aLayoutWithRoomForEverythingDrawsEverythingAndSaysNothing() {
        List<LogRecord> logged = new ArrayList<>();

        int drawn = withCapture(logged, () -> LayoutFit.drawable("fits", 3, List.of(1, 2, 3)));

        assertThat(drawn).isEqualTo(3);
        assertThat(logged).isEmpty();
    }

    @Test
    void aLayoutWithSpareSlotsDrawsOnlyWhatThereIsAndSaysNothing() {
        // More slots than things is not a mistake: the tail is simply unused.
        List<LogRecord> logged = new ArrayList<>();

        int drawn = withCapture(logged, () -> LayoutFit.drawable("spare", 2, List.of(1, 2, 3, 4)));

        assertThat(drawn).isEqualTo(2);
        assertThat(logged).isEmpty();
    }

    @Test
    void aLayoutTooSmallDrawsWhatItCanAndNamesTheKeyOnce() {
        List<LogRecord> logged = new ArrayList<>();

        int drawn = withCapture(logged, () -> {
            LayoutFit.drawable("too-small-slots", 8, List.of(1, 2, 3));
            LayoutFit.drawable("too-small-slots", 8, List.of(1, 2, 3));
            return LayoutFit.drawable("too-small-slots", 8, List.of(1, 2, 3));
        });

        assertThat(drawn).isEqualTo(3);
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getMessage())
                .contains("too-small-slots")
                .contains("needs=8")
                .contains("slots=3");
    }

    @Test
    void aDifferentShortfallOnTheSameKeyIsReportedAgain() {
        // Keyed by the counts, not by the caller: the property objects these run inside are rebuilt on every
        // redraw, so keying on an object would report every open, and keying on the name alone would hide an
        // operator making the layout smaller still.
        List<LogRecord> logged = new ArrayList<>();

        withCapture(logged, () -> {
            LayoutFit.drawable("changing-slots", 8, List.of(1, 2, 3));
            return LayoutFit.drawable("changing-slots", 8, List.of(1, 2));
        });

        assertThat(logged).hasSize(2);
    }

    private static int withCapture(List<LogRecord> into, java.util.function.IntSupplier body) {
        Logger logger = Logger.getLogger(LayoutFit.class.getName());
        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    into.add(record);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        boolean parents = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(collector);
        try {
            return body.getAsInt();
        } finally {
            logger.removeHandler(collector);
            logger.setUseParentHandlers(parents);
        }
    }
}
