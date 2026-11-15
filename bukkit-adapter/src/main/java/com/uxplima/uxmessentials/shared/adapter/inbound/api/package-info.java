/**
 * Where the published developer API meets the running plugin: the front-door implementation, the engine-backed menu
 * surface, and the wrappers that adapt the menu engine's runtime contexts onto the published view interfaces.
 *
 * <p>The dependency direction is one-way. Classes here import the published API and the engine; nothing in the
 * published API imports anything here, which {@code publishedApiNamesNoInternalType} in
 * {@code LayerDependenciesTest} enforces so a published signature can never name an internal type.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.api;
