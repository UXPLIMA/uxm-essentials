package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The browse request value: the {@link WarpQuery#publicBrowse} safe default and the page/pageSize guards. */
class WarpQueryTest {

    private static final UUID VIEWER = UUID.randomUUID();

    @Test
    void publicBrowseIsTheSafeDefault() {
        WarpQuery query = WarpQuery.publicBrowse(VIEWER, WarpSort.VISITS, 2, 45);

        assertThat(query.access()).contains(WarpAccess.PUBLIC);
        assertThat(query.onlyActive()).isTrue();
        assertThat(query.sort()).isEqualTo(WarpSort.VISITS);
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.pageSize()).isEqualTo(45);
        assertThat(query.viewer()).isEqualTo(VIEWER);
        assertThat(query.category()).isEmpty();
        assertThat(query.server()).isEmpty();
        assertThat(query.owner()).isEmpty();
        assertThat(query.search()).isEmpty();
        assertThat(query.favouritesOf()).isEmpty();
        assertThat(query.viewerPosition()).isEmpty();
    }

    @Test
    void pageAndPageSizeAreBounded() {
        assertThatThrownBy(() -> WarpQuery.publicBrowse(VIEWER, WarpSort.NEWEST, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WarpQuery.publicBrowse(VIEWER, WarpSort.NEWEST, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WarpQuery.publicBrowse(VIEWER, WarpSort.NEWEST, 0, WarpQuery.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(WarpQuery.publicBrowse(VIEWER, WarpSort.NEWEST, 0, 1).pageSize())
                .isEqualTo(1);
        assertThat(WarpQuery.publicBrowse(VIEWER, WarpSort.NEWEST, 0, WarpQuery.MAX_PAGE_SIZE)
                        .pageSize())
                .isEqualTo(WarpQuery.MAX_PAGE_SIZE);
    }
}
