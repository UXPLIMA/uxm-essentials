package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The real {@link NbtApplier}, backed by NBT-API and reached only from {@link NbtApiHook#whenPresent}, past
 * {@code Hooks}' present-guard. NBT-API is not a compile dependency: this class names it purely by string
 * class-name through reflection — {@code new NBTItem(ItemStack)}, then {@code setInteger}/{@code setDouble}/
 * {@code setString} per inferred type, then {@code getItem()} — so no {@code de.tr7zw} type appears in any
 * field or method signature and constructing this on a server without NBT-API loads none of its classes.
 *
 * <p>The {@code NBTItem} class is resolved once in the constructor (it is reachable because this is reached
 * only past the present-guard); if it cannot be resolved the applier reports {@link #available()} false and
 * every apply is a no-op. Each apply is guarded: any {@link ReflectiveOperationException} (NBT-API absent, or
 * its method shape shifted under a version bump) is logged once and degraded to returning the item unchanged,
 * so a raw-NBT edit never throws into a menu click.
 */
@NullMarked
final class NbtApiService implements NbtApplier {

    static final String NBT_ITEM_CLASS = "de.tr7zw.changeme.nbtapi.NBTItem";

    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    @Nullable private final Class<?> nbtItemType;

    NbtApiService(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
        this.nbtItemType = loadNbtItemType();
    }

    /** True when NBT-API's {@code NBTItem} class is loadable; the probe used both here and by the hook's presence. */
    static boolean nbtApiLoadable() {
        return loadNbtItemType() != null;
    }

    @Override
    public boolean available() {
        return nbtItemType != null;
    }

    @Override
    public ItemStack apply(ItemStack item, Map<String, String> rawNbt) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(rawNbt, "rawNbt");
        Class<?> type = nbtItemType;
        if (type == null || rawNbt.isEmpty()) {
            return item;
        }
        try {
            return writeTags(type, item, rawNbt);
        } catch (ReflectiveOperationException failure) {
            degrade(failure);
            return item;
        }
    }

    private static ItemStack writeTags(Class<?> type, ItemStack item, Map<String, String> rawNbt)
            throws ReflectiveOperationException {
        // Wrap a clone so even a direct-apply NBT-API build cannot mutate the caller's item.
        Object nbtItem = type.getConstructor(ItemStack.class).newInstance(item.clone());
        for (Map.Entry<String, String> tag : rawNbt.entrySet()) {
            writeTag(type, nbtItem, tag.getKey(), tag.getValue());
        }
        Object built = type.getMethod("getItem").invoke(nbtItem);
        return built instanceof ItemStack stack ? stack : item;
    }

    private static void writeTag(Class<?> type, Object nbtItem, String key, String value)
            throws ReflectiveOperationException {
        switch (NbtTagType.infer(value)) {
            case INT ->
                type.getMethod("setInteger", String.class, Integer.class).invoke(nbtItem, key, Integer.valueOf(value));
            case DOUBLE ->
                type.getMethod("setDouble", String.class, Double.class).invoke(nbtItem, key, Double.valueOf(value));
            case STRING ->
                type.getMethod("setString", String.class, String.class).invoke(nbtItem, key, value);
        }
    }

    private static @Nullable Class<?> loadNbtItemType() {
        try {
            return Class.forName(NBT_ITEM_CLASS);
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private void degrade(ReflectiveOperationException failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=nbt_apply_failed reason={}", failure.toString());
        }
    }
}
