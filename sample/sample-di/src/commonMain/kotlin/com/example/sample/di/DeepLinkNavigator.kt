package com.example.sample.di

import com.example.sample.navigation.SampleRoute

/**
 * Where [DeepLinkRouter] sends a route once it has one.
 *
 * The shell implements this - Compose on Android, SwiftUI on iOS - and hands the implementation to
 * the graph at creation time. Which is the reason the interface exists: the router is
 * multiplatform and the navigation it drives is not.
 */
public interface DeepLinkNavigator {
  public fun goTo(route: SampleRoute)
}
