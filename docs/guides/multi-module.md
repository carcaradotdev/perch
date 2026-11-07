# Multi-module apps

In a modular app, each feature declares its own routes, and one module collects them:

| Plugin | Apply it to | It generates |
| --- | --- | --- |
| `dev.carcara.perch` | Each module that declares routes. | `internal fun perchModuleParser()` |
| `dev.carcara.perch.aggregation` | The module that assembles the app. | `public fun perchParser()` |

A module can apply one plugin, both plugins or no plugin. [Get started](../get-started.md) shows the
build file for each.

??? example "The layout of the sample app"

    ```text
    sample/
      sample-navigation/        SampleRoute, the supertype of all routes
      features/home/api/        producer: /home
      features/payments/api/    producer: /payments/{id} and /payment-approvals
      features/payments/impl/   the handler for the approvals link
      sample-di/                the router, and its interfaces for the platform apps
      sample-app/               aggregator: perchParser() for both features
      sample-android/           the Android app
      sample-ios-app/           the iOS app
    ```

    No central list names the routes. To add a feature, add a module.

## Options

| Extension | Property | Description |
| --- | --- | --- |
| `perch` | `outputPackage` | Required. The package of `perchModuleParser()`. An empty string publishes the routes and generates no parser. |
| `perch` | `processorCoordinates` | Optional. The KSP processor, as a Maven coordinate or a project path. The default is `perch-ksp` of the same version as the plugin. |
| `perchAggregation` | `outputPackage` | Required. The package of `perchParser()`. |

## Two targets or more

**A module that applies `dev.carcara.perch` must declare at least two Kotlin targets.** The KSP
processor runs on the `commonMain` compilation, and Kotlin Multiplatform creates that compilation
only for two or more targets. If a producer module has fewer, the build fails at configuration time.

The aggregator module does not have this rule.

## Which dependencies the aggregator reads

The aggregator reads the dependencies that `commonMain` of its module compiles against:

- `commonMain` dependencies declared with `implementation` or `api`.
- Transitive dependencies through `api` only. If `A` depends on `B` with `implementation`, an
  aggregator that depends on `A` does not see the routes of `B`.

It does not read `compileOnly` dependencies, or dependencies of `androidMain`, `iosMain` and the
other platform source sets.

!!! warning "Declare each producer dependency in `commonMain`"

    A route that the aggregator cannot see is not in `perchParser()`, and the build does not tell
    you. `parse` then returns `null` for its URLs.

To see what the aggregator found, read this line in the build output:

```text
Perch: generated perchParser() for com.example.app with 3 route(s) from 2 manifest(s):
```

## Collisions between modules

The KSP processor sees one module at a time. If two features declare patterns that can match the
same URL, the aggregator stops the build and names both routes. Change one of the two patterns. See
[Collisions](routes.md#collisions).

??? note "How the pipeline works"

    ```mermaid
    graph TB
      subgraph producer["Producer module"]
        src["@DeepLink classes in commonMain"] --> ksp["KSP: kspCommonMainKotlinMetadata"]
        ksp --> manifest["perch-manifest-*.txt"]
        ksp --> modparser["perchModuleParser()"]
      end
      subgraph aggregator["Aggregator module"]
        collect["perchManifests configuration"] --> task["generateDeepLinkRegistration"]
        task --> parser["perchParser()"]
      end
      manifest -->|perchManifestElements| collect
    ```

    1. The producer plugin adds the KSP processor to the `commonMain` metadata compilation.
    2. The processor finds the `@DeepLink` classes, checks them for collisions, and writes a route
       manifest and `perchModuleParser()`.
    3. The producer plugin publishes the manifest on the `perchManifestElements` configuration.
    4. The aggregator resolves the manifests of its `commonMain` dependencies.
    5. The `generateDeepLinkRegistration` task checks all routes for collisions and writes
       `perchParser()`. The task is cacheable.

    | Generated file | Contents |
    | --- | --- |
    | `build/generated/ksp/metadata/commonMain/kotlin/<package>/DeepLinkRegistration.kt` | `perchModuleParser()` |
    | `build/generated/ksp/metadata/commonMain/resources/perch-manifest-<module>.txt` | The route manifest |
    | `build/generated/perch/commonMain/kotlin/<package>/PerchDeepLinkRegistration.kt` | `perchParser()` |

    Each manifest line holds one route as `pattern|class|module`, for example:

    ```text
    payments/{id}|com.example.sample.payments.api.PaymentRoutes.Details|:sample:features:payments:api
    ```
