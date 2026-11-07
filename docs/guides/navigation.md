# Navigation and DI

Perch turns a URL into a route object and stops there. This page shows how to get the URL from the
operating system, and how to give the route to a navigator.

Some navigation libraries have a deep-link feature of their own. Perch does not replace it. Perch
reads the URL and gives you a typed object. The navigator decides what to do with that object.

## Get the URL from the operating system

=== "Android"

    ```kotlin title="MainActivity.kt"
    class MainActivity : ComponentActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            // A recreated activity receives the same intent again, so read it on a new start only.
            if (savedInstanceState == null) {
                intent?.dataString?.let(::open)
            }
        }

        override fun onNewIntent(intent: Intent) {
            super.onNewIntent(intent)
            intent.dataString?.let(::open)
        }

        private fun open(url: String) {
            val route = parser.parse(url)
            // Give the route to your navigator.
        }
    }
    ```

    Set `android:launchMode="singleTask"` on the activity. A URL that arrives while the app runs
    then goes to `onNewIntent` of the same activity.

=== "iOS"

    ```swift title="App.swift"
    @main
    struct MyApp: App {
        var body: some Scene {
            WindowGroup {
                ContentView()
                    .onOpenURL { url in router.open(url.absoluteString) }
            }
        }
    }
    ```

    Objective-C has no nested types. In Swift, the Kotlin class `PaymentRoutes.Details` is
    `PaymentRoutesDetails`.

## Navigation 3

Navigation 3 needs no adapter. Its back stack holds `NavKey`, which is a marker interface. A route
can implement it, because Perch does not require a supertype:

```kotlin
interface Route : NavKey

@DeepLink("/payments/{id}")
data class PaymentLink(val id: String) : Route

val route = parser.parse(url)
if (route is NavKey) backStack.add(route)
```

`rememberNavBackStack` requires keys that are serializable. A route is serializable already,
because `@DeepLink` is `@MetaSerializable`.

## Voyager

A Voyager `Screen` declares `@Composable fun Content()`, so a `Screen` is UI. A route module that
implements `Screen` must depend on Compose and hold the layout. Keep the routes plain, and map them
to screens in the module that has the UI:

```kotlin
fun Any?.toScreen(): Screen? = when (this) {
    is HomeLink -> HomeScreen
    is PaymentLink -> PaymentScreen(id)
    else -> null
}
```

Most navigation libraries need a function like this one.

## Dependency injection

`perchParser()` is a plain function, so any DI framework can provide it.

=== "Metro"

    ```kotlin
    @BindingContainer
    @ContributesTo(AppScope::class)
    object DeepLinkBindings {
        @Provides
        @SingleIn(AppScope::class)
        fun provideParser(): DeepLinkParser =
            perchParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))
    }
    ```

=== "Koin"

    ```kotlin
    val deepLinkModule = module {
        single {
            perchParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))
        }
    }
    ```

After this provider, no other code names a feature or the generated function. Other classes ask
for a `DeepLinkParser`.

### A router with handlers from each feature

The sample app shows a router that each feature can extend without a central list:

1. `DeepLinkRouter` gets the parser, a navigator and a map of handlers from the graph.
2. `open(url)` parses the URL and narrows the result to the app supertype.
3. If a feature contributed a handler for the route class, the handler can send the user to a
   different route. For example, the payments feature sends a signed-out user to home.
4. The router gives the final route to the navigator. Each platform app implements the navigator.

A feature thus declares its URLs with `@DeepLink`, and its rules with a handler. Perch finds the
routes. Metro finds the handlers.

See these files in the repository:

- [`sample-di/.../DeepLinkRouter.kt`](https://github.com/carcaradotdev/perch/blob/main/sample/sample-di/src/commonMain/kotlin/com/example/sample/di/DeepLinkRouter.kt)
- [`sample-di/.../DeepLinkRouteHandler.kt`](https://github.com/carcaradotdev/perch/blob/main/sample/sample-di/src/commonMain/kotlin/com/example/sample/di/DeepLinkRouteHandler.kt)
- [`features/payments/impl/.../ApprovalsHandler.kt`](https://github.com/carcaradotdev/perch/blob/main/sample/features/payments/impl/src/commonMain/kotlin/com/example/sample/payments/impl/ApprovalsHandler.kt)
- [`sample-app/.../SampleGraph.kt`](https://github.com/carcaradotdev/perch/blob/main/sample/sample-app/src/commonMain/kotlin/com/example/sample/app/SampleGraph.kt)
