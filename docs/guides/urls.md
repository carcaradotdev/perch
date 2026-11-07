# Parse and build URLs

`DeepLinkParser` turns a URL into a route object with `parse`. It turns a route object into a URL
with `toUrl`.

## Create a parser

Use the function that the Gradle plugins generate:

- `perchParser()` from the aggregator. It registers every route that the app module can reach.
- `perchModuleParser()` from the producer. It registers the routes of one module, and only that
  module can call it.

```kotlin
val parser = perchParser(
    schemes = setOf("myapp", "https"),
    hosts = setOf("myapp.example"),
    logger = DeepLinkLogger { message, throwable -> log.error(message, throwable) },
)
```

| Parameter | Default | Description |
| --- | --- | --- |
| `schemes` | None | The URL schemes that the parser accepts. Letter case is ignored. It must not be empty. |
| `hosts` | `emptySet()` | The hosts that the parser accepts for `http` and `https`. Letter case is ignored. |
| `logger` | `DeepLinkLogger.None` | Receives the problems that Perch recovers from. |

If `schemes` contains `http` or `https`, `hosts` must not be empty. See
[Schemes and hosts](schemes-and-hosts.md).

### Register routes yourself

You can also create a `DeepLinkParser` and register routes on it. This is useful in tests.

```kotlin
val parser = DeepLinkParser(schemes = setOf("myapp")).apply {
    register<HomeLink>()
    register<PaymentLink>()
}
```

- Each parser has its own routes. Two parsers do not share routes.
- If you register the same route twice, the second call does nothing.
- If a route collides with a route already registered, `register` throws
  `DeepLinkCollisionException`.
- If a class has no `@DeepLink` pattern, `register` skips it and reports it to the logger.
- `register(serializer)` accepts a `KSerializer` for a route that is not known at compile time.

## Parse a URL

`parse(url)` returns the route object, or `null` if no route matches. It does not throw for a URL.

```kotlin
parser.parse("myapp://payments/abc123")                // PaymentLink(id = "abc123")
parser.parse("https://myapp.example/payments/abc123")  // PaymentLink(id = "abc123")
parser.parse("https://evil.example/payments/abc123")   // null, the host is not in hosts
parser.parse("otherapp://payments/abc123")             // null, the scheme is not in schemes
parser.parse("myapp://payments")                       // null, no route matches
```

??? note "How `parse` reads a URL"

    `parse` reads a URL in this sequence:

    1. It removes the whitespace at the start and at the end.
    2. It reads the scheme before `://`. If the scheme is not in `schemes`, `parse` returns `null`.
    3. If the scheme is `http` or `https`, it reads the host. If the host is not in `hosts`, `parse`
       returns `null`.
    4. It removes the fragment, which starts at `#`.
    5. It divides the path into segments at `/` and removes the empty segments.
    6. It divides the query string into `name=value` pairs at `&`. It removes a pair that has no `=`.
    7. It percent-decodes each segment, each name and each value.
    8. It tries each registered route. It returns the first route that matches the path and gets a
       value for each required property.

    Perch decodes each segment and each query component after it divides the URL. Thus `%2F` in a value
    becomes a `/` inside that value. It does not start a new segment.

    - `+` stays `+`. A `+` means a space only in form data, not in a URL.
    - A `%` that is not followed by two hex digits stays as it is.
    - Bytes that are not valid UTF-8 become the replacement character, `U+FFFD`.
    - Perch does not normalize the path. A `{path...}` tailcard receives `.` and `..` as they are.

### A string without a scheme

`parse` also accepts a path without a scheme:

```kotlin
parser.parse("payments/abc123")   // PaymentLink(id = "abc123")
parser.parse("/payments/abc123")  // PaymentLink(id = "abc123")
```

!!! warning

    Do not use `schemes` as a filter for all input. A string without a scheme gets no scheme check
    and no host check.

A string that has a scheme in a different form is not a path. For example, `https:/evil.example/x`
has no `://`, but `parse` returns `null` for it.

## Build a URL

`toUrl(route)` returns the URL for a route object. `parse` turns that URL back into the same route.
The examples use the routes from [Declare routes](routes.md).

```kotlin
val parser = perchParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))

parser.toUrl(PaymentLink("abc123"))                // "myapp://payments/abc123"
parser.toUrl(SearchLink("kotlin", page = 2))       // "myapp://search?query=kotlin&page=2"
parser.toUrl(PaymentLink("a b/c é"))               // "myapp://payments/a%20b%2Fc%20%C3%A9"
```

??? note "How `toUrl` builds the URL"

    The scheme and the host come from the parser:

    - If `schemes` contains a custom scheme, the URL uses the first custom scheme. The result is
      `<scheme>://<path>`.
    - If `schemes` contains only `http` and `https`, the URL uses the first scheme and the first host.
      The result is `<scheme>://<host>/<path>`.

    The path and the query come from the route:

    - Each property fills the placeholder that has its name.
    - The other properties become query parameters, in the sequence of their declaration.
    - A property with a default value is written too.
    - A `null` value and an empty list are not written.
    - Perch percent-encodes each segment and each query component. Only the RFC 3986 unreserved
      characters stay as they are: letters, digits, `-`, `.`, `_` and `~`.

`toUrl` does not need the route to be registered.

`toUrl` throws `DeepLinkSerializationException` if it cannot build the URL. This occurs if:

- The class has no `@DeepLink` annotation.
- A required placeholder has no value, for example a `{id}` whose property is `null`.

A failure in `toUrl` is an error in the app code, so it throws. A failure in `parse` comes from outside
the app, so it returns `null`.

## Log what Perch recovers from

`DeepLinkLogger` receives two problems that do not stop the app, but that make a URL fail
without a sign:

- A registered class has no `@DeepLink` pattern. This can occur when a generated registration is
  older than the code. Perch skips the class.
- `parse` runs on a parser that has no routes. Perch reports this once per parser.

```kotlin
val logger = DeepLinkLogger { message, throwable ->
    myLogger.error(message, throwable)
}
```

The default, `DeepLinkLogger.None`, discards every message. Thus `perch-core` does not depend on a
log library.
