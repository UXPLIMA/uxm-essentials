package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VisitSummaryTest {

    @Test
    void emptyHasNoVisits() {
        VisitSummary summary = VisitSummary.empty();
        assertThat(summary.count()).isZero();
        assertThat(summary.uniqueVisitors()).isZero();
    }

    @Test
    void rejectsNegativeCount() {
        assertThatThrownBy(() -> new VisitSummary(-1L, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeUniqueVisitors() {
        assertThatThrownBy(() -> new VisitSummary(0L, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incrementedAdvancesTheTotalButLeavesUniqueVisitorsUntouched() {
        VisitSummary after = new VisitSummary(10L, 4).incremented();
        assertThat(after.count()).isEqualTo(11L);
        assertThat(after.uniqueVisitors()).isEqualTo(4);
    }

    @Test
    void incrementedFromEmptyRecordsTheFirstRawVisit() {
        assertThat(VisitSummary.empty().incremented().count()).isEqualTo(1L);
    }
}
