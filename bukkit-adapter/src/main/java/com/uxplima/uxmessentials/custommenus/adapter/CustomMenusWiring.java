package com.uxplima.uxmessentials.custommenus.adapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConverter;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConverter;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.MenuCommand;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.MenuOpenCommand;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuOpenerInteractListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuOpenerJoinListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuSwapListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerItems;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the custommenus context's adapters over the shared menu engine: a {@link CustomMenuLoader} that reads
 * the operator's {@code menus/*.conf} into {@link Menus}, run once on enable, the {@code /menu} command, one
 * {@link MenuOpenCommand} per menu that declared its own {@code command {}} block ({@code /shop}, {@code /store}),
 * and the opener-item listeners loaded from {@code menus/openers.conf}. This is the one place the context is wired —
 * nothing else news up its classes.
 *
 * <p>The loaded menu names are held in an {@link AtomicReference} the {@code /menu list} and tab-completion read and
 * {@code /menu reload} swaps atomically: a reload re-runs the loader (re-registering specs into the engine,
 * overwriting the previous ones) and publishes the fresh name set in one assignment, so a reader sees either the old
 * or the new list, never a half-applied one. The parsed openers are held in a second {@link AtomicReference} swapped
 * the same way, so a {@code /menu reload} also re-reads {@code openers.conf} — the join listener reads the fresh list
 * on the next join, the interact listener reads the fresh menu-name set, and the swap listener reads the fresh
 * off-hand-swap menu (or none) on the next swap. (Open commands are the one exception:
 * Brigadier only registers at startup, so a reload cannot add or drop a {@code /shop}-style command — that needs a
 * restart, as in DeluxeMenus.) A disabled module never reaches this wiring, so it loads no menus and registers
 * nothing.
 */
@NullMarked
public final class CustomMenusWiring {

    private CustomMenusWiring() {}

    public static Wired wire(
            Plugin plugin, Menus menus, MenuBindings bindings, Path dataFolder, Logger log, Messages messages) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(messages, "messages");

        // The custom-placeholder fallback registers here, after every built-in / data / stat / papi family the main
        // wiring registered, so a reserved-prefix custom name is still claimed by its prefix fallback first and a
        // custom name can never shadow a built-in exact handler. The loader owns it and swaps its definitions on each
        // load pass, so a /menu reload re-reads placeholders.conf alongside the menus and openers.
        CustomPlaceholders customPlaceholders = new CustomPlaceholders(bindings);
        CustomMenuLoader loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, menus, log, customPlaceholders);
        OpenerLoader openerLoader = new OpenerLoader(log);
        Path menusDir = dataFolder.resolve("menus");
        Path openersFile = menusDir.resolve("openers.conf");

        CustomMenuLoader.LoadResult first = loader.loadFrom(menusDir);
        OpenerLoader.OpenerConfig firstOpeners = openerLoader.loadConfig(openersFile, Set.copyOf(first.loadedNames()));
        AtomicReference<List<String>> names = new AtomicReference<>(first.loadedNames());
        AtomicReference<List<OpenerSpec>> openers = new AtomicReference<>(firstOpeners.openers());
        // The off-hand-swap menu rides its own reference, swapped on reload beside the openers, so the swap listener
        // reads the fresh menu (or none) on the next swap without being re-registered.
        AtomicReference<Optional<String>> swapMenu = new AtomicReference<>(firstOpeners.swapMenu());

        Supplier<List<String>> nameSupplier = names::get;
        Supplier<List<OpenerSpec>> openerSupplier = openers::get;
        Supplier<Optional<String>> swapMenuSupplier = swapMenu::get;
        Supplier<CustomMenuLoader.LoadResult> reload = () -> {
            CustomMenuLoader.LoadResult result = loader.loadFrom(menusDir);
            OpenerLoader.OpenerConfig reloaded = openerLoader.loadConfig(openersFile, Set.copyOf(result.loadedNames()));
            names.set(result.loadedNames());
            openers.set(reloaded.openers());
            swapMenu.set(reloaded.swapMenu());
            return result;
        };
        // The DeluxeMenus and zMenu converters behind /menu convert <deluxemenus|zmenu> <path>. Both write into the
        // same menus/ directory the loader reads, so a converted spec is picked up on the next /menu reload (neither
        // converter reloads itself).
        DeluxeMenusConvertService deluxeMenusConvert =
                new DeluxeMenusConvertService(menusDir, new DeluxeMenusConverter(), log);
        ZMenuConvertService zMenuConvert = new ZMenuConvertService(menusDir, new ZMenuConverter(), log);
        MenuCommand command = new MenuCommand(menus, nameSupplier, reload, deluxeMenusConvert, zMenuConvert, messages);

        // The open commands a menu declares in its `command {}` block are built once here, from this first load,
        // and handed back for the bootstrap to register at the LifecycleEvents.COMMANDS event. Brigadier only
        // accepts command registrations at that startup event, so — as in DeluxeMenus — a later `/menu reload`
        // refreshes menu CONTENT (specs re-parsed, /menu open sees the new set) but does NOT add, rename, or drop
        // open commands for new or edited menus: that needs a server restart. We deliberately do not try to
        // re-register Brigadier nodes on reload.
        List<CommandRegistration> commands = new ArrayList<>();
        commands.add(command);
        first.openCommands()
                .forEach((menuId, spec) -> commands.add(new MenuOpenCommand(menus, menuId, spec, messages)));

        OpenerItems openerItems = new OpenerItems(plugin);
        List<Listener> listeners = List.of(
                new MenuOpenerInteractListener(menus, nameSupplier, openerItems),
                new MenuOpenerJoinListener(openerSupplier, openerItems),
                new MenuSwapListener(menus, swapMenuSupplier, nameSupplier));
        return new Wired(commands, nameSupplier, listeners);
    }

    /**
     * The wired custommenus adapters handed back to bootstrap: the {@code /menu} command (plus any open commands),
     * the loaded-menu-names supplier (so a later consumer — e.g. a management hub entry — can read the current set),
     * and the opener-item and off-hand-swap listeners. There is no stop hook: the loader registers specs into the
     * shared engine, which the bootstrap tears down centrally on disable.
     */
    public record Wired(
            List<CommandRegistration> commands, Supplier<List<String>> menuNames, List<Listener> listeners) {
        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(menuNames, "menuNames");
            listeners = List.copyOf(listeners);
        }
    }
}
