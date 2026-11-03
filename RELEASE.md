# Releasing

A release is a tag `X.Y.Z` on `main`. The version comes from the tag: no file has to be edited to change it, and the
build uses `0.1.0-SNAPSHOT` when no `-PreleaseVersion` is given.

`.github/workflows/release.yml` runs the checks, builds every publication, waits for the approval of the `release`
environment, creates the GitHub draft release with the notes from `CHANGELOG.md`, releases to Maven Central and then
publishes the GitHub release.

Versions follow [Semantic Versioning](https://semver.org). While the version is `0.x`, a breaking change of the public
API raises the minor version. API marked `@ExperimentalEdgeChameleonApi` may change in any release.

## One-time setup

**Create the `release` environment before the first tag** (Settings → Environments → New environment). A workflow that
names an environment that does not exist creates it without protection, and the release would run without approval.
Environments with reviewers are available for public repositories of personal accounts.

- Required reviewers: the maintainer. Leave "Prevent self-review" off, otherwise nobody can approve a release the
  maintainer started.
- Deployment branches and tags: select "Selected branches and tags", add the tag rule `[0-9]*.[0-9]*.[0-9]*`.
- Environment secrets:

| Secret | What |
|---|---|
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | User token of the Central Portal account that owns the namespace `dev.schattauer.edgechameleon` |
| `SIGNING_KEY`, `SIGNING_KEY_PASSWORD` | Armored private signing key (`gpg --armor --export-secret-keys <KEYID>`) and its passphrase; the public key must be on a keyserver |

Also set, once:

- Settings → General → Releases: enable immutable releases.
- Settings → Security → Private vulnerability reporting: enable it, see [SECURITY.md](SECURITY.md).
- Optional: a tag ruleset for `*.*.*` that only lets the maintainer create tags.

The ruleset of `main` lets only the maintainer push to it, so the steps below are done by them.

## Release

1. Make sure `main` is green (CI: hygiene, formatting, unit tests, web and Apple compilation, public API check).

2. Update `CHANGELOG.md`:
    1. Change `Snapshot` to the version that is released, e.g. `[0.2.0]`, and remove placeholder lines such as
       `Upcoming additions go here`. The workflow refuses a section that still has one.
    2. Point its link at the release: `[0.2.0]: https://github.com/rschattauer/EdgeChameleon/compare/0.1.0...0.2.0`. The
       first release links to `https://github.com/rschattauer/EdgeChameleon/releases/tag/0.1.0`.
    3. Add a new `Snapshot` section on the top, with the link
       `[Snapshot]: https://github.com/rschattauer/EdgeChameleon/compare/0.2.0...main` and the placeholders
       `**New**` / `- Upcoming additions go here` and `**Changed**` / `- Upcoming changes go here`.
       The workflow takes the release notes from the section of the version.

3. For the first release only: remove the "Not published yet" note from the quick start in `README.md`.

4. Commit and push to `main`: `git commit -am "Release X.Y.Z" && git push`. Wait for CI.

5. Tag exactly that commit and push only the tag:
   `git tag -am "Release X.Y.Z" X.Y.Z && git push origin X.Y.Z`

6. The workflow `Release` runs the checks again, checks that the tag is on `main` and has release notes (shown in the
   summary of the run), builds all publications and then waits for the approval of the `release` environment. After the
   approval it:
    1. creates the GitHub release as a draft with the release notes;
    2. uploads to Maven Central and releases it (a published version cannot be changed or removed);
    3. publishes the GitHub release.

7. Check the result:
    - the GitHub release has the notes and is marked as latest;
    - central.sonatype.com → Deployments shows the deployment as published (the artifacts appear on search.maven.org
      after about half an hour);
    - the dependency resolves: `dev.schattauer.edgechameleon:edgechameleon:X.Y.Z` in a fresh project with
      `mavenCentral()`.

## When something fails

- Before the Maven Central step: fix the problem and re-run the failed jobs; the draft release is reused. If the fix
  needs a change of the code, delete the tag and the draft release, fix it on `main` and tag again.
- After the Maven Central step: that version exists for good. Fix the problem and release the next patch version.
- A dry run builds and tests everything without publishing: run the `Release` workflow manually (Actions → Release →
  Run workflow) with a version like `0.0.0-dryrun`. It uses the `Snapshot` section as the release notes.
