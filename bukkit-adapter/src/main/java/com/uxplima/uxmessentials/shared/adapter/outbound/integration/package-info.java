/**
 * The integration catalog: one declaration per third-party plugin uxmEssentials integrates with.
 *
 * <p>Nothing here constructs an integration. Each family's registry does that and keeps living beside the
 * providers it builds. This package answers a different question, the one an operator asks: which plugins does
 * this server's uxmEssentials talk to, and where does each conversation start. The plugin manifest, the
 * {@code /uxmess doctor} report and the published integrations page all derive from this list rather than
 * repeating it.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.integration;

import org.jspecify.annotations.NullMarked;
