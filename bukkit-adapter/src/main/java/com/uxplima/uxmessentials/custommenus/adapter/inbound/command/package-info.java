/**
 * The custommenus context's inbound command layer: the {@code /menu} Brigadier command (open / list / reload) over
 * the loaded operator menus. {@code open} opens a registered spec through the shared {@code Menus} façade, {@code
 * list} prints the loaded names, and {@code reload} re-runs the {@code CustomMenuLoader} and reports the counts.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;
