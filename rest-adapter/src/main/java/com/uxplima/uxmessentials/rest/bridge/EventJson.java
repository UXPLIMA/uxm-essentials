package com.uxplima.uxmessentials.rest.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.rest.view.Views;
import org.jspecify.annotations.Nullable;

/**
 * An event as JSON, read off the getters the event class publishes.
 *
 * <p>Reflection rather than seventy-odd hand-written renderers, and deliberately so. The events are a published
 * surface with a compatibility promise and a golden file of their own, so their getters are exactly the list of
 * what an event is; writing that list out a second time here would only give it somewhere to drift to. What the
 * reflection produces is pinned in a golden file too, so the wire shape of every event is something a reader can
 * see without running the server.
 *
 * <p>Values go through the same conventions as the REST reads, and through {@link Views} for the types both share.
 * A location is a location whether it arrived over a socket or from {@code GET /players/{uuid}/homes}, which is
 * the whole reason to have one vocabulary rather than two.
 */
public final class EventJson {

    /** Getters that are Bukkit's own bookkeeping rather than anything the event is about. */
    private static final Set<String> NOT_FIELDS = Set.of("getHandlers", "getEventName", "isAsynchronous", "getClass");

    /**
     * Types that are a handle rather than a value.
     *
     * <p>Every event that offers one also carries the id and the name it was built from, so nothing is lost by
     * leaving these out; a live player object is not a thing that can be written down for somebody on the far end
     * of a socket, and the alternatives are all worse than saying so here.
     */
    private static final Set<Class<?>> NOT_VALUES = Set.of(Player.class, OfflinePlayer.class);

    private EventJson() {}

    /** Everything {@code event} carries, as one object. */
    public static JsonObject of(UxmEvent event) {
        JsonObject json = new JsonObject();
        for (Method getter : fieldsOf(event.getClass())) {
            json.add(nameOf(getter), value(read(getter, event)));
        }
        return json;
    }

    /**
     * The getters that make up an event, in a fixed order.
     *
     * <p>Sorted by name because reflection does not promise one, and a payload whose keys move about between
     * restarts is a payload nobody can write a golden file against.
     */
    public static List<Method> fieldsOf(Class<?> type) {
        List<Method> fields = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (isField(method)) {
                fields.add(method);
            }
        }
        fields.sort(Comparator.comparing(EventJson::nameOf));
        return fields;
    }

    private static boolean isField(Method method) {
        if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() > 0) {
            return false;
        }
        if (NOT_FIELDS.contains(method.getName()) || NOT_VALUES.contains(method.getReturnType())) {
            return false;
        }
        return method.getName().startsWith("get") || method.getName().startsWith("is");
    }

    /** {@code getTransactionId} becomes {@code transaction-id}, and a duration says its unit. */
    public static String nameOf(Method getter) {
        String name = getter.getName();
        String stem = name.startsWith("get") ? name.substring(3) : name.substring(2);
        String field = EventNames.kebab(stem);
        return getter.getReturnType() == Duration.class ? field + "-seconds" : field;
    }

    /** Whether this renderer knows how to write a value of that type, which the drift guard asks of every getter. */
    public static boolean canRender(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || type.isRecord()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class.isAssignableFrom(type)
                || Character.class.isAssignableFrom(type)
                || UUID.class.isAssignableFrom(type)
                || Instant.class.isAssignableFrom(type)
                || Duration.class.isAssignableFrom(type)
                || Optional.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type);
    }

    private static @Nullable Object read(Method getter, UxmEvent event) {
        try {
            return getter.invoke(event);
        } catch (IllegalAccessException | InvocationTargetException unreadable) {
            // A published getter that throws is the event's bug rather than this one's, and one field that cannot be
            // read is not a reason to drop the whole event: send it as null and let the rest through.
            return null;
        }
    }

    private static JsonElement value(@Nullable Object raw) {
        return switch (raw) {
            case null -> JsonNull.INSTANCE;
            case CharSequence text -> new JsonPrimitive(text.toString());
            case Boolean flag -> new JsonPrimitive(flag);
            case Character letter -> new JsonPrimitive(letter.toString());
            case Number number -> new JsonPrimitive(number);
            case UUID id -> new JsonPrimitive(id.toString());
            case Enum<?> constant -> new JsonPrimitive(constant.name());
            case Instant when -> new JsonPrimitive(when.toString());
            case Duration length -> new JsonPrimitive(length.toSeconds());
            case Optional<?> maybe -> value(maybe.orElse(null));
            case UxmLocation location -> Views.location(location);
            case UxmMoney money -> Views.money(money);
            case UxmIssuer issuer -> Views.issuer(issuer);
            case Collection<?> items -> array(items);
            case Record parts -> components(parts);
            default -> JsonNull.INSTANCE;
        };
    }

    private static JsonElement array(Collection<?> items) {
        JsonArray array = new JsonArray();
        items.forEach(item -> array.add(value(item)));
        return array;
    }

    /** Any other published record, written out by its components, so a new view type is not a hole in the stream. */
    private static JsonElement components(Record parts) {
        JsonObject json = new JsonObject();
        for (RecordComponent component : parts.getClass().getRecordComponents()) {
            json.add(EventNames.kebab(capitalise(component.getName())), value(read(component, parts)));
        }
        return json;
    }

    private static @Nullable Object read(RecordComponent component, Record parts) {
        try {
            return component.getAccessor().invoke(parts);
        } catch (IllegalAccessException | InvocationTargetException unreadable) {
            return null;
        }
    }

    private static String capitalise(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
