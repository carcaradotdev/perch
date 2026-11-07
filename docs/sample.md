# Sample app

The `sample/` directory holds an app that uses Perch from end to end. It has two feature modules
that declare routes, one module that aggregates them, an Android app and an iOS app.

The [Multi-module apps](guides/multi-module.md) guide shows its module layout.

## Build and test the sample

The root build compiles and tests the sample:

```bash
./gradlew build
```

The Apple targets compile only on macOS.

The sample tests check the full pipeline:

| Test | Module | What it checks |
| --- | --- | --- |
| `HomeModuleRegistrationTest` | `features/home/api` | `perchModuleParser()` resolves the route of its own module. |
| `PaymentRoutesTest` | `features/payments/api` | Routes nested in a sealed interface are registered. |
| `SamplePipelineTest` | `sample-app` | `perchParser()` holds the routes of both features. |
| `SampleGraphTest` | `sample-app` | The Metro graph, the router and the payments handler work together. |

## Run the Android app

```bash
./gradlew :sample:sample-android:installDebug
adb shell am start -a android.intent.action.VIEW -d "sample://payments/abc123"
```

You can also type a URL in the app. The app has three demos:

- **Navigation 3**: the parsed route goes on the back stack without an adapter.
- **Voyager**: a function maps the route to a `Screen`.
- **Dependency injection**: a Metro graph holds the parser. A router parses the URL and navigates.

Each URL that Android sends to the app also goes through the router.

## Run the iOS app

You need macOS, Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

1. Generate the Xcode project:

    ```bash
    cd sample/sample-ios-app
    xcodegen generate
    open SampleIos.xcodeproj
    ```

2. Run `SampleIos` on an iOS simulator, on an Apple silicon Mac. The sample builds the Kotlin
    framework for `iosSimulatorArm64` only. A build step in the Xcode project runs Gradle to link
    it.
3. Send a URL to the simulator:

    ```bash
    xcrun simctl openurl booted "sample://payments/abc123"
    ```

The app also lists the sample URLs. Tap a URL to send it through the router.

## URLs to try

The parser of the sample accepts the scheme `sample`, and `https` with the host `sample.example`.

| URL | Result |
| --- | --- |
| `sample://payments/abc123` | `PaymentRoutes.Details(id = "abc123")` |
| `sample://payment-approvals` | `PaymentRoutes.Approvals`. If the user is signed out, the payments handler sends the user to home. |
| `sample://home` | `HomeDeepLink` |
| `https://sample.example/payments/from-the-web` | `PaymentRoutes.Details(id = "from-the-web")` |
| `https://evil.example/payments/abc123` | `null`. The host is not in `hosts`. |
| `sample://nothing-matches-this` | `null`. No route matches. |

The operating system sends an `https` URL to the app only after it verifies the domain. The sample
does not own `sample.example`, so on a device an `https` URL opens the browser. Type the URL in the
app to see the result.
