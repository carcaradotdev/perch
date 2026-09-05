package com.example.sample.home.api

import com.example.sample.navigation.SampleRoute
import dev.carcara.perch.DeepLink

/**
 * The link that opens the app on Home.
 *
 * A `data object` because the route carries nothing: there is one Home, and the path has no
 * placeholders to fill. `@DeepLink` is the whole contract Perch asks for - no `@Serializable`,
 * because `@DeepLink` is `@MetaSerializable` and the compiler plugin generates the serialiser from
 * it alone.
 */
@DeepLink("/home")
public data object HomeDeepLink : SampleRoute
