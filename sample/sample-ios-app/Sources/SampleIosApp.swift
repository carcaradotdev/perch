import SwiftUI
import SampleShared

@main
struct SampleIosApp: App {

  @StateObject private var shell = Shell()

  var body: some Scene {
    WindowGroup {
      ContentView(shell: shell)
        .onOpenURL { shell.open($0.absoluteString) }
    }
  }
}

/// `sampleParser()` is a Kotlin top-level function, so from Swift it is a static on the class named
/// after its file — `SampleAppKt`.
@MainActor
final class Shell: ObservableObject {

  private let parser = SampleAppKt.sampleParser()

  @Published private(set) var url: String
  @Published private(set) var route: Any?
  @Published var path = NavigationPath()

  init() {
    url = SampleLinks.shared.DEFAULT
    route = parser.parse(url: SampleLinks.shared.DEFAULT)
  }

  /// One method for a link tapped in the picker and a link the OS delivers, because a deep link is
  /// only worth the name if those are the same event.
  func open(_ link: String) {
    url = link
    // Stored, not computed from `url`: a computed property re-crosses the Objective-C bridge and
    // re-parses on every SwiftUI body evaluation, including ones caused by `path`.
    route = parser.parse(url: link)
    path = NavigationPath()
    if let destination = route as? NSObject {
      path.append(destination)
    }
  }
}
