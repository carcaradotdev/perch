plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.metro)
  id("dev.carcara.perch.detekt")
  id("dev.carcara.perch.publishing")
}

// The Metro compiler plugin emits, alongside the four types this module actually declares
// (`DeepLinkBindings`, `DeepLinkRouteHandlerMapAccessor`, `DeepLinkRouteKey`), a set of public
// classes that exist only so a consumer's separately-compiled Metro graph can link against this
// module's `@Provides`/`@ContributesTo` declarations: one `*MetroFactory` per `@Provides`
// function (plus its `Companion`), a `BindsMirror` per `@Multibinds` property, a
// `MetroContributionToAppScope` marker, and two top-level "hint" classes in a synthetic
// `metro.hints` package. None of these are meant to be referenced by name - Dagger and Anvil have
// the same shape of generated surface for the same reason - and leaving them in the dump means a
// routine Metro version bump (which can rename or restructure its own generated classes) fails
// `apiCheck` for reasons that have nothing to do with Perch's own API, training whoever hits that
// to `apiDump` past it without reading the diff. `ignoredPackages` only reaches `metro.hints`,
// which is Metro's own synthetic namespace and can never hold a real Perch declaration; every
// other exclusion below names an exact class, so a real type later added to
// `dev.carcara.perch.metro` still appears in the dump untouched. Adding, removing or renaming a
// `@Provides` function changes this list - that's intentional: it forces a human to look at the
// new generated shape once, rather than have a name pattern silently swallow it.
//
// `ignoredClasses` needs the binary name with `$` as the nested-class separator (`Outer$Inner`),
// not `.` (`Outer.Inner`). Verified by trying the dotted form first: it silently dropped `Outer`
// (here, `DeepLinkBindings` and `DeepLinkRouteHandlerMapAccessor` themselves) from the dump too,
// alongside the intended nested class - exactly the failure mode this filter exists to avoid. The
// `$`-separated form below only ever removes the exact class named.
extensions.configure<kotlinx.validation.ApiValidationExtension> {
  ignoredPackages += "metro.hints"
  ignoredClasses += setOf(
    "dev.carcara.perch.metro.DeepLinkBindings\$ProvideDeepLinkManagerMetroFactory",
    "dev.carcara.perch.metro.DeepLinkBindings\$ProvideDeepLinkManagerMetroFactory\$Companion",
    "dev.carcara.perch.metro.DeepLinkBindings\$ProvideDeepLinkBootstrapStateMetroFactory",
    "dev.carcara.perch.metro.DeepLinkBindings\$ProvideDeepLinkBootstrapStateMetroFactory\$Companion",
    "dev.carcara.perch.metro.DeepLinkBindings\$ProvideDeepLinkHandlerDispatcherMetroFactory",
    "dev.carcara.perch.metro.DeepLinkRouteHandlerMapAccessor\$BindsMirror",
    "dev.carcara.perch.metro.DeepLinkRouteHandlerMapAccessor\$MetroContributionToAppScope",
  )
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "dev.carcara.perch.metro"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }

  jvm()
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  macosX64()
  macosArm64()

  sourceSets {
    commonMain.dependencies {
      api(projects.perchCore)
      implementation(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(projects.perchTest)
    }
  }
}
