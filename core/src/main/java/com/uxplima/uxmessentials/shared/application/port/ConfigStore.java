package com.uxplima.uxmessentials.shared.application.port;

/**
 * Read access to the typed configuration tree, addressed by dotted HOCON paths
 * ({@code modules.homes.enabled}). The concrete implementation lives in an
 * adapter; the kernel depends only on this narrow contract so application code
 * never touches Configurate types directly.
 *
 * <p>Each {@code getX} takes a default returned when the path is absent, which is
 * how a feature module's {@code enabled} flag defaults to {@code true} when an
 * operator has not declared it in {@code modules.conf}.
 */
public interface ConfigStore {

    /** Returns the boolean at {@code path}, or {@code fallback} when it is absent. */
    boolean getBoolean(String path, boolean fallback);

    /** Returns the string at {@code path}, or {@code fallback} when it is absent. */
    String getString(String path, String fallback);

    /** Returns the int at {@code path}, or {@code fallback} when it is absent. */
    int getInt(String path, int fallback);
}
