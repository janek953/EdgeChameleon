# Security Policy

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub: open the **Security** tab of this repository and choose
**Report a vulnerability**. Do not open public issues or pull requests for security reports.

Include what you found, how to reproduce it, which EdgeChameleon version, Android version and device are affected, and
what an attacker gains. We acknowledge reports within a few working days and keep you informed until a fix is released.

## Supported versions

The latest released version receives security fixes.

## What is in scope

EdgeChameleon is a library. It runs inside the process of the app that uses it, reads back the colours behind the system
bars and changes the system bar icons of the window of the app. By default it reads the Compose content it is applied
to; the experimental `SamplingSource.Window` copies the bar areas from the frame of the app's own window. Reports about
these are in scope:

- pixels of the sampled content that outlive a read, are stored, or leave the process in any way;
- reading more than the sampling source is meant to see (the bar areas under the modifier, from the Compose content of
  the modifier, or with `SamplingSource.Window` from the app's own window), or anything of another window or app;
- a crash or a hang of the app, such as a blocked render thread, that content or a sequence of window changes can
  trigger;
- changes to the window or the system bars that the options do not name;
- weaknesses in the release pipeline and the published artifacts: workflows, signing, the Maven Central publication.

## Good to know

- The library adds no permissions and makes no network requests. It stores nothing: the sampled pixels stay in buffers
  in memory that are reused for the next read, and only the chosen icon colour is kept.
- EdgeChameleon is only implemented on Android. Sampling needs Android 9 (API 28) or newer and looks at the bar areas
  under the modifier only: by default at the Compose content inside the modifier, with `SamplingSource.Window` at the
  frame of the app's own window, which includes Views. On the other targets the modifier only draws the content.
- On Android 10 and newer, the modifier turns off the navigation bar contrast scrim of the window while it is active,
  and restores it when it is disabled or removed. It also changes the light appearance of the system bars, and restores
  that too.
- `EdgeChameleonState.collectCells` is experimental and off by default. When on, it hands the averaged colours of the
  sampled cells to the app. The sample app's debug panel uses it and is meant for development only.
- Dependencies are kept up to date through Dependabot, and the GitHub Actions of this repository are pinned to commits.
