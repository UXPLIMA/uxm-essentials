package com.uxplima.uxmessentials.shared.adapter.outbound.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * uxmLib speaks {@link System.Logger} and this plugin speaks its own port, so a line the library writes for
 * us has to arrive in the port at the same level and already formatted, or it never reaches the file an
 * operator reads.
 */
final class SystemLoggerBridgeTest {

    private final List<String> lines = new ArrayList<>();
    private final System.Logger bridge = new SystemLoggerBridge("uxmEssentials", recording());

    @Test
    @DisplayName("each level reaches the port at the matching level")
    void eachLevelReachesTheMatchingMethod() {
        bridge.log(System.Logger.Level.INFO, "bound");
        bridge.log(System.Logger.Level.WARNING, "degraded");
        bridge.log(System.Logger.Level.DEBUG, "detail");
        bridge.log(System.Logger.Level.ERROR, "broken");

        assertThat(lines).containsExactly("info bound", "warn degraded", "debug detail", "error broken");
    }

    @Test
    @DisplayName("a MessageFormat line arrives formatted, so the port never sees a {0}")
    void aFormatArrivesFormatted() {
        bridge.log(System.Logger.Level.INFO, "Bound to {0}", "Vault");

        assertThat(lines).containsExactly("info Bound to Vault");
    }

    @Test
    @DisplayName("a warning with a cause keeps the cause")
    void aWarningKeepsItsCause() {
        bridge.log(System.Logger.Level.WARNING, "Could not bind", new NoClassDefFoundError("com/example/Sdk"));

        assertThat(lines)
                .singleElement()
                .asString()
                .startsWith("warn Could not bind")
                .contains("com/example/Sdk");
    }

    @Test
    @DisplayName("every level is loggable, because the port decides what an operator sees")
    void everyLevelIsLoggable() {
        assertThat(bridge.isLoggable(System.Logger.Level.TRACE)).isTrue();
        assertThat(bridge.getName()).isEqualTo("uxmEssentials");
    }

    private Logger recording() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {
                lines.add("info " + message);
            }

            @Override
            public void warn(String message, Object... args) {
                StringBuilder line = new StringBuilder("warn " + message);
                for (Object arg : args) {
                    line.append(' ').append(arg);
                }
                lines.add(line.toString());
            }

            @Override
            public void error(String message, Throwable cause) {
                lines.add("error " + message);
            }

            @Override
            public void debug(String message, Object... args) {
                lines.add("debug " + message);
            }
        };
    }
}
