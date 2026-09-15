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
        Section("URL") {
          Text(shell.url).font(.system(.footnote, design: .monospaced))
        }

        Section("Resolves to") {
          Text(describe(shell.route).name)
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

/// The counterpart of `describeRoute` on the Android side.
///
/// Objective-C has no nested types, so Kotlin's `PaymentRoutes.Details` is `PaymentRoutesDetails`
/// here — a feature's grouping becomes part of the name.
private func describe(_ route: Any?) -> (name: String, screen: String, detail: String) {
  let onTheStack = "The object on the NavigationPath is the one parse() returned."
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
