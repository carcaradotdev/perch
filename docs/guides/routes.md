# Declare routes

A route is a class annotated `@DeepLink`. The annotation holds the path pattern of the route.

```kotlin
@DeepLink("/payments/{id}")
data class PaymentLink(val id: String, val tab: String? = null)
// myapp://payments/abc123?tab=receipt
```

A route can be a `class`, a `data class` or a `data object`. It must be in `commonMain` of a module
that applies `dev.carcara.perch`.

## Path patterns

A path pattern is a list of segments, separated by `/`. Each segment is a constant or a placeholder.

| Segment | Name | Matches |
| --- | --- | --- |
| `payments` | Constant | The same text, exactly. |
| `{id}` | Required parameter | One segment. |
| `{id?}` | Optional parameter | One segment, or no segment. Use it only as the last segment. |
| `{path...}` | Tailcard | All the segments that remain, zero or more. |

These forms are the same as in Ktor.

```kotlin
@DeepLink("/profile/{userId?}")
data class ProfileLink(val userId: String? = null)
// myapp://profile      -> ProfileLink(userId = null)
// myapp://profile/42   -> ProfileLink(userId = "42")

@DeepLink("/files/{path...}")
data class FileLink(val path: List<String>)
// myapp://files/docs/2026/report.pdf -> FileLink(path = ["docs", "2026", "report.pdf"])
```

Perch ignores empty segments. A slash at the start, a slash at the end and a double slash do not
change a pattern or a URL. `/feature/list` and `/feature/list/` are the same pattern.

## Path parameters and query parameters

Each property whose name is a placeholder in the pattern gets its value from the path. Each other
property gets its value from the query string.

```kotlin
@DeepLink("/search")
data class SearchLink(
    val query: String,
    val page: Int = 1,
    val tags: List<String> = emptyList(),
)
// myapp://search?query=kotlin&page=2&tags=kmp&tags=ksp
//   -> SearchLink(query = "kotlin", page = 2, tags = ["kmp", "ksp"])
```

### Property types

Perch reads every value from the URL as text, then converts it to the property type.

| Property type | Conversion |
| --- | --- |
| `String` | No conversion. |
| `Int`, `Long`, `Short`, `Byte`, `Double`, `Float` | `toInt()`, `toLong()` and the other standard functions. |
| `Boolean` | `true` in any letter case gives `true`. Every other value gives `false`. |
| `Char` | The first character. |
| An enum class | The name of the enum entry. The letter case must agree. |
| `List` of one of these types | A repeated query parameter, or a tailcard. |

### Absent and incorrect values

- If the URL has no value for a property that has a default, the property gets the default.
- If the URL has no value for a property that has no default, the route does not match. This is
  also true for a nullable property. To make a parameter optional, give it a default, for example
  `= null`.
- If a value does not convert to the property type, the route does not match. An example is
  `page=abc` for an `Int`.

When a route does not match, `parse` tries the next route. If no route matches, `parse` returns
`null`. `parse` does not throw for a URL.

### A placeholder name that is not the property name

Use `@SerialName` to connect a placeholder to a property with a different name:

```kotlin
@DeepLink("/transfer/{id}")
data class TransferLink(
    @SerialName("id") val transferId: String,
)
```

## Nested routes

A route can extend the path of another route. Give the child route a property whose type is the
parent route. Perch then adds the path of the child to the path of the parent.

```kotlin
@DeepLink("/orders")
class Orders {
    @DeepLink("/{id}")
    class ById(val parent: Orders = Orders(), val id: String)
}
// Orders        -> /orders
// Orders.ById   -> /orders/{id}
```

The parent property connects the two routes. The location of the class declaration does not. A
class with `@DeepLink` inside another class, without a parent property, has only its own path.

Rules:

- The parent property is the property whose type is a route. A property of another type is a
  parameter.
- A route can have one parent property only.
- A route cannot be its own parent, directly or through other routes.

The KSP processor stops the build if a route breaks one of these rules.

## Group the routes of a feature

You can declare the routes of a feature inside a sealed interface. The KSP processor finds nested
classes, so each route is registered with its full name, for example `PaymentRoutes.Details`.

```kotlin
sealed interface PaymentRoutes {
    @DeepLink("/payments/{id}")
    data class Details(val id: String) : PaymentRoutes

    @DeepLink("/payment-approvals")
    data object Approvals : PaymentRoutes
}
```

A `when` over `PaymentRoutes` is then exhaustive.

## Give routes a supertype

`parse` returns `Any?`, because Perch does not require a supertype for routes. If you give your
routes a supertype of your own, you narrow the result once. After the cast, the result is typed,
and `null` means "not one of our routes".

```kotlin
interface Route

@DeepLink("/home")
data object HomeLink : Route

@DeepLink("/payments/{id}")
data class PaymentLink(val id: String) : Route

when (val route = parser.parse(url) as? Route) {
    is PaymentLink -> openPayment(route.id)
    HomeLink -> openHome()
    else -> Unit
}
```

If you make `Route` a sealed interface, the `when` does not need `else`. Kotlin permits this only if
every route is in the module that declares `Route`. If your routes are in different feature modules,
the supertype cannot be sealed.

The supertype is also the place for a type that your navigator requires, for example `NavKey`. See
[Navigation and DI](navigation.md).

## Collisions

Two path patterns collide if one URL can match both. Perch does not choose between them. It stops
the build, or it throws at registration.

| Pattern A | Pattern B | Result |
| --- | --- | --- |
| `/feature/list` | `/feature/list/` | Collision. Only the slash at the end is different. |
| `/feature/list` | `/feature/{id}` | Collision. `{id}` also matches `list`. |
| `/feature/{id}` | `/feature/{name}` | Collision. The structure is the same. |
| `/feature/list` | `/feature/details` | No collision. The constants are different. |
| `/feature/list` | `/feature/list/details` | No collision. The segment count is different. |
| `/feature` | `/feature/{id?}` | Collision. The optional segment can be absent. |
| `/feature/{rest...}` | `/feature/a/b` | Collision. The tailcard matches the segments that remain. |

Perch checks for collisions in three places:

1. The KSP processor checks the routes of one module.
2. The aggregator checks the routes of all the modules that it collects.
3. `DeepLinkParser.register` checks each route against the routes already registered. It throws
   `DeepLinkCollisionException`.

A collision between a constant and a parameter is a common case. The sample app has
`/payments/{id}`. A route at `/payments/approvals` would collide with it, because `approvals` is
also a valid `{id}`. The sample uses `/payment-approvals` for that reason.
