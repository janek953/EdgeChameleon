# Changelog

## [Snapshot]

[Snapshot]: https://github.com/rschattauer/EdgeChameleon/compare/0.1.0...main

**New**

- Upcoming additions go here

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
