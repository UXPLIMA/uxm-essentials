package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.economy.application.Worth;
import com.uxplima.uxmessentials.economy.application.WorthSource;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The bottom layer of the worth chain: EconomyShopGUI's own sell prices, so a server that has already priced its
 * whole item catalogue in a shop does not have to price it a second time for {@code /worth} and {@code /sell}.
 * An operator's {@code /setworth} override and configured worth both sit above this and always win.
 *
 * <p>Reached <b>entirely by reflection</b>: there is no compile dependency on EconomyShopGUI, so this class loads
 * and runs whether or not it is present. The catalogue is read once, on the first query, and cached for the
 * lifetime of the source; a reload rebuilds the economy wiring and with it this cache, which is the same
 * lifetime the configured worth table has.
 *
 * <p>Prices are read through {@code EconomyShopGUIHook.getItemSellPrice(shopItem, stack, 1, 0)}: one item, zero
 * sold. That is the player-free path, which matters because a worth is a property of the item in our model and
 * not of who is holding it. On a shop configured with dynamic (stock-based) pricing it therefore reports the base
 * price rather than the price after the day's trading, and an operator who wants the exact live number should
 * price the item explicitly instead.
 *
 * <p>A shop can price several entries for one material (different names, enchantments or NBT). The catalogue
 * keeps the first price it sees for a material, which is the shop's own first-listed entry: our worth model is
 * one price per material, so the alternative would be an arbitrary maximum rather than a more accurate answer.
 * Anything the shop cannot price ({@code null}, negative, or not a number) is skipped, so an unsellable shop
 * entry stays unsellable here.
 */
@NullMarked
public final class ShopWorthSource implements WorthSource {

    private static final String ECONOMY_SHOP_GUI = "EconomyShopGUI";
    private static final String ECONOMY_SHOP_GUI_PREMIUM = "EconomyShopGUI-Premium";
    private static final String HOOK_CLASS = "me.gypopo.economyshopgui.api.EconomyShopGUIHook";

    private final Server server;
    private final Logger log;
    private final String currencyId;
    private final AtomicBoolean warned = new AtomicBoolean();

    private @Nullable Map<String, Worth> catalogue;

    public ShopWorthSource(Server server, Logger log, String currencyId) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
        this.currencyId = Objects.requireNonNull(currencyId, "currencyId");
    }

    /**
     * Whether EconomyShopGUI is installed and enabled, so the wiring can decide whether binding this is worth it.
     * Both editions count: the premium one registers under its own plugin name but ships the same
     * {@code me.gypopo.economyshopgui.api} surface, so one reader serves both and asking for only the free name
     * would leave every premium server without the fallback.
     */
    public static boolean present(Server server) {
        Objects.requireNonNull(server, "server");
        return server.getPluginManager().isPluginEnabled(ECONOMY_SHOP_GUI)
                || server.getPluginManager().isPluginEnabled(ECONOMY_SHOP_GUI_PREMIUM);
    }

    @Override
    public Optional<Worth> unitPrice(String material) {
        Objects.requireNonNull(material, "material");
        return Optional.ofNullable(catalogue().get(material.toLowerCase(Locale.ROOT)));
    }

    private Map<String, Worth> catalogue() {
        Map<String, Worth> cached = catalogue;
        if (cached != null) {
            return cached;
        }
        Map<String, Worth> read = present(server) ? read() : Map.of();
        catalogue = read;
        return read;
    }

    /** Walk every shop section once and price what it can, degrading to an empty catalogue on any failure. */
    private Map<String, Worth> read() {
        try {
            Class<?> hook = Class.forName(HOOK_CLASS);
            Method sellPrice = itemSellPrice(hook);
            Object sections = hook.getMethod("getShopSections").invoke(null);
            if (!(sections instanceof List<?> ids)) {
                return Map.of();
            }
            Map<String, Worth> prices = new HashMap<>();
            for (Object id : ids) {
                Object section = hook.getMethod("getShopSection", String.class).invoke(null, String.valueOf(id));
                if (section != null) {
                    priceSection(section, sellPrice, prices);
                }
            }
            log.info("event=shop_worth_catalogue_read provider=economyshopgui materials={}", prices.size());
            return Map.copyOf(prices);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            degrade(failure);
            return Map.of();
        }
    }

    private void priceSection(Object section, Method sellPrice, Map<String, Worth> prices)
            throws ReflectiveOperationException {
        Object items = section.getClass().getMethod("getShopItems").invoke(section);
        if (!(items instanceof Iterable<?> shopItems)) {
            return;
        }
        for (Object shopItem : shopItems) {
            ItemStack stack = stackOf(shopItem);
            if (stack == null) {
                continue;
            }
            String material = stack.getType().name().toLowerCase(Locale.ROOT);
            if (prices.containsKey(material)) {
                continue;
            }
            price(sellPrice, shopItem, stack).ifPresent(amount -> prices.put(material, Worth.of(amount, currencyId)));
        }
    }

    /** The item a shop entry stands for: what it displays, or failing that what buying it hands over. */
    private static @Nullable ItemStack stackOf(Object shopItem) throws ReflectiveOperationException {
        Object display = shopItem.getClass().getMethod("getShopItem").invoke(shopItem);
        if (display instanceof ItemStack stack) {
            return stack;
        }
        Object given = shopItem.getClass().getMethod("getItemToGive").invoke(shopItem);
        return given instanceof ItemStack stack ? stack : null;
    }

    private static Optional<BigDecimal> price(Method sellPrice, Object shopItem, ItemStack stack) {
        try {
            Object answer = sellPrice.invoke(null, shopItem, stack.clone(), 1, 0);
            if (!(answer instanceof Number number) || number.doubleValue() < 0) {
                // EconomyShopGUI reports an unsellable entry as null or a negative price.
                return Optional.empty();
            }
            return Optional.of(BigDecimal.valueOf(number.doubleValue()));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unpriceable) {
            return Optional.empty();
        }
    }

    /**
     * {@code getItemSellPrice(ShopItem, ItemStack, int, int)}, matched by name and arity rather than by its exact
     * parameter types: the shop-item type is EconomyShopGUI's own and is never named here.
     */
    private static Method itemSellPrice(Class<?> hook) throws NoSuchMethodException {
        for (Method candidate : hook.getMethods()) {
            if (candidate.getName().equals("getItemSellPrice") && candidate.getParameterCount() == 4) {
                return candidate;
            }
        }
        throw new NoSuchMethodException(hook.getName() + "#getItemSellPrice");
    }

    private void degrade(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=shop_worth_read_failed provider=economyshopgui reason={}", failure.toString());
        }
    }
}
