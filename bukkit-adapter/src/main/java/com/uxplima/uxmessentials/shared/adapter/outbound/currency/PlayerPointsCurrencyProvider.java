package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The {@code playerpoints} back-end, reached reflectively exactly as the migration {@code PlayerPointsBalanceFeed}
 * does: {@code org.black_ixx.playerpoints.PlayerPoints.getAPI()} hands back the API, whose {@code look(UUID)},
 * {@code give(UUID,int)} and {@code take(UUID,int)} read and move integer points. PlayerPoints is single-currency,
 * so the spec carries no sub-currency name.
 *
 * <p>Points are whole numbers; a fractional amount is rounded to the nearest point before it crosses into the API.
 * No {@code org.black_ixx} type appears in this class — every reference is a string class-name through reflection,
 * so the absent path loads nothing.
 */
final class PlayerPointsCurrencyProvider extends ReflectiveCurrencyProvider {

    private static final String PLUGIN_NAME = "PlayerPoints";
    private static final String API_CLASS = "org.black_ixx.playerpoints.PlayerPoints";

    PlayerPointsCurrencyProvider(String id, Server server, Logger log) {
        super(id, PLUGIN_NAME, null, server, log);
    }

    @Override
    protected double readBalance(UUID player) throws ReflectiveOperationException {
        Object api = api();
        Object points = api.getClass().getMethod("look", UUID.class).invoke(api, player);
        return ((Number) points).doubleValue();
    }

    @Override
    protected boolean changeBalance(UUID player, double amount, boolean deposit) throws ReflectiveOperationException {
        Object api = api();
        Method method = api.getClass().getMethod(deposit ? "give" : "take", UUID.class, int.class);
        Object ok = method.invoke(api, player, (int) Math.max(0, Math.round(amount)));
        return Boolean.TRUE.equals(ok);
    }

    private static Object api() throws ReflectiveOperationException {
        Object api = Class.forName(API_CLASS).getMethod("getAPI").invoke(null);
        if (api == null) {
            throw new ReflectiveOperationException("PlayerPoints.getAPI() returned null");
        }
        return api;
    }
}
