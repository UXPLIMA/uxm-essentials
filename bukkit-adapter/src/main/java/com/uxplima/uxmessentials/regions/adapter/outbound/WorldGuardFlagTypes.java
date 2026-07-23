package com.uxplima.uxmessentials.regions.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagKind;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The reflective mapping between a WorldGuard {@code Flag} and the WorldGuard-free {@link FlagDescriptor} the flag
 * editor speaks. It classifies a registered flag into a portable {@link FlagKind}, renders its current value as a
 * portable string, lists an {@link FlagKind#ENUM} flag's choices, and converts a portable value the editor produced
 * back to the flag's concrete WorldGuard value on write.
 *
 * <p>Like {@link WorldGuardRegionService}, the SDK is named only by string class-name ({@code
 * com.sk89q.worldguard.protection.flags.*}) and reached purely by reflection, so no field or method signature carries a
 * {@code com.sk89q} type and none of WorldGuard's classes load until a caller behind the plugin-present guard actually
 * uses this. A flag type this helper does not recognise degrades to {@link FlagKind#OTHER} (read-only) rather than
 * failing, so a WorldGuard version that adds a new flag type never breaks the editor.
 */
@NullMarked
final class WorldGuardFlagTypes {

    private static final String FLAGS_PACKAGE = "com.sk89q.worldguard.protection.flags.";
    private static final String STATE_FLAG = FLAGS_PACKAGE + "StateFlag";
    private static final String STATE_ENUM = FLAGS_PACKAGE + "StateFlag$State";
    private static final String BOOLEAN_FLAG = FLAGS_PACKAGE + "BooleanFlag";
    private static final String INTEGER_FLAG = FLAGS_PACKAGE + "IntegerFlag";
    private static final String DOUBLE_FLAG = FLAGS_PACKAGE + "DoubleFlag";
    private static final String ENUM_FLAG = FLAGS_PACKAGE + "EnumFlag";
    private static final String REGISTRY_FLAG = FLAGS_PACKAGE + "RegistryFlag";
    private static final String STRING_FLAG = FLAGS_PACKAGE + "StringFlag";

    /** Resolved-class cache so a full registry pass does not re-run {@code Class.forName} per flag per predicate. */
    private static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();

    private WorldGuardFlagTypes() {}

    /**
     * Describe one registered flag for the editor: its name, its portable kind, the region's current value (empty when
     * the region has not set it), and the choices a fixed-choice flag offers. A registry-backed flag whose choices
     * cannot be enumerated degrades to a {@link FlagKind#STRING} the operator types (its own registry still validates
     * the typed value on write).
     */
    static FlagDescriptor describe(Object flag, @Nullable Object rawValue) throws ReflectiveOperationException {
        Objects.requireNonNull(flag, "flag");
        String name = flagName(flag);
        FlagKind kind = classify(flag);
        List<String> choices = kind == FlagKind.ENUM ? choices(flag) : List.of();
        if (kind == FlagKind.ENUM && choices.isEmpty()) {
            kind = FlagKind.STRING;
        }
        return new FlagDescriptor(name, kind, portableValue(flag, rawValue), choices);
    }

    /** The flag's registered name ({@code Flag#getName}, final on the base class). */
    static String flagName(Object flag) throws ReflectiveOperationException {
        return String.valueOf(flag.getClass().getMethod("getName").invoke(flag));
    }

    /** The portable kind of {@code flag}, decided by its WorldGuard type; an unrecognised type is {@link FlagKind#OTHER}. */
    static FlagKind classify(Object flag) {
        if (isInstance(STATE_FLAG, flag)) {
            return FlagKind.STATE;
        }
        // EnumFlag (and its RegionGroupFlag subclass) and RegistryFlag both present as a fixed set of choices.
        if (isInstance(ENUM_FLAG, flag) || isInstance(REGISTRY_FLAG, flag)) {
            return FlagKind.ENUM;
        }
        if (isInstance(BOOLEAN_FLAG, flag)) {
            return FlagKind.BOOLEAN;
        }
        if (isInstance(INTEGER_FLAG, flag)) {
            return FlagKind.INTEGER;
        }
        if (isInstance(DOUBLE_FLAG, flag)) {
            return FlagKind.DOUBLE;
        }
        // A CommandStringFlag is a StringFlag, so this also covers the command-string flags.
        if (isInstance(STRING_FLAG, flag)) {
            return FlagKind.STRING;
        }
        return FlagKind.OTHER;
    }

    /** The region's current value for {@code flag} as a portable string, or empty when the region has not set it. */
    static String portableValue(Object flag, @Nullable Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        if (isInstance(REGISTRY_FLAG, flag)) {
            String id = registryId(rawValue);
            if (id != null) {
                return id;
            }
        }
        // A StateFlag.State and any EnumFlag value are Java enums, so their constant name is the portable token; the
        // remaining kinds (boolean, number, string) round-trip through their own toString.
        if (rawValue instanceof Enum<?> constant) {
            return constant.name();
        }
        return String.valueOf(rawValue);
    }

    /** The choices of an {@link FlagKind#ENUM} flag: an EnumFlag's constant names, or a RegistryFlag's registered ids. */
    static List<String> choices(Object flag) throws ReflectiveOperationException {
        if (isInstance(ENUM_FLAG, flag)) {
            return enumChoices(flag);
        }
        if (isInstance(REGISTRY_FLAG, flag)) {
            return registryChoices(flag);
        }
        return List.of();
    }

    /**
     * Convert the editor's portable {@code value} to the concrete WorldGuard value {@code flag} accepts, ready to hand
     * to {@code ProtectedRegion.setFlag}. A blank value clears the flag ({@code null}). A value the flag cannot accept
     * (an unparsable number, an unknown choice) or a flag whose type the editor does not support raises
     * {@link IllegalArgumentException} so the write is refused rather than silently dropped.
     */
    static @Nullable Object toWgValue(Object flag, String value) throws ReflectiveOperationException {
        Objects.requireNonNull(flag, "flag");
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            return null;
        }
        if (isInstance(STATE_FLAG, flag)) {
            return stateValue(value);
        }
        if (isInstance(ENUM_FLAG, flag) || isInstance(REGISTRY_FLAG, flag)) {
            return requireAccepted(flag, value, unmarshal(flag, value));
        }
        if (isInstance(BOOLEAN_FLAG, flag)) {
            return parseBoolean(value);
        }
        if (isInstance(INTEGER_FLAG, flag)) {
            return Integer.valueOf(value.trim());
        }
        if (isInstance(DOUBLE_FLAG, flag)) {
            return Double.valueOf(value.trim());
        }
        if (isInstance(STRING_FLAG, flag)) {
            return value;
        }
        throw new IllegalArgumentException("flag type is not editable here");
    }

    /** The {@code StateFlag.State} constant for a portable state token ({@code ALLOW} / {@code DENY}, any case). */
    private static Object stateValue(String value) throws ReflectiveOperationException {
        return requireClass(STATE_ENUM)
                .getMethod("valueOf", String.class)
                .invoke(null, value.trim().toUpperCase(Locale.ROOT));
    }

    /** Parse a portable boolean token; only {@code true}/{@code false} (any case) are accepted. */
    private static Boolean parseBoolean(String value) {
        String token = value.trim();
        if (token.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (token.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("not a boolean: " + value);
    }

    /** {@code Flag#unmarshal(Object)}: the flag's own string-to-value reader (enum / registry). */
    private static @Nullable Object unmarshal(Object flag, String value) throws ReflectiveOperationException {
        return flag.getClass().getMethod("unmarshal", Object.class).invoke(flag, value);
    }

    /** An unmarshalled value is required to be non-null; a null means the flag rejected the token. */
    private static Object requireAccepted(Object flag, String value, @Nullable Object unmarshalled)
            throws ReflectiveOperationException {
        if (unmarshalled == null) {
            throw new IllegalArgumentException("not a valid value for " + flagName(flag) + ": " + value);
        }
        return unmarshalled;
    }

    /** The constant names of an {@code EnumFlag}'s enum, in declaration order. */
    private static List<String> enumChoices(Object flag) throws ReflectiveOperationException {
        Object enumClass = flag.getClass().getMethod("getEnumClass").invoke(flag);
        Object[] constants = ((Class<?>) enumClass).getEnumConstants();
        List<String> out = new ArrayList<>();
        if (constants != null) {
            for (Object constant : constants) {
                out.add(((Enum<?>) constant).name());
            }
        }
        return List.copyOf(out);
    }

    /** The ids of a {@code RegistryFlag}'s registry, best-effort; an empty list if it cannot be enumerated. */
    private static List<String> registryChoices(Object flag) {
        try {
            Object registry = flag.getClass().getMethod("getRegistry").invoke(flag);
            List<String> out = new ArrayList<>();
            if (registry instanceof Iterable<?> entries) {
                for (Object entry : entries) {
                    String id = registryId(entry);
                    if (id != null) {
                        out.add(id);
                    }
                }
            }
            return List.copyOf(out);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError cannotEnumerate) {
            return List.of();
        }
    }

    /** The id of a WorldEdit {@code Keyed} registry value ({@code Keyed#id}), or {@code null} when it has none. */
    private static @Nullable String registryId(Object keyed) {
        try {
            return String.valueOf(keyed.getClass().getMethod("id").invoke(keyed));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notKeyed) {
            return null;
        }
    }

    /** Whether {@code obj} is an instance of the named WorldGuard class; a class absent on this version is a no-match. */
    private static boolean isInstance(String className, Object obj) {
        Class<?> type = optionalClass(className);
        return type != null && type.isInstance(obj);
    }

    /** The named class, cached, or {@code null} when this WorldGuard version does not ship it. */
    private static @Nullable Class<?> optionalClass(String className) {
        Class<?> cached = CLASSES.get(className);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> loaded = Class.forName(className);
            CLASSES.put(className, loaded);
            return loaded;
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    /** The named class, cached, required to be present (the {@code StateFlag.State} enum a state write needs). */
    private static Class<?> requireClass(String className) throws ClassNotFoundException {
        Class<?> type = optionalClass(className);
        if (type == null) {
            throw new ClassNotFoundException(className);
        }
        return type;
    }
}
