# Changelog

## [Snapshot]

[Snapshot]: https://github.com/rschattauer/EdgeChameleon/compare/0.3.0...main

**New**

- Upcoming additions go here

**Changed**

- Upcoming changes go here

**Documentation**

- `docs/ADVANCED.md` describes the experimental API: `EdgeChameleonTuning` and the `tuning` overload of the modifier,
  `SamplingSource`, the sampled cells and `sampleCount` of `EdgeChameleonState` (there since 0.1.0) and the
  `EdgeChameleon.*` trace sections
- Since 0.2.0 the library depends on `androidx.core` 1.19.1, so an app needs compileSdk 37 and Android Gradle Plugin
  9.1 or newer. The README lists this under Requirements
- The README and `SECURITY.md` say what `SamplingSource.Window` sees, which is more than the content of the modifier
- The README and `docs/ADVANCED.md` say how a `TextureView` and a `SurfaceView` behind a bar are handled: a `TextureView`
  is sampled with both sources, a `SurfaceView` is not seen by either, and a hint sets the icons there. A device test
  covers all of it

## [0.3.0]

[0.3.0]: https://github.com/rschattauer/EdgeChameleon/compare/0.2.0...0.3.0

**New**

- `Modifier.edgeChameleonHint(icons)` tells the library which icons the content of an element needs. A bar that is
  completely covered by hints is not sampled at all, and the content is not recorded for it. Partly covered bars are
  sampled as before
- Experimental: `EdgeChameleonTuning.samplingSource` (`SamplingSource.Content` or `Window`) reads the bars from the frame
  that the window has drawn with `PixelCopy`, instead of drawing the content a second time. `Auto`, the default, is
  `Content` for now
- A macrobenchmark module (`benchmark`) and a `BenchmarkActivity` in the sample app, to measure what the library costs
  the frames of an app

**Changed**

- While the bars keep showing the same colours, the pause between two readbacks grows from 100 ms up to 500 ms, so a
  spinner that redraws elsewhere on the screen no longer causes 10 readbacks per second. The first change after that is
  seen after at most 500 ms. Experimental: `EdgeChameleonTuning.maxIdleIntervalMillis` sets the limit, and a value that
  is not larger than the normal pause turns this off
- The library writes trace sections (`EdgeChameleon.*`) that show up in a system trace

## [0.2.0]

[0.2.0]: https://github.com/rschattauer/EdgeChameleon/compare/0.1.0...0.2.0

**Changed**

- The navigation bar counts every cell the same, so the whole width of the bar decides. Before, three zones around the
  buttons counted three times, at positions that do not match every device. `EdgeChameleonTuning.iconZoneWeight` now
  only applies to the status bar

## [0.1.0]

[0.1.0]: https://github.com/rschattauer/EdgeChameleon/releases/tag/0.1.0

First release. Compose Multiplatform library that switches the status bar and navigation bar icons between light and
dark, based on the content behind the bars.

**New**

- `Modifier.edgeChameleon()` samples the content behind the bars and sets the icons to the colour with the better
  contrast. Options: `enabled`, `forcedIcons`, `statusBar`, `navigationBar` and `state`
- `EdgeChameleonState` holds the current `statusBarIcons` and `navigationBarIcons` as Compose state; `BarIcons` has the
  values `Light` and `Dark`
- Experimental (`@ExperimentalEdgeChameleonApi`): `EdgeChameleonTuning` with the sampling rate, cell count, contrast
  threshold, hysteresis and zone weights, and `EdgeChameleonState.collectCells` with the sampled cells
- Android 9 (API 28) and newer: the content is sampled after it changes, at most 10 times per second and not at all on
  a static screen; each bar is reduced to 16 cells that vote for the icon colour with the better contrast
- Android: sampling pauses while another window has the focus; a full-screen `Dialog` has its own bars, so the modifier
  goes on its content as well; with gesture navigation the navigation bar is not handled; the navigation bar contrast
  scrim is turned off while the modifier is active; disabling or removing the modifier restores the original icons
- Android below 9, iOS, macOS, desktop (JVM), web (JS and Wasm): the modifier only draws the content, the options have
  no effect
- Targets: Android (minSdk 23), iOS (arm64 and simulator arm64), macOS arm64, JVM, JS and Wasm
- The public API is checked against `edgechameleon/api` in CI
