# Contributing to EdgeChameleon

Thank you for your interest in EdgeChameleon. Issues, pull requests and ideas are welcome.

- Search the existing issues before you file a bug.
- For a large change, open an issue first and describe what you plan.
- Be kind: see the [Code of Conduct](CODE_OF_CONDUCT.md).
- Report security problems privately, see [SECURITY.md](SECURITY.md).

## Development setup

| For | You need |
|---|---|
| Library, unit tests, web and desktop targets | JDK 21 (the Gradle wrapper is included) |
| Android, sample app, device tests | Android SDK with platform 37 (`ANDROID_HOME`), an emulator or a device for the device tests |
| iOS and macOS targets | macOS with Xcode |

```bash
./gradlew :edgechameleon:testAndroidHostTest :edgechameleon:jvmTest      # unit tests
./gradlew :edgechameleon:connectedAndroidDeviceTest                      # device tests, on a running emulator or device
./gradlew :edgechameleon:compileKotlinJs :edgechameleon:compileKotlinWasmJs
./gradlew :edgechameleon:compileKotlinIosArm64 :edgechameleon:compileKotlinIosSimulatorArm64 :edgechameleon:compileKotlinMacosArm64   # macOS
./gradlew :sample:androidApp:installDebug                                # the sample app
./gradlew :benchmark:connectedBenchmarkAndroidTest                       # what the library costs the frames, on a physical device
./gradlew ktfmtCheck ktfmtCheckScripts                                   # formatting
./gradlew :edgechameleon:checkKotlinAbi                                  # the public API against edgechameleon/api
./scripts/check-hygiene.sh                                               # no secrets or personal paths in tracked files
./scripts/check-links.sh                                                 # the links, anchors and images of the markdown files
actionlint                                                               # the workflows
```

The benchmark (`benchmark/`) runs the sample app in every sampling mode on four kinds of screens, and compares the frames
with the app without the library (`Off`). Run it on a physical device: an emulator gives no usable numbers and is
refused, unless the `androidx.benchmark.suppressErrors=EMULATOR` instrumentation argument is set for a smoke run.

Format the code with `./gradlew ktfmtFormat ktfmtFormatScripts`; CI fails on unformatted files. The style is
[ktfmt](https://github.com/facebook/ktfmt) (kotlinlang style). The public API is explicit (`explicitApi()`): after a
deliberate change of it, run `./gradlew :edgechameleon:updateKotlinAbi` and commit the changed dump in
`edgechameleon/api`. Functions have a block body, not an expression body.

## Repository layout

| Path | What |
|---|---|
| `edgechameleon/` | The library: `commonMain` (modifier, hints, node, bar decision, throttle), `androidMain` (pixel readback of the content and of the window, window bars), `nonAndroidMain` (no-op for the targets that don't need it), unit tests in `commonTest`, device tests in `androidDeviceTest` |
| `edgechameleon/api/` | Dump of the public API, checked in CI |
| `sample/` | Sample Android app with a debug panel and a `BenchmarkActivity` (`androidApp`), and its shared Compose code (`shared`) |
| `benchmark/` | Macrobenchmark of what the library costs the frames of the sample app |
| `docs/` | `ADVANCED.md` (the experimental API) and the screenshots of the README in `images/` |
| `scripts/` | `check-hygiene.sh` (secret scan), `check-links.sh` (links of the markdown files) and `release-notes.sh` (notes of a release from `CHANGELOG.md`) |
| `.github/` | Workflows, Dependabot, and the templates for issues and pull requests |

The Maven artifact is `dev.schattauer.edgechameleon:edgechameleon`.

### Platforms

EdgeChameleon is a Compose Multiplatform library that is only implemented on Android (`androidMain`), because the other
targets don't need it. `nonAndroidMain` is a deliberate no-op that lets shared code call the modifier everywhere. Please
don't add an implementation for iOS, macOS, desktop or web; if you think a target needs one, open an issue and explain
what that platform lacks.

## Branches and continuous integration

Work happens on `main`, and pull requests target `main`. A release is a tag on `main`, see [RELEASE.md](RELEASE.md).
The CI (`.github/workflows/ci.yml`) runs on every pull request and on `main`: hygiene (secret scan, markdown links,
actionlint), formatting, unit tests, compilation of the web targets and the Android sample, compilation of the Apple
targets and the check of the public API. Device tests are not part of the CI: run them on an emulator when you change
the sampling or the window handling. The benchmark is not either, as it needs a physical device: run it when you change
what the library costs.

## Pull requests

1. Fork the repository and create a topic branch from `main`.
2. Add or update tests for your change.
3. Make sure the commands above pass.
4. Update `README.md`, [docs/ADVANCED.md](docs/ADVANCED.md) for the experimental API, and the `Snapshot` section of
   [CHANGELOG.md](CHANGELOG.md) when users can notice the change. A
   breaking change of the public API needs a new minor version while the version is `0.x`, and a new major version after
   1.0.
5. Describe what changed and how you tested it.

## Commit messages

To keep the history readable at a glance, a commit message is one line that says precisely what changed, 100 characters
at most.

## Screenshots and examples

Use neutral content. Do not commit real customer names, account data, API keys or screenshots of real apps.
