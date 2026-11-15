package com.uxplima.uxmessentials.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.staff.adapter.StaffGadget;
import com.uxplima.uxmessentials.staff.adapter.StaffSettings;
import org.junit.jupiter.api.Test;

/** A gadget's name reads from the catalogue unless an operator renamed it. */
class StaffGadgetNamesTest {

    private final List<String> notices = new ArrayList<>();

    private final Logger log = new Logger() {
        @Override
        public void info(String message, Object... args) {
            notices.add(message);
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    @Test
    void anUnnamedGadgetReadsTheCatalogue() {
        assertThat(nameOf(StaffGadget.VANISH, new Written())).isEqualTo("@staff.gadget.vanish");
        assertThat(nameOf(StaffGadget.COMPASS, new Written())).isEqualTo("@staff.gadget.compass");
    }

    @Test
    void theEnglishNameEarlierVersionsShippedReadsTheCatalogueToo() {
        Written config = new Written()
                .set("gadgets.vanish.name", "<accent>ᴠᴀɴɪꜱʜ</accent>")
                .set("gadgets.follow.name", "<accent>Follow</accent>");

        assertThat(nameOf(StaffGadget.VANISH, config)).isEqualTo("@staff.gadget.vanish");
        assertThat(nameOf(StaffGadget.FOLLOW, config)).isEqualTo("@staff.gadget.follow");
        assertThat(notices).hasSize(2);
    }

    @Test
    void aNameTheOperatorWroteIsKept() {
        Written config = new Written().set("gadgets.freeze.name", "<aqua>Ice Wand");

        assertThat(nameOf(StaffGadget.FREEZE, config)).isEqualTo("<aqua>Ice Wand");
        assertThat(notices).isEmpty();
    }

    private String nameOf(StaffGadget gadget, ConfigStore config) {
        return new StaffSettings(config, log)
                .gadgets().stream()
                        .filter(spec -> spec.gadget() == gadget)
                        .findFirst()
                        .orElseThrow()
                        .name();
    }

    /** The staff config as an operator wrote it: the keys given here, every other key at its default. */
    private static final class Written implements ConfigStore {

        private final Map<String, String> values = new HashMap<>();

        Written set(String path, String value) {
            values.put(path, value);
            return this;
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.getOrDefault(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
