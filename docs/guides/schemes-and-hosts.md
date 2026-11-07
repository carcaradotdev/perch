# Schemes and hosts

`DeepLinkParser` accepts a URL only if its scheme is in `schemes`. For `http` and `https`, the host
must also be in `hosts`.

```kotlin
DeepLinkParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))
```

## `http` and `https` need hosts

**If `schemes` contains `http` or `https`, `hosts` must not be empty.** If it is empty, the
constructor throws `IllegalArgumentException`:

```text
DeepLinkParser was given [https] with no hosts, which lets any website on the internet deep-link into these routes. Pass the domains you own, for example hosts = setOf("example.com"), or drop http and https from schemes.
```

This is the security property of Perch. Any website can link into your app over `http` and
`https`, through Android App Links and iOS Universal Links. Without a host check, a link from any
site could open a route that your app owns. The `hosts` list names the domains that you control.

## Custom schemes have no host

Only your app can register a custom scheme, such as `myapp://`, with the operating system. Thus a
custom scheme does not need hosts, and Perch does not read `hosts` for it.

In a custom-scheme URL, the text after `://` is the path. In `myapp://payments/abc123`, `payments`
is the first path segment, not a host:

```kotlin
@DeepLink("/payments/{id}")
data class PaymentLink(val id: String)

parser.parse("myapp://payments/abc123")               // PaymentLink(id = "abc123")
parser.parse("myapp://myapp.example/payments/abc123") // null, the path has three segments
```

??? note "How Perch reads the host"

    For `http` and `https`, the authority is the text between `://` and the first `/`, `?`, `#` or `\`.
    Perch removes the user information before `@` and the port after `:`. The remainder is the host.
    Perch compares hosts without letter case.

    The examples use `hosts = setOf("myapp.example")`:

    | URL | Host | Result |
    | --- | --- | --- |
    | `https://myapp.example/payments/1` | `myapp.example` | Accepted. |
    | `https://MyApp.Example/payments/1` | `myapp.example` | Accepted. Letter case is ignored. |
    | `https://myapp.example:8443/payments/1` | `myapp.example` | Accepted. The port is removed. |
    | `https://user@myapp.example/payments/1` | `myapp.example` | Accepted. The user information is removed. |
    | `https://myapp.example@evil.example/payments/1` | `evil.example` | Rejected. |
    | `https://evil.example\@myapp.example/payments/1` | `evil.example` | Rejected. A `\` ends the authority, as in a browser. |
    | `https:///payments/1` | None | Rejected. The authority is empty. |
    | `https://evil.example/payments/1` | `evil.example` | Rejected. |

    An IPv6 host keeps its brackets. To accept `https://[::1]/payments/1`, use `hosts = setOf("[::1]")`.

    Perch does not decode the authority. The host check uses the same bytes that a browser uses.

## Register the scheme and the hosts with the operating system

Perch does not tell the operating system which URLs to send to your app. Declare them on each
platform:

- **Android**: add an `intent-filter` for each scheme and host to `AndroidManifest.xml`. An
  `https` link opens your app only after Android verifies the domain with a Digital Asset Links
  file.
- **iOS**: add each custom scheme to `CFBundleURLTypes` in `Info.plist`. An `https` link needs
  Universal Links: the Associated Domains entitlement, and an `apple-app-site-association` file on
  your domain.

The [sample app](../sample.md) declares the `sample` scheme on both platforms, and the
`sample.example` host on Android.
