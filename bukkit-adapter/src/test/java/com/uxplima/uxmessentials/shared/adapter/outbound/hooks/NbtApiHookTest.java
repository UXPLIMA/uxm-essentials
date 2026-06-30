package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The optional NBT-API hook in its absent state — the only state reachable in a test, since NBT-API is not on
 * the classpath (it is reached purely by reflection, like the currency reflection providers). The contract
 * proved here is the load-safe one: an NBT-API-less server resolves the hook to the no-op {@link NbtApplier}
 * whose every call is safe, its interface and absent default declare no {@code de.tr7zw} type (so loading them
 * pulls in zero SDK class), and the raw-string {@code ->} tag-type inference classifies values correctly. The
 * present SDK happy-path stays untested by design — there is no NBT-API to delegate to — so the absent path,
 * the inference, and the registration are the tested contract.
 */
class NbtApiHookTest {

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
    void absent_resolvesToNoOpAndEveryCallIsSafe() {
        NbtApiHook hook = new NbtApiHook(HookHarness.SILENT);

        NbtApplier applier = HookHarness.absent(hook);

        assertThat(applier).isSameAs(NbtApplier.ABSENT);
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        assertThatCode(() -> {
                    assertThat(applier.available()).isFalse();
                    assertThat(applier.apply(item, Map.of("Foo", "bar"))).isSameAs(item);
                    assertThat(applier.apply(item, Map.of())).isSameAs(item);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void absentDefaultAndInterfaceDeclareNoNbtApiType() {
        // The structural confirmation that the no-op default carries no SDK type, so loading the interface (and
        // its ABSENT field initializer) on an NBT-API-less server cannot pull in de.tr7zw. Only NbtApiService —
        // reached past Hooks' present-guard — references NBT-API, and even then only by reflective string name.
        assertThat(referencesNbtApiSdk(NbtApplier.class)).isFalse();
        assertThat(referencesNbtApiSdk(NbtApplier.ABSENT.getClass())).isFalse();
    }

    @Test
    void inference_classifiesByParseability() {
        assertThat(NbtTagType.infer("42")).isEqualTo(NbtTagType.INT);
        assertThat(NbtTagType.infer("-7")).isEqualTo(NbtTagType.INT);
        assertThat(NbtTagType.infer("1.5")).isEqualTo(NbtTagType.DOUBLE);
        assertThat(NbtTagType.infer("3.0")).isEqualTo(NbtTagType.DOUBLE);
        assertThat(NbtTagType.infer("hello")).isEqualTo(NbtTagType.STRING);
        assertThat(NbtTagType.infer("")).isEqualTo(NbtTagType.STRING);
    }

    @Test
    void hookIsRegisteredAndResolvesToNonNullCapability() {
        Hooks hooks = Hooks.resolve(server, HookHarness.SILENT, List.of(new NbtApiHook(HookHarness.SILENT)));

        assertThat(hooks.provides(NbtApplier.class)).isTrue();
        assertThat(hooks.capability(NbtApplier.class)).isSameAs(NbtApplier.ABSENT);
    }

    private static boolean referencesNbtApiSdk(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (mentionsNbtApi(method.getReturnType())) {
                return true;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (mentionsNbtApi(parameter)) {
                    return true;
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (mentionsNbtApi(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsNbtApi(Class<?> type) {
        return type.getName().startsWith("de.tr7zw");
    }
}
