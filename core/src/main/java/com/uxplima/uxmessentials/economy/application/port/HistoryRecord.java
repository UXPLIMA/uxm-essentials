package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.time.Instant;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents a historical transaction record retrieved from persistence.
 */
@NullMarked
public record HistoryRecord(
        long id,
        Instant timestamp,
        @Nullable String fromUuid,
        @Nullable String fromName,
        @Nullable String toUuid,
        @Nullable String toName,
        String currency,
        BigDecimal amount,
        String type,
        String reason) {}
