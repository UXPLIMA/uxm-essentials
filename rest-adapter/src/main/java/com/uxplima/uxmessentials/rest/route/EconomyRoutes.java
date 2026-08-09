package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmEconomyActions;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** Money: what currencies exist, what somebody has, who has the most, and moving it. */
public final class EconomyRoutes {

    /** The most rows a leaderboard request can ask for at once. */
    private static final int TOP_CAP = 100;

    private EconomyRoutes() {}

    public static List<Route> of(UxmEssentialsApi api, ActionsFor actions) {
        return List.of(
                Route.of("GET", PREFIX + "/economy/currencies", Scopes.READ, request -> currencies(api)),
                Route.of("GET", PREFIX + "/economy/top", Scopes.READ, request -> top(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/balance", Scopes.READ, request -> balance(api, request)),
                Route.of("GET", PREFIX + "/players/{uuid}/balances", Scopes.READ, request -> balances(api, request)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/balance/deposit",
                        Scopes.WRITE,
                        request -> move(actions, request, UxmEconomyActions::deposit, UxmEconomyActions::deposit)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/balance/withdraw",
                        Scopes.WRITE,
                        request -> move(actions, request, UxmEconomyActions::withdraw, UxmEconomyActions::withdraw)),
                Route.of(
                        "POST",
                        PREFIX + "/players/{uuid}/balance/set",
                        Scopes.WRITE,
                        request -> move(actions, request, UxmEconomyActions::set, UxmEconomyActions::set)),
                Route.of("POST", PREFIX + "/economy/transfer", Scopes.WRITE, request -> transfer(actions, request)));
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

    /**
     * Deposit, withdraw and set, which differ only in which method they call.
     *
     * <p>Each takes {@code amount} and an optional {@code currency}, and each answers with the balance afterwards,
     * so a caller never has to follow a write with a read to find out where it landed.
     */
    private static HttpResponse move(ActionsFor actions, RestRequest request, Default asDefault, Named asNamed) {
        UxmEconomyActions economy = writes(actions, request);
        UUID playerId = request.uuidParameter("uuid");
        Body body = Body.of(request);
        BigDecimal amount = body.decimal("amount");
        Optional<String> currency = body.optionalText("currency");

        return Writes.result(
                currency.map(name -> asNamed.apply(economy, playerId, amount, name))
                        .orElseGet(() -> asDefault.apply(economy, playerId, amount)),
                Views::money);
    }

    /** Moving money between two players, which is one operation rather than a withdraw and a deposit. */
    private static HttpResponse transfer(ActionsFor actions, RestRequest request) {
        UxmEconomyActions economy = writes(actions, request);
        Body body = Body.of(request);
        UUID from = body.uuid("from");
        UUID to = body.uuid("to");
        BigDecimal amount = body.decimal("amount");
        Optional<String> currency = body.optionalText("currency");

        return Writes.outcome(currency.map(name -> economy.transfer(from, to, amount, name))
                .orElseGet(() -> economy.transfer(from, to, amount)));
    }

    private static UxmEconomyQuery economy(UxmEssentialsApi api) {
        return Reads.module(api.economy(), "economy");
    }

    private static UxmEconomyActions writes(ActionsFor actions, RestRequest request) {
        return Reads.module(actions.actingFor(request.caller()).economy(), "economy");
    }

    /** {@code deposit(player, amount)} and its two siblings. */
    @FunctionalInterface
    private interface Default {
        CompletableFuture<UxmResult<UxmMoney>> apply(UxmEconomyActions economy, UUID playerId, BigDecimal amount);
    }

    /** The same three with a currency named. */
    @FunctionalInterface
    private interface Named {
        CompletableFuture<UxmResult<UxmMoney>> apply(
                UxmEconomyActions economy, UUID playerId, BigDecimal amount, String currency);
    }
}
