package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;

import com.google.gson.JsonNull;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmTradeQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Trades: what is open right now, and whether one particular player is in one. */
public final class TradeRoutes {

    private TradeRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(
                Route.of("GET", PREFIX + "/trades", Scopes.READ, request -> open(api)),
                Route.of("GET", PREFIX + "/players/{uuid}/trade", Scopes.READ, request -> trade(api, request)));
    }

    private static HttpResponse open(UxmEssentialsApi api) {
        return Reads.list(trades(api).open(), Views::trade);
    }

    /** The trade a player is in, or {@code null} rather than a {@code 404}: not trading is an answer, not a miss. */
    private static HttpResponse trade(UxmEssentialsApi api, RestRequest request) {
        return Json.ok(
                trades(api).of(request.uuidParameter("uuid")).map(Views::trade).orElse(JsonNull.INSTANCE));
    }

    private static UxmTradeQuery trades(UxmEssentialsApi api) {
        return Reads.module(api.trade(), "trade");
    }
}
