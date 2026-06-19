package com.uxplima.uxmessentials.worlds.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A single typed per-world property: its catalog key, default, string codec (the {@code decode}
 * doubles as the validator — empty means "invalid"), and tab-completion suggestions. One descriptor
 * is the single source of truth driving the {@code /worlds set} argument, the {@code world_setting}
 * (de)serialization, and the live-apply binding.
 */
public final class WorldProperty<T> {

    private final String key;
    private final T defaultValue;
    private final Function<String, Optional<T>> decode;
    private final Function<T, String> encode;
    private final List<String> suggestions;

    private WorldProperty(
            String key,
            T defaultValue,
            Function<String, Optional<T>> decode,
            Function<T, String> encode,
            List<String> suggestions) {
        this.key = Objects.requireNonNull(key, "key");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.decode = Objects.requireNonNull(decode, "decode");
        this.encode = Objects.requireNonNull(encode, "encode");
        this.suggestions = List.copyOf(suggestions);
    }

    public String key() {
        return key;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public Optional<T> decode(String raw) {
        return raw == null ? Optional.empty() : decode.apply(raw.strip());
    }

    public String encode(T value) {
        return encode.apply(Objects.requireNonNull(value, "value"));
    }

    public List<String> suggestions() {
        return suggestions;
    }

    static WorldProperty<Boolean> ofBoolean(String key, boolean def) {
        return new WorldProperty<>(
                key,
                def,
                raw -> switch (raw.toLowerCase(Locale.ROOT)) {
                    case "true" -> Optional.of(Boolean.TRUE);
                    case "false" -> Optional.of(Boolean.FALSE);
                    default -> Optional.empty();
                },
                String::valueOf,
                List.of("true", "false"));
    }

    static <E extends Enum<E>> WorldProperty<E> ofEnum(String key, E def, Class<E> type) {
        List<String> names =
                Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        return new WorldProperty<>(
                key,
                def,
                raw -> {
                    for (E constant : type.getEnumConstants()) {
                        if (constant.name().equalsIgnoreCase(raw)) {
                            return Optional.of(constant);
                        }
                    }
                    return Optional.empty();
                },
                Enum::name,
                names);
    }

    static WorldProperty<Long> ofTicks(String key) {
        return new WorldProperty<>(
                key,
                0L,
                raw -> {
                    try {
                        long ticks = Long.parseLong(raw);
                        return ticks < 0 ? Optional.empty() : Optional.of(ticks);
                    } catch (NumberFormatException notANumber) {
                        return Optional.empty();
                    }
                },
                String::valueOf,
                List.of("0", "6000", "12000", "18000"));
    }

    static WorldProperty<Integer> ofInteger(String key, int def) {
        return new WorldProperty<>(
                key,
                def,
                raw -> {
                    try {
                        int value = Integer.parseInt(raw);
                        return value < 0 ? Optional.empty() : Optional.of(value);
                    } catch (NumberFormatException notANumber) {
                        return Optional.empty();
                    }
                },
                String::valueOf,
                List.of("0", "1", "10", "50"));
    }

    static WorldProperty<String> ofString(String key, String def) {
        return new WorldProperty<>(
                key, def, raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw), Function.identity(), List.of());
    }

    static WorldProperty<BigDecimal> ofDecimal(String key) {
        return new WorldProperty<>(
                key,
                BigDecimal.ZERO,
                raw -> {
                    try {
                        BigDecimal value = new BigDecimal(raw);
                        return value.signum() < 0 ? Optional.empty() : Optional.of(value);
                    } catch (NumberFormatException notANumber) {
                        return Optional.empty();
                    }
                },
                BigDecimal::toPlainString,
                List.of("0", "100", "500", "1000"));
    }
}
