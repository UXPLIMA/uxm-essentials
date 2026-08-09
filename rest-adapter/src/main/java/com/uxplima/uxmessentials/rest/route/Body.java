package com.uxplima.uxmessentials.rest.route;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.uxplima.uxmessentials.api.view.UxmGameMode;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.rest.http.HttpException;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestRequest;

/**
 * The JSON body of a write, read one field at a time.
 *
 * <p>Every reader here answers with the value or with a {@code 400} that names the field and says what was wrong
 * with it. That is the whole point: a write refused because a field was missing should tell whoever sent it which
 * field, not leave them comparing their request against the documentation line by line.
 *
 * <p>Nothing here is lenient. A number sent as a string, a uuid without dashes, a location missing its world: each
 * is a mistake worth reporting rather than guessing at, because the alternative is a deposit that quietly went to
 * the wrong account.
 *
 * @param json the parsed body, empty when the request had none
 */
public record Body(JsonObject json) {

    public Body {
        Objects.requireNonNull(json, "json");
    }

    /** Read the request's body, or {@code 400} when it is not a JSON object. */
    public static Body of(RestRequest request) {
        return new Body(Json.parse(request.http().body()));
    }

    /** A string field that has to be there. */
    public String text(String name) {
        String value = present(name).getAsString();
        if (value.isBlank()) {
            throw bad(name, "must not be blank");
        }
        return value;
    }

    /** A string field that may be left out. */
    public Optional<String> optionalText(String name) {
        return has(name) ? Optional.of(text(name)) : Optional.empty();
    }

    /** A decimal amount that has to be there, and has to be a number. */
    public BigDecimal decimal(String name) {
        JsonElement value = present(name);
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException notANumber) {
            throw bad(name, "must be a number");
        }
    }

    /** A number read as a float, for the speed multipliers. */
    public float number(String name) {
        return decimal(name).floatValue();
    }

    /** A whole number that may be left out. */
    public int integer(String name, int fallback) {
        return has(name) ? decimal(name).intValue() : fallback;
    }

    /** A true/false field that may be left out. */
    public boolean flag(String name, boolean fallback) {
        if (!has(name)) {
            return fallback;
        }
        JsonElement value = present(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw bad(name, "must be true or false");
        }
        return value.getAsBoolean();
    }

    /** A player id that has to be there. */
    public UUID uuid(String name) {
        String raw = text(name);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException notAUuid) {
            throw bad(name, "must be a uuid: " + raw);
        }
    }

    /** A duration written in seconds, or empty when the field is absent, which means "no end". */
    public Optional<Duration> seconds(String name) {
        if (!has(name)) {
            return Optional.empty();
        }
        long value = decimal(name).longValue();
        if (value < 1) {
            throw bad(name, "must be at least one second");
        }
        return Optional.of(Duration.ofSeconds(value));
    }

    /** One of the four game modes, however it was capitalised. */
    public UxmGameMode gameMode(String name) {
        String raw = text(name);
        try {
            return UxmGameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw bad(name, "must be one of survival, creative, adventure, spectator");
        }
    }

    /**
     * A place: a world and three coordinates, with the way somebody is facing optional.
     *
     * <p>Yaw and pitch default to zero, because a plugin setting a home from a map click has coordinates and no
     * opinion about which way the player should be looking, and demanding two more numbers to say nothing would
     * only invite them to be made up.
     */
    public UxmLocation location(String name) {
        JsonElement value = present(name);
        if (!value.isJsonObject()) {
            throw bad(name, "must be an object with world, x, y and z");
        }
        Body place = new Body(value.getAsJsonObject());
        return new UxmLocation(
                place.text("world"),
                place.decimal("x").doubleValue(),
                place.decimal("y").doubleValue(),
                place.decimal("z").doubleValue(),
                place.has("yaw") ? place.number("yaw") : 0f,
                place.has("pitch") ? place.number("pitch") : 0f);
    }

    /** Whether the body carries this field at all, an explicit null counting as absent. */
    public boolean has(String name) {
        return json.has(name) && !json.get(name).isJsonNull();
    }

    private JsonElement present(String name) {
        if (!has(name)) {
            throw bad(name, "is required");
        }
        return json.get(name);
    }

    private static HttpException bad(String field, String what) {
        return new HttpException(HttpStatus.BAD_REQUEST, field + " " + what);
    }
}
