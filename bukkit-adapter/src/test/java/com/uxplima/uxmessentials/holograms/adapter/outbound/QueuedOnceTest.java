package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class QueuedOnceTest {

    private final Queue<Runnable> thread = new ArrayDeque<>();
    private final AtomicInteger redraws = new AtomicInteger();
    private final QueuedOnce redraw = new QueuedOnce(thread::add, redraws::incrementAndGet);

    private void runQueued() {
        while (!thread.isEmpty()) {
            thread.poll().run();
        }
    }

    @Test
    void aHundredRequestsBeforeTheRunAreServedByOneRedraw() {
        for (int viewer = 0; viewer < 100; viewer++) {
            redraw.request();
        }

        assertThat(thread).hasSize(1);
        runQueued();
        assertThat(redraws).hasValue(1);
    }

    @Test
    void aRequestAfterTheRunQueuesAnotherRedraw() {
        redraw.request();
        runQueued();

        redraw.request();
        runQueued();

        assertThat(redraws).hasValue(2);
    }

    @Test
    void aRequestMadeDuringTheRunIsNotLost() {
        QueuedOnce[] self = new QueuedOnce[1];
        self[0] = new QueuedOnce(thread::add, () -> {
            if (redraws.incrementAndGet() == 1) {
                self[0].request();
            }
        });

        self[0].request();
        runQueued();

        assertThat(redraws).hasValue(2);
    }
}
