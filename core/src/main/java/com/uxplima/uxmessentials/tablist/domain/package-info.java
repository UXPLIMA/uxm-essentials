/**
 * The tablist context's domain: the immutable {@link com.uxplima.uxmessentials.tablist.domain.TablistContent} value
 * object that captures the operator-authored tablist header and footer, refresh cadence, and per-world blacklist. The
 * content strings are raw MiniMessage source the adapter renders later — the domain only enforces the structural
 * invariants (a positive refresh interval). Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.tablist.domain;

import org.jspecify.annotations.NullMarked;
