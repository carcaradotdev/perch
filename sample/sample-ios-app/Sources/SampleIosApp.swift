import SwiftUI
import SampleShared

@main
struct SampleIosApp: App {

  @StateObject private var shell = Shell()

  var body: some Scene {
    WindowGroup {
      ContentView(shell: shell)
        // The other half of the deep link: a URL the OS hands the app because `sample` is in
        // CFBundleURLSchemes. `simctl openurl sample://home` lands here.
        .onOpenURL { shell.open($0.absoluteString) }
    }
  }
}

/// Holds the parser and the current route.
///
/// The parser is built once. `sampleParser()` is a Kotlin top-level function, so from Swift it is a
/// static on the file's class - `SampleAppKt` - which is the one naming convention the bridge
/// imposes that has no counterpart on the Kotlin side.
@MainActor
final class Shell: ObservableObject {

  private let parser = SampleAppKt.sampleParser()

  @Published private(set) var url: String
  @Published private(set) var route: Any?
  @Published var path = NavigationPath()

  /// Starts resolved rather than blank, so the picker shows what a link does before anything is
  /// tapped. `url` and `route` only ever change together, in `open`.
  init() {
    url = SampleLinks.shared.DEFAULT
    route = parser.parse(url: SampleLinks.shared.DEFAULT)
  }

  /// Opening a link is the whole demo: parse it, and go where it points.
  ///
  /// One method for both halves deliberately. A link picked from the list and a link the OS
  /// delivers through `onOpenURL` are the same event as far as the app is concerned - which is the
  /// property a deep link has to have, and the one worth showing.
  ///
  /// A URL that resolves to nothing leaves the stack alone rather than pushing an empty screen.
  /// The picker says so, which is the other half of what this demonstrates: `parse` returning
  /// `null` is an answer, not a failure.
  func open(_ link: String) {
    url = link
    // What Perch does, in one line, and it is the same line the Android shell runs. Stored rather
    // than recomputed from `url`: a computed property would re-cross the Objective-C bridge and
    // re-run the parser on every SwiftUI body evaluation, including ones caused by `path`.
    route = parser.parse(url: link)
    path = NavigationPath()
    if let destination = route as? NSObject {
      path.append(destination)
    }
  }
}
