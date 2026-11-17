package com.uxplima.uxmessentials.rest.route;

import static com.uxplima.uxmessentials.rest.Routes.PREFIX;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmPlayersQuery;
import com.uxplima.uxmessentials.api.view.UxmPlayer;
import com.uxplima.uxmessentials.api.view.UxmPlayerMatch;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpResponse;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.view.Views;

/**
 * Finding an account by name, so a web store or a bot holding what a player typed gets the uuid every other route
 * takes.
 *
 * <p>{@code GET /players/by-name/{name}} answers {@code 200} with {@code match} set to {@code found} and the account in
 * {@code player}, or {@code 200} with {@code match} set to {@code ambiguous}, {@code player} null and every account
 * the name could mean in {@code candidates}: on an offline-mode server {@code Steve} and {@code steve} are two
 * accounts, and a name that matches several only in case is not a guess this side makes. A name nobody has played
 * under is a {@code 404}. The name is percent-decoded, so a Floodgate prefix such as {@code #} can be sent as
 * {@code %23}.
 */
public final class PlayersRoutes {

    private PlayersRoutes() {}

    public static List<Route> of(UxmEssentialsApi api) {
        return List.of(Route.of(
                "GET", PREFIX + "/players/by-name/{name}", Scopes.READ, request -> byName(api.players(), request)));
    }

    static HttpResponse byName(UxmPlayersQuery players, RestRequest request) {
        String name = decode(request.parameter("name")).strip();
        if (name.isEmpty()) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "name is blank");
        }
        UxmPlayerMatch match = Reads.await(players.byName(name));
        if (match instanceof UxmPlayerMatch.Unknown) {
            throw new HttpException(HttpStatus.NOT_FOUND, "no player has played under that name");
        }
        JsonObject json = new JsonObject();
        json.addProperty("match", match instanceof UxmPlayerMatch.Found ? "found" : "ambiguous");
        json.add("player", match.player().map(PlayersRoutes::player).orElse(JsonNull.INSTANCE));
        json.add("candidates", Views.each(match.candidates(), PlayersRoutes::player));
        return Json.ok(json);
    }

    private static JsonElement player(UxmPlayer player) {
        JsonObject json = new JsonObject();
        json.addProperty("player-id", player.playerId().toString());
        json.addProperty("player-name", player.name());
        return json;
    }

    /** Percent-decoding only: a {@code +} in a path is a plus, which a Floodgate prefix may be. */
    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "name is not percent-encoded correctly: " + raw);
        }
    }
}
