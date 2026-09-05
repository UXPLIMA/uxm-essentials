/**
 * The colour editor of the shared management-GUI framework:
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourProperty} is the button an
 * editor draws for a colour-valued field, and clicking it opens a picker built from
 * {@code ColourPickerLayout}. {@code ColourSwatch} renders one choice as a dyed tile, {@code ColourHex} parses
 * and formats the {@code #rrggbb} form the text prompt accepts, and {@code ColourPickerText} resolves every
 * label through the message catalog. It sits in its own package because the picker is a sub-menu with its own
 * layout rather than a single button, which is what separates it from the value editors beside it.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour;
