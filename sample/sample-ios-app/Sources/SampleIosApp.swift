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

/// Everything the Kotlin graph needs from this side, and the router it hands back.
///
/// `NSObject` rather than a plain class: a Kotlin interface crosses as an Objective-C protocol, and
/// only an Objective-C class can conform to one. That is the price of the seam, and what it buys is
/// that the router calls back into SwiftUI without either side importing the other's world.
///
/// `@preconcurrency` on both conformances is the honest label for what the bridge leaves out. The
/// generated protocols carry no isolation, so Swift cannot see that the only caller of `goTo` is
/// the router, reached from `open` on the main thread.
@MainActor
final class Shell: NSObject, ObservableObject, @preconcurrency DeepLinkNavigator, @preconcurrency SessionState {

  /// `@Published` cannot back this one. It satisfies an Objective-C protocol requirement, and a
  /// property wrapper has no Objective-C representation, so the observer does that job by hand.
  var signedIn = false {
    willSet { objectWillChange.send() }
  }

  /// The last link that went through the router, or nil before one has. Launching the app is not
  /// opening a link, so there is nothing to show until a tap or the OS provides one.
  @Published private(set) var url: String?
  @Published private(set) var routing: (any Routing)?
  @Published var path = NavigationPath()

  /// `SampleGraph` is a Kotlin interface, so from Swift its Metro-generated companion is a class of
  /// its own rather than a member of it.
  private lazy var router = SampleGraphCompanion.shared
    .create(navigator: self, session: self)
    .router

  /// One method for a link tapped in the picker and a link the OS delivers, because a deep link is
  /// only worth the name if those are the same event.
  func open(_ link: String) {
    url = link
    path = NavigationPath()
    routing = router.open(url: link)
  }

  /// Called from Kotlin, by the router, once it has decided where this link lands.
  func goTo(route: any SampleRoute) {
    // Every exported Kotlin class is an NSObject subclass, which is where NavigationPath's
    // Hashable requirement is met.
    if let destination = route as? NSObject {
      path.append(destination)
    }
  }
}
