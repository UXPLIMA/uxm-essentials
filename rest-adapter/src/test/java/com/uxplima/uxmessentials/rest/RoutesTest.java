package com.uxplima.uxmessentials.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Router;
import org.junit.jupiter.api.Test;

class RoutesTest {

    @Test
    void statusReportsTheRunningVersion() {
        assertThat(status().body()).contains("\"version\":\"0.5.0\"").contains("\"api\":\"v1\"");
    }

    @Test
    void statusReportsWhichModulesAreAnswering() {
        String body = status().body();

        assertThat(body).contains("\"economy\":true");
        assertThat(body).contains("\"homes\":false");
    }

    private static HttpResponse status() {
        Router router = Routes.build(api());
        HttpRequest request = new HttpRequest("GET", Routes.PREFIX + "/status", Map.of(), Map.of(), "");
        Router.Match match = router.find(request).orElseThrow();
        return match.route().handler().handle(match.request());
    }

    /** An API that is running, with one module on, so the status answer has both cases in it. */
    private static UxmEssentialsApi api() {
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.version()).thenReturn("0.5.0");
        when(api.economy()).thenReturn(Optional.of(mock(UxmEconomyQuery.class)));
        return api;
    }
}
