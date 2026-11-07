# Contribute

## The three Gradle builds

The repository holds three separate Gradle builds:

| Build | Contents | Command |
| --- | --- | --- |
| Root | `perch-core`, `perch-ksp` and the sample. | `./gradlew build` |
| `perch-gradle-plugin` | The two Gradle plugins. | `./gradlew -p perch-gradle-plugin build --no-configuration-cache` |
| `build-logic` | The convention plugins that the other two builds apply. | `./gradlew -p build-logic build` |

The root build cannot reach the tasks of the other two. If you change a plugin, run the build of
that plugin.

`./gradlew build` in the root runs `check`. `check` runs `apiCheck` for `perch-core` and detekt on
each main source set. It also builds and tests the sample, which runs the KSP processor and both
Gradle plugins from end to end.

- If you change the public API of `perch-core`, run `./gradlew apiDump` and commit the result.
- Detekt does not examine test sources.
- The Apple targets compile only on macOS.

## Code that the builds share

The three builds cannot depend on one another. Thus the path-pattern rules and the manifest format
are shared as source:

- `shared/routing` holds the collision rules. `perch-core`, `perch-ksp` and `perch-gradle-plugin`
  compile it.
- `shared/manifest` holds the manifest format. `perch-ksp` and `perch-gradle-plugin` compile it.

The declarations in these directories are `internal`, and they import nothing.

## License header

Each source file starts with the Apache license header. A script adds it:

```bash
python3 scripts/license_header.py check   # lists the files without a current header
python3 scripts/license_header.py apply   # adds or updates the header in place
```

The script reads the header text from the appendix of `LICENSE`. It examines Kotlin, Gradle Kotlin
scripts, Swift, Python, YAML, TOML and properties files.

## Continuous integration

The `CI` workflow runs on each pull request:

| Job | Runner | Steps |
| --- | --- | --- |
| `plugins` | Linux | License header check, `build-logic` build, `perch-gradle-plugin` build and `publishToMavenLocal`. |
| `library` | macOS | Root build and `publishToMavenLocal`. |

`publishToMavenLocal` makes sure that the POM, the sources jar and the javadoc jar are correct
before a release.

The `Docs` workflow builds this site on each pull request. On `main`, it also publishes the site to
GitHub Pages.

## Documentation

The pages are Markdown files in `docs/`. [Zensical](https://zensical.org) builds them into a site.
The configuration and the navigation are in `zensical.toml`.

Zensical needs Python 3.11 or later. To preview the site:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install zensical
zensical serve
```

Then open `http://localhost:8000`.

To build the site as CI does:

```bash
zensical build --clean --strict
```

`--strict` stops the build on a warning, for example a link to a page that does not exist. The
site goes to `site/`, which Git ignores.

When you add a page, add it to `nav` in `zensical.toml`.

## Release

A published GitHub Release starts the release. The tag is the version, for example `v0.2.0` or
`0.2.0`. No commit in the tree changes the version.

1. Publish a GitHub Release. The `Release` workflow then:
    1. reads the version from the tag,
    2. runs the `CI` workflow on the tagged commit,
    3. uploads `perch-gradle-plugin` to the Maven Central Portal,
    4. uploads `perch-core` and `perch-ksp` to the Maven Central Portal.
2. The Portal holds two staged deployments, one for each Gradle build. Nothing is public yet.
3. Release both deployments together. Run the `Publish staged deployments` workflow with the two
   deployment IDs, or release them on
   [central.sonatype.com](https://central.sonatype.com/publishing/deployments). The workflow
   releases nothing unless both deployments have the state `VALIDATED`.

The release needs four repository secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | The user name of a Central Portal user token. |
| `MAVEN_CENTRAL_PASSWORD` | The password of that user token. |
| `SIGNING_IN_MEMORY_KEY` | The armored private GPG key. |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | The password of that key. |

!!! warning

    Release both deployments or drop both. A deployment on Maven Central cannot be changed or
    removed. If only one deployment is released, the plugin can be public without the library that
    it needs.
