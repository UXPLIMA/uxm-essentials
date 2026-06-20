/**
 * The shared, config-driven management-GUI framework every module's admin menus reuse. Two generic views do
 * the work: {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView} is a paginated list of
 * entities (icon per entity, create button, paging), and
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView} is a property grid for one
 * entity (one button per editable field, back, optional confirm-gated delete). Both are built on the uxmLib
 * menu toolkit (so click routing flows through the installed listener) and are opened on the viewer's entity
 * thread through the shared {@code Scheduler}.
 *
 * <p>Nothing is hardcoded. Geometry, materials, slots, and filler come from
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout} /
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout}, read once from a module's
 * {@code modules/<m>/gui/<name>.conf} by {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts}
 * (which also reads the older {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout} and
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout}). All visible text is a
 * {@code MessageKey} resolved through
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText}, which renders it with the project
 * style tokens. The editable fields live in the
 * {@code com.uxplima.uxmessentials.shared.adapter.inbound.gui.property} sub-package; each mutates through the
 * module's own application use case, so the framework adds presentation only and never domain logic.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui;
