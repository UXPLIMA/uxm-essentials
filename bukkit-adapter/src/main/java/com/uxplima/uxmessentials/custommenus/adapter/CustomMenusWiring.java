package com.uxplima.uxmessentials.custommenus.adapter;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.MenuCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the custommenus context's adapters over the shared menu engine: a {@link CustomMenuLoader} that reads
 * the operator's {@code menus/*.conf} into {@link Menus}, run once on enable, and the {@code /menu} command. This is
 * the one place the context is wired — nothing else news up its classes.
 *
 * <p>The loaded menu names are held in an {@link AtomicReference} the {@code /menu list} and tab-completion read and
 * {@code /menu reload} swaps atomically: a reload re-runs the loader (re-registering specs into the engine,
 * overwriting the previous ones) and publishes the fresh name set in one assignment, so a reader sees either the old
 * or the new list, never a half-applied one. A disabled module never reaches this wiring, so it loads no menus and
 * registers no command.
 */
@NullMarked
public final class CustomMenusWiring {

    private CustomMenusWiring() {}

    public static Wired wire(Menus menus, MenuBindings bindings, Path dataFolder, Logger log, Messages messages) {
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(messages, "messages");

        CustomMenuLoader loader = new CustomMenuLoader(new MenuSpecLoader(), bindings, menus, log);
        Path menusDir = dataFolder.resolve("menus");
        AtomicReference<List<String>> names =
                new AtomicReference<>(loader.loadFrom(menusDir).loadedNames());

        Supplier<List<String>> nameSupplier = names::get;
        Supplier<CustomMenuLoader.LoadResult> reload = () -> {
            CustomMenuLoader.LoadResult result = loader.loadFrom(menusDir);
            names.set(result.loadedNames());
            return result;
        };
        MenuCommand command = new MenuCommand(menus, nameSupplier, reload, messages);
        return new Wired(List.of(command), nameSupplier);
    }

    /**
     * The wired custommenus adapters handed back to bootstrap: the {@code /menu} command and the loaded-menu-names
     * supplier (so a later consumer — e.g. a management hub entry — can read the current set). There is no stop hook:
     * the loader registers specs into the shared engine, which the bootstrap tears down centrally on disable.
     */
    public record Wired(List<CommandRegistration> commands, Supplier<List<String>> menuNames) {
        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(menuNames, "menuNames");
        }
    }
}
