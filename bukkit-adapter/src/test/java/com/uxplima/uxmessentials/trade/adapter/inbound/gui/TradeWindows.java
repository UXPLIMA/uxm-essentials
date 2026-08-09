package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;

/**
 * Builds the two trade windows over a test engine, the way production wiring does but off a data folder that holds
 * no overrides, so every fixture reads the bundled specs from the classpath — the very files the plugin ships. The
 * geometry the tests assert against therefore comes from those files rather than from anything the tests declare.
 */
public final class TradeWindows {

    /** No data folder on disk, so the bundled spec on the classpath is what loads. */
    private static final Path NO_OVERRIDES = Path.of("no-such-data-folder");

    private TradeWindows() {}

    public static TradeWindow sameServer(Messages messages, Menus menus, List<String> currencies) {
        return new TradeWindow(messages, menus, currencies, NO_OVERRIDES, SILENT);
    }

    public static CrossTradeWindow crossServer(Messages messages, Menus menus) {
        return new CrossTradeWindow(messages, menus, NO_OVERRIDES, SILENT);
    }

    /** A logger that says nothing; the spec loads cleanly here, so there is nothing for it to report. */
    public static final Logger SILENT = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };
}
