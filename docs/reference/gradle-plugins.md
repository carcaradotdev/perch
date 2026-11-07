# Gradle plugins

Both plugins are in the artifact `dev.carcara.perch:perch-gradle-plugin`, on Maven Central.

## Producer: `dev.carcara.perch`

Apply it to a module that declares routes.

### Requirements

- Kotlin Multiplatform, `org.jetbrains.kotlin.multiplatform`.
- KSP, `com.google.devtools.ksp`.
- Two or more Kotlin targets.

The kotlinx.serialization compiler plugin is also necessary, because it generates the serializer
of each route.

### Extension: `perch`

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `outputPackage` | `Property<String>` | None. Required. | The package of the generated `perchModuleParser()`. An empty string publishes the manifest and generates no parser. |
| `processorCoordinates` | `Property<String>` | `dev.carcara.perch:perch-ksp:<plugin version>` | The KSP processor. A Maven coordinate, or a project path such as `:perch-ksp`. |

Change `processorCoordinates` only to use a processor from a different location. The Perch sample
sets it to a project path, because the processor is in the same build.

### What it adds

| Item | Name | Description |
| --- | --- | --- |
| Configuration | `perchManifestElements` | A consumable configuration that publishes the route manifest. It has the attribute `dev.carcara.perch.manifest = true`. |
| Dependency | `kspCommonMainMetadata` | The KSP processor, from `processorCoordinates`. |
| KSP options | `perch.outputPackage`, `perch.moduleId` | The output package, and the Gradle path of the module. |
| Task order | `kspCommonMainKotlinMetadata` | Runs before each Kotlin compile task, each detekt task and each sources jar task. |
| Source directory | `build/generated/ksp/metadata/commonMain/kotlin` | Added to `commonMain`, so the module compiles its own `perchModuleParser()`. |

### Generated files

| File | Contents |
| --- | --- |
| `build/generated/ksp/metadata/commonMain/kotlin/<package>/DeepLinkRegistration.kt` | `internal fun perchModuleParser(schemes, hosts, logger): DeepLinkParser` |
| `build/generated/ksp/metadata/commonMain/resources/perch-manifest-<module>.txt` | The route manifest. |

## Aggregator: `dev.carcara.perch.aggregation`

Apply it to the module that assembles your app.

### Requirements

- Kotlin Multiplatform, `org.jetbrains.kotlin.multiplatform`.

### Extension: `perchAggregation`

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `outputPackage` | `Property<String>` | None. Required. | The package of the generated `perchParser()`. |

### What it adds

| Item | Name | Description |
| --- | --- | --- |
| Configuration | `perchManifests` | A resolvable configuration. It extends `commonMainImplementation` and `commonMainApi`, and resolves the variant that `commonMain` compiles against. |
| Task | `generateDeepLinkRegistration` | Reads the manifests, checks for collisions and writes `perchParser()`. It is in the `build` group, and its output is cacheable. |
| Source directory | `build/generated/perch/commonMain/kotlin` | Added to `commonMain`. Gradle runs the task before it compiles `commonMain`. |

### Generated file

| File | Contents |
| --- | --- |
| `build/generated/perch/commonMain/kotlin/<package>/PerchDeepLinkRegistration.kt` | `public fun perchParser(schemes, hosts, logger): DeepLinkParser` |

Both generated functions have the same parameters as the `DeepLinkParser` constructor:

```kotlin
fun perchParser(
    schemes: Set<String>,
    hosts: Set<String> = emptySet(),
    logger: DeepLinkLogger = DeepLinkLogger.None,
): DeepLinkParser
```

Each function registers its routes in the alphabetical sequence of their class names, with the full
class name.

## The route manifest

The KSP processor writes one manifest for each producer module. The aggregator reads all of them.
The file name is `perch-manifest-`, then the Gradle path of the module, then `.txt`. In the path,
each group of characters that are not letters or digits becomes one `-`, and a `-` at the start or
at the end is removed. For `:features:payments:api`, the name is
`perch-manifest-features-payments-api.txt`.

Each line holds one route, with three fields separated by `|`:

```text
payments/{id}|com.example.sample.payments.api.PaymentRoutes.Details|:sample:features:payments:api
```

| Field | Contents |
| --- | --- |
| 1 | The full path pattern, with the patterns of the parents, without the slash at the start. |
| 2 | The full class name of the route. |
| 3 | The Gradle path of the module that declares the route. |

The aggregator ignores a line that does not have three fields, and logs a warning for it.

## Gradle features

The aggregator task is cacheable. The Perch repository builds its sample with the configuration
cache and Isolated Projects on, with both plugins applied.
