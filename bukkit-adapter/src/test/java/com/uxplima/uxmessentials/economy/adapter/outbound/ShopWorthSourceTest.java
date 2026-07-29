package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The shop-backed worth fallback in the two states a test classpath can reach. EconomyShopGUI is not on it, so the
 * catalogue read cannot be exercised; what can be, and what decides behaviour on a real server, is that a server
 * without the plugin prices nothing and never touches the SDK, that a server with the plugin but an unreachable
 * SDK answers "unpriced" instead of throwing into a {@code /worth}, and that the failure is reported once rather
 * than on every lookup.
 */
class ShopWorthSourceTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void present_isFalse_whenEconomyShopGuiIsAbsent() {
        assertThat(ShopWorthSource.present(server)).isFalse();
    }

    @Test
    void present_isTrue_onceTheShopIsInstalled() {
        MockBukkit.createMockPlugin("EconomyShopGUI");

        assertThat(ShopWorthSource.present(server)).isTrue();
    }

    @Test
    void withoutTheShopEveryMaterialIsUnpricedAndNothingIsLogged() {
        CountingLogger log = new CountingLogger();
        ShopWorthSource worth = new ShopWorthSource(server, log, "coins");

        assertThat(worth.unitPrice("diamond")).isEmpty();
        assertThat(log.warns()).isZero();
    }

    @Test
    void anUnreachableShopApiPricesNothingAndWarnsOnce() {
        MockBukkit.createMockPlugin("EconomyShopGUI");
        CountingLogger log = new CountingLogger();
        ShopWorthSource worth = new ShopWorthSource(server, log, "coins");

        assertThatCode(() -> {
                    assertThat(worth.unitPrice("diamond")).isEmpty();
                    assertThat(worth.unitPrice("emerald")).isEmpty();
                })
                .doesNotThrowAnyException();
        assertThat(log.warns())
                .as("the catalogue is read once, so the failure is reported once")
                .isEqualTo(1);
    }

    @Test
    void theSourceDeclaresNoShopSdkType() {
        // The structural guarantee that the present-guard, not a classload, is what gates the reflection: the SDK
        // is named only by string class-name, so loading this on a shop-less server pulls in zero me.gypopo class.
        assertThat(declaresPackage(ShopWorthSource.class, "me.gypopo")).isFalse();
    }

    private static boolean declaresPackage(Class<?> type, String prefix) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getReturnType().getName().startsWith(prefix)) {
                return true;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (parameter.getName().startsWith(prefix)) {
                    return true;
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.getType().getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** A {@link Logger} that counts warnings, so the warn-once contract can be asserted. */
    private static final class CountingLogger implements Logger {
        private final AtomicInteger warns = new AtomicInteger();

        int warns() {
            return warns.get();
        }

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warns.incrementAndGet();
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
