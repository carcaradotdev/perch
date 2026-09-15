import SwiftUI
import SampleShared

/// The picker, and whatever the selected URL resolves to.
///
/// Worth comparing against `sample-android`: there, Navigation 3 takes the parsed object onto its
/// back stack with no adapter because the routes implement `NavKey`. Here the object goes onto a
/// SwiftUI `NavigationPath` with no adapter either, and for a reason nobody designed for - every
/// Kotlin class exported to Objective-C is an `NSObject` subclass, Kotlin's `equals`/`hashCode`
/// become `isEqual`/`hash`, and that is exactly what Swift's `Hashable` is built from.
struct ContentView: View {

  @ObservedObject var shell: Shell

  var body: some View {
    NavigationStack(path: $shell.path) {
      List {
        Section("URL") {
          Text(shell.url).font(.system(.footnote, design: .monospaced))
        }

        Section("Resolves to") {
          Text(describe(shell.route))
            .font(.system(.footnote, design: .monospaced))
            .foregroundStyle(shell.route == nil ? .secondary : .primary)
        }

        Section("Try a link") {
          ForEach(SampleLinks.shared.all, id: \.self) { link in
            Button(link) { shell.open(link) }
              .font(.system(.footnote, design: .monospaced))
          }
        }
      }
      .navigationTitle("Perch on iOS")
      .navigationDestination(for: NSObject.self) { Destination(route: $0) }
    }
  }
}

/// The one `switch` the app writes, and the counterpart of `describeRoute` on the Android side.
///
/// Note the flattening: Kotlin's `PaymentRoutes.Details` is `PaymentRoutesDetails` here. The
/// Objective-C bridge has no nested types, so the nesting a feature uses to group its links
/// becomes part of the name.
private func describe(_ route: Any?) -> String {
  switch route {
  case is HomeDeepLink: return "HomeDeepLink"
  case let details as PaymentRoutesDetails: return "PaymentRoutes.Details(id = \(details.id))"
  case is PaymentRoutesApprovals: return "PaymentRoutes.Approvals"
  case .none: return "null — no route matched this URL"
  default: return String(describing: type(of: route!))
  }
}

private struct Destination: View {

  let route: NSObject

  var body: some View {
    VStack(spacing: 12) {
      Text(screen).font(.title2.bold())
      Text(detail).font(.system(.footnote, design: .monospaced)).foregroundStyle(.secondary)
    }
    .padding()
  }

  private var screen: String {
    switch route {
    case is HomeDeepLink: return "Home"
    case is PaymentRoutesDetails: return "Payment"
    case is PaymentRoutesApprovals: return "Approvals"
    default: return "Unknown"
    }
  }

  private var detail: String {
    if let details = route as? PaymentRoutesDetails { return "id = \(details.id)" }
    return "The object on the NavigationPath is the one parse() returned."
  }
}
