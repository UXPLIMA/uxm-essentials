package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.util.List;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmSkinQuery;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/** The skin a player chose: where it came from, and when they took it. */
public final class SkinRoutes {

    private SkinRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(Route.of("GET", PREFIX + "/players/{uuid}/skin", Scopes.READ, request -> skin(api, request)));
    }

    /**
     * The player's own choice, or {@code 404} when they made none.
     *
     * <p>Not the texture: it is a signed blob meaningful only to a client, and the source is what a caller can do
     * something with.
     */
    private static HttpResponse skin(UxmEssentialsApi api, RestRequest request) {
        return Reads.found(Reads.await(reads(api).of(request.uuidParameter("uuid"))), "skin", Views::skin);
    }

    private static UxmSkinQuery reads(UxmEssentialsApi api) {
        return Reads.module(api.skin(), "skin");
    }
}
