package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Reading money: what currencies exist, what somebody has, and who has the most. */
public final class EconomyRoutes {

    /** The most rows a leaderboard request can ask for at once. */
    private static final int TOP_CAP = 100;

    private EconomyRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/economy/currencies", Scopes.READ, request -> currencies(api)),
                Route.of("GET", PREFIX + "/economy/top", Scopes.READ, request -> top(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/balance", Scopes.READ, request -> balance(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/balances", Scopes.READ, request -> balances(api, request)));
    }

    private static HttpResponse currencies(UxmEssentialsApi api) {
        return Json.ok(Views.strings(economy(api).currencies()));
    }

    private static HttpResponse top(UxmEssentialsApi api, RestRequest request) {
        UxmEconomyQuery economy = economy(api);
        int limit = Reads.limit(request, 10, TOP_CAP);
        Optional<String> currency = request.http().queryParam("currency");
        return Reads.list(
                Reads.await(currency.map(name -> economy.top(name, limit)).orElseGet(() -> economy.top(limit))),
                Views::baltopEntry);
    }

    /**
     * One balance. Without {@code ?currency=} it is the default one, which is what a server with a single currency
     * always wants; with it, a currency this server does not have is a {@code 404} rather than a zero balance.
     */
    private static HttpResponse balance(UxmEssentialsApi api, RestRequest request) {
        UxmEconomyQuery economy = economy(api);
        Optional<String> currency = request.http().queryParam("currency");
        if (currency.isEmpty()) {
            return Json.ok(Views.money(Reads.await(economy.balance(request.uuidParameter("uuid")))));
        }
        Optional<UxmMoney> held = Reads.await(economy.balance(request.uuidParameter("uuid"), currency.get()));
        return Reads.found(held, "currency named " + currency.get(), Views::money);
    }

    private static HttpResponse balances(UxmEssentialsApi api, RestRequest request) {
        return Reads.list(Reads.await(economy(api).balances(request.uuidParameter("uuid"))), Views::money);
    }

    private static UxmEconomyQuery economy(UxmEssentialsApi api) {
        return Reads.module(api.economy(), "economy");
    }
}
