# Runtime API

The public API of `perch-core`, in the package `dev.carcara.perch`. The build checks it for binary
compatibility.

## `@DeepLink`

```kotlin
@SerialInfo
@MetaSerializable
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeepLink(val path: String)
```

Marks a class as a route, and gives its path pattern. Because the annotation is `@MetaSerializable`,
the class does not need `@Serializable`. See [Declare routes](../guides/routes.md).

## `DeepLinkParser`

```kotlin
class DeepLinkParser(
    schemes: Set<String>,
    hosts: Set<String> = emptySet(),
    logger: DeepLinkLogger = DeepLinkLogger.None,
)
```

Turns URLs into route objects, and route objects into URLs.

The constructor throws `IllegalArgumentException` if:

- `schemes` is empty.
- `schemes` contains `http` or `https`, and `hosts` is empty.

### `register`

```kotlin
inline fun <reified T : Any> register()
fun <T : Any> register(serializer: KSerializer<T>)
```

Adds a route to this parser.

- A second registration of the same route does nothing.
- If the class has no `@DeepLink` pattern, `register` skips it and sends a message to the logger.
- If the pattern collides with a pattern already registered, `register` throws
  `DeepLinkCollisionException`.

### `parse`

```kotlin
fun parse(url: String): Any?
```

Returns the route object for `url`, or `null` if no route matches. Does not throw for a URL. See
[Parse a URL](../guides/urls.md#parse-a-url).

### `toUrl`

```kotlin
inline fun <reified T : Any> toUrl(deepLink: T): String
fun <T : Any> toUrl(serializer: KSerializer<T>, deepLink: T): String
```

Returns the URL for a route object. Throws `DeepLinkSerializationException` if the class has no
`@DeepLink` annotation, or if a required placeholder has no value. See
[Build a URL](../guides/urls.md#build-a-url).

## `DeepLinkLogger`

```kotlin
fun interface DeepLinkLogger {
    fun error(message: String, throwable: Throwable?)

    companion object {
        val None: DeepLinkLogger
    }
}
```

Receives the problems that Perch recovers from, instead of a throw:

- `register` skips a class that has no `@DeepLink` pattern.
- `parse` runs on a parser that has no routes. Perch reports this once for each parser.

`DeepLinkLogger.None` discards every message. It is the default.

## `DeepLinkCollisionException`

```kotlin
class DeepLinkCollisionException(
    val pattern: String,
    val existingRoute: String,
    val newRoute: String,
) : IllegalStateException
```

`register` throws it when two path patterns can match the same URL. `existingRoute` and `newRoute`
are the serial names of the two route classes.

## `DeepLinkSerializationException`

```kotlin
class DeepLinkSerializationException(message: String) : Exception
```

`toUrl` throws it when it cannot build a URL from a route object. `parse` does not throw it.
