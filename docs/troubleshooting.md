# Troubleshooting

This page lists each message that Perch gives, where it comes from, and what to do. In the
messages, `<...>` shows a value that changes, such as a module path.

## A URL does not resolve

If `parse` returns `null` for a URL that you expect to match, examine these items in sequence:

1. The module that declares the route applies `dev.carcara.perch`.
2. The route class is in `commonMain`, not in a platform source set.
3. The aggregator module depends on the producer module in `commonMain`, with `implementation` or
   `api`. A dependency in `androidMain`, in `iosMain` or with `compileOnly` is not read.
4. If the dependency is transitive, each module between them uses `api`.
5. The scheme of the URL is in `schemes`.
6. For `http` and `https`, the host of the URL is in `hosts`.
7. For a custom scheme, the text after `://` is the path. `myapp://myapp.example/home` has the path
   `myapp.example/home`.
8. The URL has a value for each property that has no default, and each value converts to the
   property type.

The aggregator logs the routes that it registers. Find this line in the build output:

```text
Perch: generated perchParser() for <package> with <n> route(s) from <m> manifest(s):
```

## Configuration errors

### Fewer than two targets

```text
dev.carcara.perch: <module> declares 1 Kotlin target (jvm), and Perch needs at least two. Kotlin Multiplatform only gives a module a shared commonMain compilation once it has two or more targets, and that compilation is the one Perch's processor runs on - with a single target there is nothing for it to scan. Declare the other targets this module is built for, or move its routes into a module that has them.
```

Add a second target to the producer module, or move its routes to a module that has two or more
targets. See [Two targets or more](guides/multi-module.md#two-targets-or-more).

### KSP is not applied

```text
dev.carcara.perch: KSP is not configured on <module>. Apply id("com.google.devtools.ksp") before dev.carcara.perch.
```

Apply the KSP plugin in the same `plugins { }` block, before `dev.carcara.perch`.

### Kotlin Multiplatform is not applied

```text
dev.carcara.perch: Kotlin Multiplatform is not applied on <module>. Perch scans commonMain, which only a multiplatform module has. Apply id("org.jetbrains.kotlin.multiplatform") in the same `plugins { }` block.
```

```text
dev.carcara.perch.aggregation: Kotlin Multiplatform is not applied on <module>. Apply id("org.jetbrains.kotlin.multiplatform") in the same `plugins { }` block.
```

Apply Kotlin Multiplatform to the module. Both plugins work only in a multiplatform module.

### No output package

```text
dev.carcara.perch: set `perch.outputPackage` in <module>
```

Set `perch.outputPackage`. To publish the routes without a parser, set it to an empty string.

### No Perch version in the plugin

```text
dev.carcara.perch: cannot determine the Perch version because <cause>, so the default KSP processor coordinate would not resolve. This is a packaging fault in the plugin itself; work around it by setting `perch.processorCoordinates` to an explicit coordinate, for example "dev.carcara.perch:perch-ksp:<version>".
```

The plugin jar is damaged. Set `perch.processorCoordinates` to `dev.carcara.perch:perch-ksp:<version>`,
and report the problem.

### `SwiftPMLockTaskAggregationBuildService` cast exception

The Kotlin Gradle plugin is in two classloaders. Declare every plugin version once, in the root
build file, with `apply false`. See [Get started](get-started.md#2-declare-the-plugins-in-the-root-build-file).

## KSP errors

The KSP processor reports these as compilation errors in the producer module.

### Collision in one module

```text
Deep link collision detected. Pattern '<pattern>' conflicts with '<pattern>', already registered by '<class>', so '<class>' cannot be registered.
```

Two routes in the module can match the same URL. Change one of the two patterns. See
[Collisions](guides/routes.md#collisions).

### More than one parent

```text
There are multiple parents for deep link '<class>'.
```

The route has more than one property whose type is a route. Keep one parent property. See
[Nested routes](guides/routes.md#nested-routes).

### A route that is its own parent

```text
Deep link route '<class>' is reachable from itself through its parents.
```

The chain of parent properties makes a loop. Remove the parent property that closes the loop.

## Aggregator errors and warnings

### Collision between modules

```text
Deep link collision between modules. '<class>' in <module> declares '<pattern>', and '<class>' in <module> declares '<pattern>'. A URL matching one matches the other, so the parser cannot know which route to return. The manifests are <file> and <file>; change one of the two patterns.
```

Change one of the two patterns. See
[Collisions between modules](guides/multi-module.md#collisions-between-modules).

### No routes found

```text
Perch: no deep-link routes were discovered for <package>, so the generated perchParser() registers nothing. Manifests are resolved leniently: a dependency whose manifest failed to resolve is indistinguishable here from one that publishes no manifest. Manifests found: <n>.
```

The aggregator found no route manifest in its `commonMain` dependencies. Do the steps in
[A URL does not resolve](#a-url-does-not-resolve).

### A dependency did not resolve

```text
Perch: could not resolve <n> of this module's dependencies while looking for route manifests. Any route they declare is missing from <package>.perchParser():
```

The warning lists each failure. Correct the dependency. Gradle resolves manifests leniently, so this
does not stop the build.

### A manifest line was ignored

```text
Perch: ignored <n> manifest line(s) that are not a `pattern|routeClassName|moduleId` triple. Any route they meant to declare is missing from <package>.perchParser():
```

A manifest file is damaged. Do a clean build of the producer module.

## Runtime errors

### Constructor

```text
DeepLinkParser needs at least one scheme
```

```text
DeepLinkParser was given [https] with no hosts, which lets any website on the internet deep-link into these routes. Pass the domains you own, for example hosts = setOf("example.com"), or drop http and https from schemes.
```

`DeepLinkParser` throws `IllegalArgumentException`. Give at least one scheme. If `schemes` contains
`http` or `https`, give the hosts that you own. See [Schemes and hosts](guides/schemes-and-hosts.md).

### `register`

```text
Deep link collision detected. Pattern '<pattern>' conflicts with a pattern already registered by '<route>', so '<route>' cannot be registered.
```

`register` throws `DeepLinkCollisionException`. Change one of the two patterns.

### Messages to the logger

```text
Skipping deep link route '<route>' with no @DeepLink path pattern
```

A registered class has no `@DeepLink` pattern. This occurs if the generated registration is older
than the code. Build again so that the registration agrees with the code.

```text
DeepLinkParser.parse was called with no routes registered, so it can only return null. Build the parser with the generated perchParser(), or register routes yourself with register<T>().
```

The parser has no routes. Create it with `perchParser()` or `perchModuleParser()`, or register
routes on it.

### `toUrl`

`toUrl` throws `DeepLinkSerializationException` with one of these messages:

| Message | Cause |
| --- | --- |
| `<route> is not annotated @DeepLink, so it has no path pattern` | The class has no `@DeepLink`. |
| `There are multiple parents for deep link <route>` | The route has more than one parent property. |
| `Expect exactly one parameter with name: <name>, but found <n>` | A required placeholder has no value, or more than one value. |
| `Expect zero or one parameter with name: <name>, but found <n>` | An optional placeholder has more than one value. |
