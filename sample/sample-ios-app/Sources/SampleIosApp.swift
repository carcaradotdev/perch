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
        .onOpenURL { shell.arrive($0.absoluteString) }
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

  @Published var url: String = SampleLinks.shared.DEFAULT
  @Published var path = NavigationPath()

  /// What Perch does, in one line, and it is the same line the Android shell runs.
  var route: Any? { parser.parse(url: url) }

  func arrive(_ incoming: String) {
    url = incoming
    path = NavigationPath()
  }
}
