# Security Policy

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub: open the **Security** tab of this repository and choose
**Report a vulnerability**. Do not open public issues or pull requests for security reports.

Include what you found, how to reproduce it, which EdgeChameleon version, Android version and device are affected, and
what an attacker gains. We acknowledge reports within a few working days and keep you informed until a fix is released.

## Supported versions

The latest released version receives security fixes.

## What is in scope

EdgeChameleon is a library. It runs inside the process of the app that uses it, reads back the Compose content it is
applied to and changes the system bar icons of the window of the app. Reports about these are in scope:

- pixels of the sampled content that outlive a read, are stored, or leave the process in any way;
- reading content outside the modifier, or of another window, or of another app;
- a crash or a hang of the app, such as a blocked render thread, that content or a sequence of window changes can
  trigger;
- changes to the window or the system bars that the options do not name;
- weaknesses in the release pipeline and the published artifacts: workflows, signing, the Maven Central publication.

## Good to know

- The library adds no permissions and makes no network requests. It stores nothing: the sampled pixels stay in buffers
  in memory that are reused for the next read, and only the chosen icon colour is kept.
- EdgeChameleon is only implemented on Android. Sampling is limited to the Compose content inside the modifier, on
  Android 9 (API 28) and newer. On the other targets the modifier only draws the content.
- While the modifier is active it turns off the navigation bar contrast scrim of the window, and restores it when the
  modifier is disabled or removed. It also changes the light appearance of the system bars, and restores that too.
- `EdgeChameleonState.collectCells` is experimental and off by default. When on, it hands the averaged colours of the
  sampled cells to the app. The sample app's debug panel uses it and is meant for development only.
- Dependencies are kept up to date through Dependabot, and the GitHub Actions of this repository are pinned to commits.
