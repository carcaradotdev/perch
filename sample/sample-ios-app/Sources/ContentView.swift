import SwiftUI
import SampleShared

/// The parsed route goes onto `NavigationPath` with no adapter: a Kotlin class exported to
/// Objective-C is an `NSObject` subclass, its `equals`/`hashCode` become `isEqual`/`hash`, and that
/// is what Swift builds `Hashable` from.
struct ContentView: View {

  @ObservedObject var shell: Shell

  var body: some View {
    NavigationStack(path: $shell.path) {
      List {
        Section("Last opened") {
          Text(shell.url ?? "Nothing yet")
            .font(.system(.footnote, design: .monospaced))
            .foregroundStyle(shell.url == nil ? .secondary : .primary)
        }

        Section("Session") {
          Toggle("Signed in", isOn: $shell.signedIn)
          Text("The payments module gates its approvals link on this. Nothing here knows that.")
            .font(.footnote)
            .foregroundStyle(.secondary)
        }

        Section("Resolves to") {
          Outcome(routing: shell.routing)
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

private struct Outcome: View {

  let routing: (any Routing)?

  var body: some View {
    switch routing {
    case let navigated as RoutingNavigated:
      Text(describe(navigated.matched).name).font(.system(.footnote, design: .monospaced))
      // Routes are NSObject subclasses, so `isEqual` is the Kotlin `equals` the data class wrote.
      if (navigated.matched as? NSObject)?.isEqual(navigated.destination) == false {
        Text("Redirected to \(describe(navigated.destination).name) by the payments handler.")
          .font(.footnote)
          .foregroundStyle(.secondary)
      }
    case is RoutingUnmatched:
      Text("null — no route matched this URL")
        .font(.system(.footnote, design: .monospaced))
        .foregroundStyle(.secondary)
    default:
      Text("Tap a link below to send it through the router.")
        .font(.footnote)
        .foregroundStyle(.secondary)
    }
  }
}

/// The counterpart of `describeRoute` on the Android side.
///
/// Objective-C has no nested types, so Kotlin's `PaymentRoutes.Details` is `PaymentRoutesDetails`
/// here — a feature's grouping becomes part of the name.
private func describe(_ route: Any?) -> (name: String, screen: String, detail: String) {
  let onTheStack = "The object on the NavigationPath is the one the router chose."
  switch route {
  case is HomeDeepLink:
    return ("HomeDeepLink", "Home", onTheStack)
  case let details as PaymentRoutesDetails:
    return ("PaymentRoutes.Details(id = \(details.id))", "Payment", "id = \(details.id)")
  case is PaymentRoutesApprovals:
    return ("PaymentRoutes.Approvals", "Approvals", onTheStack)
  case .none:
    return ("null — no route matched this URL", "Nothing", onTheStack)
  default:
    let name = String(describing: type(of: route!))
    return (name, name, onTheStack)
  }
}

private struct Destination: View {

  let route: NSObject

  var body: some View {
    let resolved = describe(route)
    VStack(spacing: 12) {
      Text(resolved.screen).font(.title2.bold())
      Text(resolved.detail).font(.system(.footnote, design: .monospaced)).foregroundStyle(.secondary)
    }
    .padding()
  }
}
