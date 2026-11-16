/**
 * Application layer of the custommenus bounded context: the {@code CustomMenusModule} that registers the {@code /menu}
 * command. The context is otherwise adapter-thin. It consumes the menu engine ({@code Menus} /
 * {@code MenuBindings}) rather than owning a domain aggregate.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.application;
