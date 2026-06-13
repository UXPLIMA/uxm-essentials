package com.uxplima.uxmessentials.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class VoteSiteCatalogTest {

    private static final VoteSiteSpec SITE_A =
            new VoteSiteSpec("TopVoter", Optional.of("https://topvoter.com"), Duration.ofHours(12));
    private static final VoteSiteSpec SITE_B = new VoteSiteSpec("VoteMC", Optional.empty(), Duration.ofHours(24));

    @Test
    void emptyReturnsNoPresentSite() {
        VoteSiteCatalog catalog = VoteSiteCatalog.empty();
        assertThat(catalog.sites()).isEmpty();
        assertThat(catalog.forName("TopVoter")).isEmpty();
    }

    @Test
    void forNameMatchesExactCase() {
        VoteSiteCatalog catalog = new VoteSiteCatalog(List.of(SITE_A, SITE_B));
        assertThat(catalog.forName("TopVoter")).contains(SITE_A);
        assertThat(catalog.forName("VoteMC")).contains(SITE_B);
    }

    @Test
    void forNameIsCaseInsensitive() {
        VoteSiteCatalog catalog = new VoteSiteCatalog(List.of(SITE_A, SITE_B));
        assertThat(catalog.forName("topvoter")).contains(SITE_A);
        assertThat(catalog.forName("TOPVOTER")).contains(SITE_A);
        assertThat(catalog.forName("votemc")).contains(SITE_B);
    }

    @Test
    void forNameReturnsEmptyForUnknownSite() {
        VoteSiteCatalog catalog = new VoteSiteCatalog(List.of(SITE_A));
        assertThat(catalog.forName("NoSuchSite")).isEmpty();
    }

    @Test
    void sitesListIsImmutable() {
        VoteSiteCatalog catalog = new VoteSiteCatalog(List.of(SITE_A));
        assertThat(catalog.sites()).hasSize(1);
    }
}
