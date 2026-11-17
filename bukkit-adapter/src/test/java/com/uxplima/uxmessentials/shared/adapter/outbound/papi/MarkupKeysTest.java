package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderSpec;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The keys whose value is the operator's own markup, parsed in our lines, are exactly the ones the catalogue says.
 * Every other key is shown there as words, so a key that starts writing markup of its own has to be listed, and a
 * listed key has to answer markup.
 */
class MarkupKeysTest {

    private static final PlayerRef ALEX = new PlayerRef(new UUID(0L, 1L), "Alex");
    private static final PlayerRef BLAKE = new PlayerRef(new UUID(0L, 2L), "Blake");

    @Test
    void aKeyThatWritesMarkupOfItsOwnIsListedAsMarkup() {
        PlaceholderResolver plain = new PlaceholderResolver(EverySeam.answering("plain"));
        List<String> unlisted = new ArrayList<>();
        for (PlaceholderSpec spec : PlaceholderCatalog.all()) {
            Optional<String> value = answer(plain, spec);
            if (value.isPresent() && value.get().contains("<") && !spec.markup()) {
                unlisted.add(spec.key() + " = " + value.get());
            }
        }

        assertThat(unlisted).isEmpty();
    }

    @Test
    void aKeyListedAsMarkupAnswersMarkup() {
        PlaceholderResolver tagged = new PlaceholderResolver(EverySeam.answering("<b>bold</b>"));
        List<String> stale = new ArrayList<>();
        for (PlaceholderSpec spec : PlaceholderCatalog.all()) {
            if (spec.markup() && !answer(tagged, spec).orElse("").contains("<")) {
                stale.add(spec.key());
            }
        }

        assertThat(PlaceholderCatalog.all()).anyMatch(PlaceholderSpec::markup);
        assertThat(stale).isEmpty();
    }

    @Test
    void inOurOwnLinesNoOtherKeyCarriesATag() {
        PlaceholderResolver tagged = new PlaceholderResolver(EverySeam.answering("<b>bold</b> &cred"));
        List<String> carried = new ArrayList<>();
        int checked = MarkupPass.during(() -> {
            int count = 0;
            for (PlaceholderSpec spec : PlaceholderCatalog.all()) {
                Optional<String> value = answer(tagged, spec);
                if (spec.markup() || value.isEmpty() || !value.get().contains("<")) {
                    continue;
                }
                count++;
                String shown = MarkupPass.shown(spec.sampled("example"), value.get());
                if (shown.replace("\\<", "").contains("<") || shown.contains("&c")) {
                    carried.add(spec.key() + " = " + shown);
                }
            }
            return count;
        });

        assertThat(checked).isPositive();
        assertThat(carried).isEmpty();
    }

    private static Optional<String> answer(PlaceholderResolver resolver, PlaceholderSpec spec) {
        String key = spec.sampled("example");
        try {
            return spec.relational() ? resolver.resolveRelational(ALEX, BLAKE, key) : resolver.resolve(ALEX, true, key);
        } catch (RuntimeException unanswered) {
            // A seam type the fakes cannot make leaves this key out of the check.
            return Optional.empty();
        }
    }
}
