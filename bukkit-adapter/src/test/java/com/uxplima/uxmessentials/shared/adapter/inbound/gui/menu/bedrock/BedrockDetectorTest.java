package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The Bedrock detector in its Floodgate-absent state — the only state reachable in a test, since Floodgate is a
 * {@code compileOnly} soft-depend that is not on the test runtime (exactly like {@code LuckPermsMetaSource} and
 * the reflective hooks). The contract proved here is the load-safe one: with no {@code floodgate} plugin the
 * {@link BedrockDetector#forServer} factory returns the always-{@code false} {@link BedrockDetector#NONE}, and
 * neither the interface nor {@code NONE} declares any {@code org.geysermc.floodgate} type, so loading them pulls
 * in zero SDK class. The Floodgate-backed happy path stays untested by design — there is no Floodgate to delegate
 * to — so the absent-path selection and the structural isolation are the tested contract.
 */
class BedrockDetectorTest {

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
    void none_isNeverBedrock() {
        assertThat(BedrockDetector.NONE.isBedrock(UUID.randomUUID())).isFalse();
    }

    @Test
    void forServer_withoutFloodgate_selectsNone() {
        // MockBukkit registers no floodgate plugin, so isPluginEnabled("floodgate") is false and the factory must
        // pick NONE without ever naming (or loading) the Floodgate-backed detector.
        BedrockDetector detector = BedrockDetector.forServer(server);

        assertThat(detector).isSameAs(BedrockDetector.NONE);
        assertThat(detector.isBedrock(UUID.randomUUID())).isFalse();
    }

    @Test
    void interfaceAndNoneDeclareNoFloodgateType() {
        // The structural confirmation that the interface and the NONE default carry no SDK type, so loading them
        // (and the forServer factory) on a Floodgate-less server cannot pull in org.geysermc.floodgate. Only
        // FloodgateBedrockDetector — constructed past forServer's present-guard — references the SDK.
        assertThat(referencesFloodgateSdk(BedrockDetector.class)).isFalse();
        assertThat(referencesFloodgateSdk(BedrockDetector.NONE.getClass())).isFalse();
    }

    private static boolean referencesFloodgateSdk(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (mentionsFloodgate(method.getReturnType())) {
                return true;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (mentionsFloodgate(parameter)) {
                    return true;
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (mentionsFloodgate(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsFloodgate(Class<?> type) {
        return type.getName().startsWith("org.geysermc");
    }
}
