package com.uxplima.uxmessentials.guard.domain.engine;

import java.util.Comparator;

/**
 * The order overlapping regions are asked in: higher priority first; at equal priority the smaller region first,
 * so a region inside another wins without anyone writing a priority; then by id so the order never depends on
 * load order.
 */
final class Precedence {

    private Precedence() {}

    static final Comparator<CompiledRegion> ORDER = Comparator
            .comparingInt((CompiledRegion r) -> -r.region().priority())
            .thenComparingLong(r -> r.region().shape().volume())
            .thenComparing(r -> r.region().id());
}
